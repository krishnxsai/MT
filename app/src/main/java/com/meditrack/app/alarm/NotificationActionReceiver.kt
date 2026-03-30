package com.meditrack.app.alarm

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
        const val ACTION_MARK_TAKEN = "com.meditrack.app.ACTION_MARK_TAKEN"
        const val ACTION_SNOOZE = "com.meditrack.app.ACTION_SNOOZE"
        const val ACTION_DISMISS = "com.meditrack.app.ACTION_DISMISS"
        const val ACTION_SKIP = "com.meditrack.app.ACTION_SKIP"
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

                val medicineId = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_ID) ?: ""
                val medicineName = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_NAME) ?: ""
                val reminderTime = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_REMINDER_TIME)
                logMedicineAction(
                    context = context,
                    medicineId = medicineId,
                    medicineName = medicineName,
                    reminderTime = reminderTime,
                    taken = true,
                    source = "notification_action_taken"
                )
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
                val medicineId = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_ID) ?: ""
                val medicineName = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_NAME) ?: ""
                val reminderTime = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_REMINDER_TIME)
                logMedicineAction(
                    context = context,
                    medicineId = medicineId,
                    medicineName = medicineName,
                    reminderTime = reminderTime,
                    taken = false,
                    source = "notification_action_skipped"
                )
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
     * Log medicine reminder action (TAKE/SKIP) using MedicineIntakeService.
     * - TAKE: Decrements stock atomically + creates intake record
     * - SKIP: Creates intake record only (no stock change)
     * - Queues offline actions if not connected
     */
    private fun logMedicineAction(
        context: Context,
        medicineId: String,
        medicineName: String,
        reminderTime: String?,
        taken: Boolean,
        source: String
    ) {
        if (medicineId.isEmpty()) return

        // Launch on background thread to log action asynchronously
        Thread {
            try {
                val offlineRepo = com.meditrack.app.data.repository.OfflineActionRepository(context)
                val intakeService = com.meditrack.app.data.repository.MedicineIntakeService(
                    context,
                    offlineRepo
                )

                // Use runBlocking to execute suspend function
                kotlinx.coroutines.runBlocking {
                    val result = if (taken) {
                        intakeService.markAsTaken(
                            medicineId = medicineId,
                            medicineName = medicineName,
                            reminderTime = reminderTime,
                            source = source
                        )
                    } else {
                        intakeService.markAsSkipped(
                            medicineId = medicineId,
                            medicineName = medicineName,
                            reminderTime = reminderTime,
                            source = source
                        )
                    }

                    when (result) {
                        is com.meditrack.app.data.model.Resource.Success -> {
                            Log.d(TAG, "Medicine action logged successfully: $medicineId, taken=$taken")
                        }
                        is com.meditrack.app.data.model.Resource.Error -> {
                            Log.e(TAG, "Failed to log medicine action: ${result.message}")
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in logMedicineAction: ${e.message}", e)
            }
        }.start()
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
