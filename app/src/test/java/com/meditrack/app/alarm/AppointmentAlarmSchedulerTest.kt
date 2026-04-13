package com.meditrack.app.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.meditrack.app.data.model.Appointment
import com.meditrack.app.data.model.AppointmentStatus
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class AppointmentAlarmSchedulerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val scheduler = AppointmentAlarmScheduler(context)

    @Test
    fun `scheduleReminder skips when computed trigger time is in the past`() {
        val appointment = sampleAppointment(
            id = "appt-past",
            minutesFromNow = 10
        )
        val alarmId = deterministicAppointmentAlarmId(appointment.id)

        scheduler.cancelReminder(appointment.id)
        scheduler.scheduleReminder(appointment)

        assertNull(getAppointmentPendingIntent(alarmId))
    }

    @Test
    fun `scheduleReminder creates pending intent for future reminder`() {
        val appointment = sampleAppointment(
            id = "appt-future",
            minutesFromNow = 120
        )
        val alarmId = deterministicAppointmentAlarmId(appointment.id)

        scheduler.cancelReminder(appointment.id)
        scheduler.scheduleReminder(appointment)

        assertNotNull(getAppointmentPendingIntent(alarmId))

        scheduler.cancelReminder(appointment.id)
    }

    @Test
    fun `cancelReminder removes previously scheduled reminder pending intent`() {
        val appointment = sampleAppointment(
            id = "appt-cancel",
            minutesFromNow = 180
        )
        val alarmId = deterministicAppointmentAlarmId(appointment.id)

        scheduler.scheduleReminder(appointment)
        assertNotNull(getAppointmentPendingIntent(alarmId))

        scheduler.cancelReminder(appointment.id)
        assertNull(getAppointmentPendingIntent(alarmId))
    }

    private fun sampleAppointment(id: String, minutesFromNow: Int): Appointment {
        val triggerCal = Calendar.getInstance().apply {
            add(Calendar.MINUTE, minutesFromNow)
        }

        val startHour = triggerCal.get(Calendar.HOUR_OF_DAY)
        val startMinute = triggerCal.get(Calendar.MINUTE)

        return Appointment(
            id = id,
            doctorId = "doctor-1",
            patientId = "patient-1",
            doctorName = "Doctor",
            patientName = "Patient",
            date = triggerCal.time,
            startTime = String.format(Locale.US, "%02d:%02d", startHour, startMinute),
            endTime = "23:59",
            status = AppointmentStatus.CONFIRMED
        )
    }

    private fun deterministicAppointmentAlarmId(appointmentId: String): Int {
        return 100_000 + (appointmentId.hashCode() and 0x7FFFFFFF) % 100_000
    }

    private fun getAppointmentPendingIntent(alarmId: Int): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            alarmId,
            Intent(context, AppointmentReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
