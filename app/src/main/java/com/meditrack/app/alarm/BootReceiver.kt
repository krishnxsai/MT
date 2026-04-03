package com.meditrack.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.meditrack.app.data.model.Appointment
import com.meditrack.app.data.model.AppointmentStatus
import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.RepeatType
import com.meditrack.app.service.DeliveryLocationService
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

                // ── Reschedule appointment reminders ──
                rescheduleAppointmentReminders(context, firestore, userId)

                // ── Restore live delivery tracking after reboot ──
                restoreActiveDeliveryTracking(context, firestore, userId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule alarms: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun rescheduleAppointmentReminders(
        context: Context,
        firestore: FirebaseFirestore,
        userId: String
    ) {
        try {
            val scheduler = AppointmentAlarmScheduler(context)
            val now = java.util.Date()

            // Check as patient
            val patientSnap = firestore.collection("appointments")
                .whereEqualTo("patientId", userId)
                .whereGreaterThanOrEqualTo("date", now)
                .get().await()

            // Check as doctor
            val doctorSnap = firestore.collection("appointments")
                .whereEqualTo("doctorId", userId)
                .whereGreaterThanOrEqualTo("date", now)
                .get().await()

            val allDocs = (patientSnap.documents + doctorSnap.documents).distinctBy { it.id }
            var count = 0

            for (doc in allDocs) {
                try {
                    val data = doc.data ?: continue
                    val appointment = Appointment.fromMap(doc.id, data)
                    if (appointment.status == AppointmentStatus.PENDING ||
                        appointment.status == AppointmentStatus.CONFIRMED
                    ) {
                        scheduler.scheduleReminder(appointment)
                        count++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reschedule appt reminder for ${doc.id}: ${e.message}")
                }
            }
            Log.d(TAG, "Rescheduled reminders for $count upcoming appointments")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reschedule appointment reminders: ${e.message}")
        }
    }

    private suspend fun restoreActiveDeliveryTracking(
        context: Context,
        firestore: FirebaseFirestore,
        userId: String
    ) {
        try {
            val shippedOrders = firestore.collection("orders")
                .whereEqualTo("status", "SHIPPED")
                .whereEqualTo("deliveryPersonId", userId)
                .get()
                .await()

            if (shippedOrders.isEmpty) {
                Log.d(TAG, "No active SHIPPED orders to restore tracking")
                return
            }

            val userDoc = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            val deliveryName = userDoc.getString("displayName")
                ?: FirebaseAuth.getInstance().currentUser?.displayName
                ?: "Delivery"
            val deliveryPhone = userDoc.getString("phoneNumber") ?: ""

            var restoredCount = 0
            for (orderDoc in shippedOrders.documents) {
                val serviceIntent = Intent(context, DeliveryLocationService::class.java).apply {
                    action = DeliveryLocationService.ACTION_ADD_TRACKING
                    putExtra(DeliveryLocationService.EXTRA_ORDER_ID, orderDoc.id)
                    putExtra(DeliveryLocationService.EXTRA_DELIVERY_PERSON_ID, userId)
                    putExtra(DeliveryLocationService.EXTRA_DELIVERY_PERSON_NAME, deliveryName)
                    putExtra(DeliveryLocationService.EXTRA_DELIVERY_PERSON_PHONE, deliveryPhone)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                restoredCount++
            }

            Log.d(TAG, "Restored live tracking for $restoredCount active SHIPPED orders")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore active delivery tracking: ${e.message}")
        }
    }
}

private object AlarmManager {
    const val ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED =
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
}

