package com.meditrack.app.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.RepeatType
import java.util.Calendar

/**
 * Scheduler for medicine reminder alarms.
 * Handles Android 12+ exact alarm permissions and provides reliable
 * alarm scheduling for medical reminders.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "AlarmScheduler"
    }

    /**
     * Schedule alarms for all reminder times of a medicine.
     * Returns list of alarm IDs that were scheduled.
     *
     * Supports DAILY and WEEKLY repeat types. WEEKLY schedules
     * the next 7 occurrences (one per day of the week) for reliability.
     */
    fun scheduleMedicineAlarms(medicine: Medicine): List<Int> {
        val alarmIds = mutableListOf<Int>()

        if (!medicine.isActive || medicine.repeatType == RepeatType.AS_NEEDED) {
            Log.d(TAG, "Skipping alarm scheduling - medicine inactive or as-needed")
            return alarmIds
        }

        // Check exact alarm permission first
        if (!canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule exact alarms - permission not granted")
            // Still schedule but with inexact alarms as fallback
        }

        for (timeString in medicine.reminderTimes) {
            when (medicine.repeatType) {
                RepeatType.DAILY -> {
                    val alarmId = generateDeterministicAlarmId(medicine.id, timeString)
                    val triggerTime = getNextTriggerTime(timeString)

                    if (triggerTime > System.currentTimeMillis()) {
                        scheduleAlarm(
                            alarmId = alarmId,
                            triggerTime = triggerTime,
                            medicineName = medicine.name,
                            medicineId = medicine.id,
                            dosage = "${medicine.dosage} ${medicine.unit}",
                            isRepeating = true,
                            reminderTime = timeString
                        )
                        alarmIds.add(alarmId)
                        Log.d(TAG, "Scheduled DAILY alarm $alarmId for ${medicine.name} at $timeString")
                    }
                }
                RepeatType.WEEKLY -> {
                    // Schedule 7 days ahead for weekly reliability
                    for (dayOffset in 0..6) {
                        val alarmId = generateDeterministicAlarmId(medicine.id, "$timeString-day$dayOffset")
                        val triggerTime = getNextTriggerTimeWithOffset(timeString, dayOffset)

                        if (triggerTime > System.currentTimeMillis()) {
                            scheduleAlarm(
                                alarmId = alarmId,
                                triggerTime = triggerTime,
                                medicineName = medicine.name,
                                medicineId = medicine.id,
                                dosage = "${medicine.dosage} ${medicine.unit}",
                                isRepeating = false, // re-scheduled on fire
                                reminderTime = timeString
                            )
                            alarmIds.add(alarmId)
                            Log.d(TAG, "Scheduled WEEKLY alarm $alarmId for ${medicine.name} at $timeString +${dayOffset}d")
                            break // only schedule the nearest upcoming day
                        }
                    }
                }
                RepeatType.AS_NEEDED -> { /* already guarded above */ }
            }
        }

        return alarmIds
    }

    /**
     * Schedule a single alarm with all the necessary extras for reliable delivery.
     */
    private fun scheduleAlarm(
        alarmId: Int,
        triggerTime: Long,
        medicineName: String,
        medicineId: String,
        dosage: String,
        isRepeating: Boolean,
        reminderTime: String
    ) {
        val intent = Intent(context, MedicineAlarmReceiver::class.java).apply {
            putExtra(MedicineAlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_ID, medicineId)
            putExtra(MedicineAlarmReceiver.EXTRA_DOSAGE, dosage)
            putExtra(MedicineAlarmReceiver.EXTRA_IS_REPEATING, isRepeating)
            putExtra(MedicineAlarmReceiver.EXTRA_REMINDER_TIME, reminderTime)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactAlarm(triggerTime, pendingIntent, isRepeating)
    }

    /**
     * Schedule an exact alarm using the best available method for the current Android version.
     * Uses AlarmClockInfo on Android 12+ for guaranteed exact timing.
     */
    private fun scheduleExactAlarm(triggerTime: Long, pendingIntent: PendingIntent, isRepeating: Boolean) {
        try {
            when {
                // Android 12+ (API 31+): Use setAlarmClock for highest priority medical alarms
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (alarmManager.canScheduleExactAlarms()) {
                        // setAlarmClock is the most reliable for medical alarms
                        // It shows an alarm icon and has highest priority
                        val alarmClockInfo = AlarmManager.AlarmClockInfo(
                            triggerTime,
                            pendingIntent
                        )
                        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                        Log.d(TAG, "Scheduled using setAlarmClock (Android 12+)")
                    } else {
                        // Fallback to inexact alarm if permission not granted
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        Log.w(TAG, "Falling back to inexact alarm - exact alarm permission not granted")
                    }
                }

                // Android 6+ (API 23+): Use setExactAndAllowWhileIdle for Doze mode compatibility
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled using setExactAndAllowWhileIdle (Android 6+)")
                }

                // Older Android versions
                else -> {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled using setExact (older Android)")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm: ${e.message}")
            // Final fallback to non-exact alarm
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.w(TAG, "Fell back to non-exact alarm")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to schedule even non-exact alarm: ${e2.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception scheduling alarm: ${e.message}")
        }
    }

    /**
     * Schedule the next daily alarm occurrence.
     * Called after an alarm fires to schedule the next day's alarm.
     */
    fun scheduleNextDailyAlarm(
        medicineName: String,
        medicineId: String,
        dosage: String,
        reminderTime: String
    ): Int {
        val alarmId = generateDeterministicAlarmId(medicineId, reminderTime)

        // Get tomorrow's trigger time
        val triggerTime = getNextTriggerTime(reminderTime, forceNextDay = true)

        scheduleAlarm(
            alarmId = alarmId,
            triggerTime = triggerTime,
            medicineName = medicineName,
            medicineId = medicineId,
            dosage = dosage,
            isRepeating = true,
            reminderTime = reminderTime
        )

        Log.d(TAG, "Scheduled next daily alarm $alarmId at ${java.util.Date(triggerTime)}")
        return alarmId
    }

    /**
     * Cancel all alarms for a medicine.
     */
    fun cancelMedicineAlarms(alarmIds: List<Int>) {
        for (alarmId in alarmIds) {
            cancelAlarm(alarmId)
        }
        Log.d(TAG, "Cancelled ${alarmIds.size} alarms")
    }

    /**
     * Cancel a single alarm by ID.
     */
    private fun cancelAlarm(alarmId: Int) {
        val intent = Intent(context, MedicineAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
            Log.d(TAG, "Cancelled alarm $alarmId")
        }
    }

    /**
     * Calculate the next trigger time for a given time string (HH:mm format).
     * If the time has already passed today, schedules for tomorrow.
     */
    private fun getNextTriggerTime(timeString: String, forceNextDay: Boolean = false): Long {
        val parts = timeString.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the time has already passed today or we need next day, schedule for tomorrow
        if (forceNextDay || calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        return calendar.timeInMillis
    }

    /**
     * Calculate trigger time with a day offset (for WEEKLY scheduling look-ahead).
     */
    private fun getNextTriggerTimeWithOffset(timeString: String, dayOffset: Int): Long {
        val parts = timeString.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return calendar.timeInMillis
    }

    /**
     * Generate a deterministic alarm ID from medicineId + time key.
     * Stable across reboots so boot-receiver can reschedule without Firestore lookup.
     */
    private fun generateDeterministicAlarmId(medicineId: String, timeKey: String): Int {
        return ("$medicineId:$timeKey".hashCode() and 0x7FFFFFFF).coerceAtLeast(1)
    }

    /**
     * Check if exact alarms can be scheduled on this device.
     * Returns true for Android < 12 or if permission is granted.
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Get the intent to open system settings for exact alarm permission.
     * Only applicable for Android 12+.
     */
    fun getExactAlarmSettingsIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
        } else {
            null
        }
    }

    /**
     * Check if POST_NOTIFICATIONS permission is granted (Android 13+).
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
