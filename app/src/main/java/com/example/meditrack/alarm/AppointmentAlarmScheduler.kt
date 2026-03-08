package com.example.meditrack.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.meditrack.data.model.Appointment
import java.text.SimpleDateFormat
import java.util.*

/**
 * Scheduler for appointment reminder alarms.
 * Schedules a notification 30 minutes before each appointment.
 */
class AppointmentAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "ApptAlarmScheduler"
        private const val REMINDER_OFFSET_MINUTES = 30L
    }

    /**
     * Schedule a reminder notification 30 minutes before the appointment.
     * If the reminder time has already passed, it is silently skipped.
     */
    fun scheduleReminder(appointment: Appointment) {
        val triggerTime = computeTriggerTime(appointment)
        if (triggerTime == null || triggerTime <= System.currentTimeMillis()) {
            Log.d(TAG, "Skipping reminder for ${appointment.id} — time already passed or invalid")
            return
        }

        val alarmId = deterministicAlarmId(appointment.id)

        val intent = Intent(context, AppointmentReminderReceiver::class.java).apply {
            putExtra(AppointmentReminderReceiver.EXTRA_APPOINTMENT_ID, appointment.id)
            putExtra(AppointmentReminderReceiver.EXTRA_DOCTOR_NAME, appointment.doctorName)
            putExtra(AppointmentReminderReceiver.EXTRA_PATIENT_NAME, appointment.patientName)
            putExtra(AppointmentReminderReceiver.EXTRA_START_TIME, appointment.startTime)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactAlarm(triggerTime, pendingIntent)
        Log.d(TAG, "Scheduled appointment reminder $alarmId at ${Date(triggerTime)} for appt ${appointment.id}")
    }

    /**
     * Cancel a previously scheduled reminder for the given appointment.
     */
    fun cancelReminder(appointmentId: String) {
        val alarmId = deterministicAlarmId(appointmentId)
        val intent = Intent(context, AppointmentReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d(TAG, "Cancelled appointment reminder $alarmId for $appointmentId")
    }

    // ── Private helpers ──

    /**
     * Compute the trigger time = appointment date+startTime − 30 minutes.
     */
    private fun computeTriggerTime(appointment: Appointment): Long? {
        return try {
            val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
            val parsed = timeFmt.parse(appointment.startTime) ?: return null

            val cal = Calendar.getInstance().apply {
                time = appointment.date
                val timeCal = Calendar.getInstance().apply { time = parsed }
                set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MINUTE, -REMINDER_OFFSET_MINUTES.toInt())
            }
            cal.timeInMillis
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute trigger time: ${e.message}")
            null
        }
    }

    /**
     * Deterministic alarm ID derived from the appointment document ID.
     * Offset by 100_000 to avoid collision with medicine alarm IDs.
     */
    private fun deterministicAlarmId(appointmentId: String): Int =
        100_000 + (appointmentId.hashCode() and 0x7FFFFFFF) % 100_000

    /**
     * Schedule an exact alarm using the best available API for the current Android version.
     */
    private fun scheduleExactAlarm(triggerTime: Long, pendingIntent: PendingIntent) {
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                        )
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                    )
                }
                else -> {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm, falling back to inexact", e)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }
}

