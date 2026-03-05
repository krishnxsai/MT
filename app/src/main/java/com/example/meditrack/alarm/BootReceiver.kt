package com.example.meditrack.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.meditrack.data.model.Medicine
import com.example.meditrack.data.model.RepeatType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Receiver that listens for device boot completion and reschedules all medicine alarms.
 * Also handles the SCHEDULE_EXACT_ALARM permission grant on Android 12+.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                Log.d(TAG, "Boot completed or alarm permission changed, rescheduling alarms")
                rescheduleAllAlarms(context)
            }
        }
    }

    private fun rescheduleAllAlarms(context: Context) {
        val pendingResult = goAsync()

        scope.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val userId = auth.currentUser?.uid

                if (userId == null) {
                    Log.d(TAG, "No user logged in, skipping alarm reschedule")
                    pendingResult.finish()
                    return@launch
                }

                val firestore = FirebaseFirestore.getInstance()
                val alarmScheduler = AlarmScheduler(context)

                // Get all active medicines for the current user
                val snapshot = firestore.collection("medicines")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("isActive", true)
                    .get()
                    .await()

                var scheduledCount = 0
                val medicineUpdates = mutableListOf<Pair<String, List<Int>>>()

                for (doc in snapshot.documents) {
                    try {
                        val medicine = doc.toObject(Medicine::class.java)
                        if (medicine != null && medicine.isActive && medicine.repeatType != RepeatType.AS_NEEDED) {
                            // Schedule alarms for this medicine
                            val newAlarmIds = alarmScheduler.scheduleMedicineAlarms(medicine)
                            if (newAlarmIds.isNotEmpty()) {
                                medicineUpdates.add(medicine.id to newAlarmIds)
                                scheduledCount++
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to reschedule alarm for medicine ${doc.id}: ${e.message}")
                    }
                }

                // Update alarm IDs in Firestore
                for ((medicineId, alarmIds) in medicineUpdates) {
                    try {
                        firestore.collection("medicines")
                            .document(medicineId)
                            .update("alarmIds", alarmIds)
                            .await()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update alarm IDs for $medicineId: ${e.message}")
                    }
                }

                Log.d(TAG, "Rescheduled alarms for $scheduledCount medicines")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule alarms: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private object AlarmManager {
    const val ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED =
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
}

