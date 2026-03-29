package com.meditrack.app.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.meditrack.app.MediTrackApplication
import com.meditrack.app.R
import com.meditrack.app.data.model.NotificationType
import com.meditrack.app.data.repository.NotificationPreferenceRepository
import com.meditrack.app.ui.appointment.AppointmentsListActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that fires ~30 minutes before an appointment
 * and shows a standard notification reminding the user.
 */
class AppointmentReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ApptReminderReceiver"
        const val EXTRA_APPOINTMENT_ID = "extra_appointment_id"
        const val EXTRA_DOCTOR_NAME = "extra_doctor_name"
        const val EXTRA_PATIENT_NAME = "extra_patient_name"
        const val EXTRA_START_TIME = "extra_start_time"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appointmentId = intent.getStringExtra(EXTRA_APPOINTMENT_ID) ?: return
        val doctorName = intent.getStringExtra(EXTRA_DOCTOR_NAME) ?: "your doctor"
        val patientName = intent.getStringExtra(EXTRA_PATIENT_NAME) ?: "Patient"
        val startTime = intent.getStringExtra(EXTRA_START_TIME) ?: ""

        Log.d(TAG, "Appointment reminder fired for $appointmentId at $startTime")

        val timeText = if (startTime.isNotEmpty()) " at $startTime" else ""
        val title = "Appointment Reminder"
        val body = "Your appointment (Dr. $doctorName & $patientName)$timeText is in about 30 minutes."

        // Check notification preferences before showing notification
        val preferenceRepo = NotificationPreferenceRepository()
        CoroutineScope(Dispatchers.IO).launch {
            val isAllowed = preferenceRepo.isNotificationAllowed(NotificationType.APPOINTMENT)
            if (isAllowed) {
                showNotification(context, appointmentId, title, body)
                Log.d(TAG, "Appointment notification shown (preferences allowed)")
            } else {
                Log.d(TAG, "Appointment notification suppressed (quiet hours or disabled)")
            }
        }
    }

    private fun showNotification(context: Context, appointmentId: String, title: String, body: String) {
        val tapIntent = Intent(context, AppointmentsListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            appointmentId.hashCode(),
            tapIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, MediTrackApplication.NOTIFICATION_CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Use a unique notification ID so multiple reminders can coexist
        val notificationId = 50_000 + (appointmentId.hashCode() and 0x7FFFFFFF) % 50_000
        notificationManager.notify(notificationId, notification)
    }
}

