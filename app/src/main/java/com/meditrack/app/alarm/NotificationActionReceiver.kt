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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        const val ACTION_MEDICINE_INTAKE_RECORDED = "com.meditrack.app.ACTION_MEDICINE_INTAKE_RECORDED"
        const val EXTRA_WAS_TAKEN = "extra_was_taken"
        private const val SNOOZE_DURATION_MINUTES = 10
        private const val TAG = "NotificationAction"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAction(appContext, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process notification action: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAction(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(MedicineAlarmReceiver.EXTRA_ALARM_ID, 0)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Stop only the alarm instance tied to this action to avoid silencing other active alarms.
        stopAlarmService(context, notificationId)

        when (intent.action) {
            ACTION_MARK_TAKEN -> {
                Log.d(TAG, "Medicine marked as taken")
                notificationManager.cancel(notificationId)

                val medicineId = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_ID) ?: ""
                val medicineName = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_NAME) ?: ""
                val reminderTime = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_REMINDER_TIME)

                val logged = logMedicineAction(
                    context = context,
                    medicineId = medicineId,
                    medicineName = medicineName,
                    reminderTime = reminderTime,
                    taken = true,
                    source = "notification_action_taken"
                )
                if (logged) {
                    notifyIntakeRecorded(context, medicineId, reminderTime, true)
                }

                showToast(context, "Medicine marked as taken ✓")
            }

            ACTION_SNOOZE -> {
                Log.d(TAG, "Snoozing alarm for $SNOOZE_DURATION_MINUTES minutes")
                notificationManager.cancel(notificationId)

                val medicineName = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_NAME) ?: "Medicine"
                val medicineId = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_ID) ?: ""
                val dosage = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_DOSAGE) ?: ""
                val reminderTime = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_REMINDER_TIME)

                scheduleSnoozeAlarm(
                    context = context,
                    alarmId = notificationId,
                    medicineName = medicineName,
                    medicineId = medicineId,
                    dosage = dosage,
                    reminderTime = reminderTime
                )
                showToast(context, "Reminder snoozed for $SNOOZE_DURATION_MINUTES minutes ⏰")
            }

            ACTION_DISMISS, ACTION_SKIP -> {
                Log.d(TAG, "Alarm dismissed/skipped")
                notificationManager.cancel(notificationId)

                val medicineId = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_ID) ?: ""
                val medicineName = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_NAME) ?: ""
                val reminderTime = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_REMINDER_TIME)

                val logged = logMedicineAction(
                    context = context,
                    medicineId = medicineId,
                    medicineName = medicineName,
                    reminderTime = reminderTime,
                    taken = false,
                    source = "notification_action_skipped"
                )
                if (logged) {
                    notifyIntakeRecorded(context, medicineId, reminderTime, false)
                }

                showToast(context, "Reminder skipped")
            }
        }
    }

    /**
     * Stop the alarm service to silence sound and vibration.
     */
    private fun stopAlarmService(context: Context, alarmId: Int) {
        try {
            if (alarmId > 0) {
                AlarmService.stopAlarm(context, alarmId)
                Log.d(TAG, "Stopped alarm service for alarmId=$alarmId")
            } else {
                AlarmService.stopAlarm(context)
                Log.d(TAG, "Stopped alarm service for all alarms (missing alarmId)")
            }
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
    private suspend fun logMedicineAction(
        context: Context,
        medicineId: String,
        medicineName: String,
        reminderTime: String?,
        taken: Boolean,
        source: String
    ): Boolean {
        if (medicineId.isEmpty()) return false

        return try {
            val offlineRepo = com.meditrack.app.data.repository.OfflineActionRepository(context)
            val intakeService = com.meditrack.app.data.repository.MedicineIntakeService(
                context,
                offlineRepo
            )

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
                    true
                }
                is com.meditrack.app.data.model.Resource.Error -> {
                    Log.e(TAG, "Failed to log medicine action: ${result.message}")
                    false
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in logMedicineAction: ${e.message}", e)
            false
        }
    }

    private fun notifyIntakeRecorded(
        context: Context,
        medicineId: String,
        reminderTime: String?,
        taken: Boolean
    ) {
        if (medicineId.isEmpty()) return
        val refreshIntent = Intent(ACTION_MEDICINE_INTAKE_RECORDED).apply {
            setPackage(context.packageName)
            putExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_ID, medicineId)
            putExtra(MedicineAlarmReceiver.EXTRA_REMINDER_TIME, reminderTime)
            putExtra(EXTRA_WAS_TAKEN, taken)
        }
        context.sendBroadcast(refreshIntent)
    }

    /**
     * Schedule a snooze alarm for the specified duration.
     */
    private fun scheduleSnoozeAlarm(
        context: Context,
        alarmId: Int,
        medicineName: String,
        medicineId: String,
        dosage: String,
        reminderTime: String?
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
            putExtra(MedicineAlarmReceiver.EXTRA_REMINDER_TIME, reminderTime)
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
