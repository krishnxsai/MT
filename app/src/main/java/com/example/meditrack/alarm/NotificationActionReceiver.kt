package com.example.meditrack.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast

/**
 * Receiver for handling notification action buttons (Take, Snooze, Skip/Dismiss).
 * Properly stops the alarm service and cancels notifications.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_TAKEN = "com.example.meditrack.ACTION_MARK_TAKEN"
        const val ACTION_SNOOZE = "com.example.meditrack.ACTION_SNOOZE"
        const val ACTION_DISMISS = "com.example.meditrack.ACTION_DISMISS"
        const val ACTION_SKIP = "com.example.meditrack.ACTION_SKIP"
        private const val SNOOZE_DURATION_MINUTES = 10
        private const val TAG = "NotificationAction"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(MedicineAlarmReceiver.EXTRA_ALARM_ID, 0)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ALWAYS stop alarm sound and vibration first for all actions
        stopAlarmService(context)

        when (intent.action) {
            ACTION_MARK_TAKEN -> {
                Log.d(TAG, "Medicine marked as taken")
                notificationManager.cancel(notificationId)

                // Show confirmation toast
                showToast(context, "Medicine marked as taken ✓")

                // TODO: Log this event to Firestore for tracking
                val medicineId = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_ID) ?: ""
                logMedicineTaken(context, medicineId)
            }

            ACTION_SNOOZE -> {
                Log.d(TAG, "Snoozing alarm for $SNOOZE_DURATION_MINUTES minutes")
                notificationManager.cancel(notificationId)

                val medicineName = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_NAME) ?: "Medicine"
                val medicineId = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_ID) ?: ""
                val dosage = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_DOSAGE) ?: ""

                scheduleSnoozeAlarm(context, notificationId, medicineName, medicineId, dosage)
                showToast(context, "Reminder snoozed for $SNOOZE_DURATION_MINUTES minutes ⏰")
            }

            ACTION_DISMISS, ACTION_SKIP -> {
                Log.d(TAG, "Alarm dismissed/skipped")
                notificationManager.cancel(notificationId)
                showToast(context, "Reminder skipped")
            }
        }
    }

    /**
     * Stop the alarm service to silence sound and vibration.
     */
    private fun stopAlarmService(context: Context) {
        try {
            AlarmService.stopAlarm(context)
            Log.d(TAG, "Alarm service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop alarm service: ${e.message}")
        }

        // Also call legacy stopAlarmSound for backward compatibility
        try {
            MedicineAlarmReceiver.stopAlarmSound()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call legacy stopAlarmSound: ${e.message}")
        }
    }

    /**
     * Show a toast message (handles main thread requirement).
     */
    private fun showToast(context: Context, message: String) {
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show toast: ${e.message}")
        }
    }

    /**
     * Log that medicine was taken to Firestore for adherence tracking.
     */
    private fun logMedicineTaken(context: Context, medicineId: String) {
        if (medicineId.isEmpty()) return
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val userId = auth.currentUser?.uid ?: return
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            val intake = hashMapOf(
                "userId" to userId,
                "medicineId" to medicineId,
                "takenAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "source" to "notification_action"
            )

            firestore.collection("medicineIntakes")
                .add(intake)
                .addOnSuccessListener {
                    Log.d(TAG, "Medicine intake logged: $medicineId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to log intake: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging medicine intake: ${e.message}")
        }
    }

    /**
     * Schedule a snooze alarm for the specified duration.
     */
    private fun scheduleSnoozeAlarm(
        context: Context,
        alarmId: Int,
        medicineName: String,
        medicineId: String,
        dosage: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Use a unique ID for the snooze alarm
        val snoozeAlarmId = alarmId + 5000 + System.currentTimeMillis().toInt() % 1000

        val snoozeIntent = Intent(context, MedicineAlarmReceiver::class.java).apply {
            putExtra(MedicineAlarmReceiver.EXTRA_ALARM_ID, snoozeAlarmId)
            putExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_ID, medicineId)
            putExtra(MedicineAlarmReceiver.EXTRA_DOSAGE, dosage)
            putExtra(MedicineAlarmReceiver.EXTRA_IS_REPEATING, false)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            snoozeAlarmId,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + (SNOOZE_DURATION_MINUTES * 60 * 1000L)

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (alarmManager.canScheduleExactAlarms()) {
                        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
                        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
                else -> {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            }
            Log.d(TAG, "Snooze alarm scheduled for ${java.util.Date(triggerTime)}")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling snooze: ${e.message}")
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule snooze alarm: ${e.message}")
        }
    }
}
