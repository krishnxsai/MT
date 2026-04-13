package com.meditrack.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.meditrack.app.R
import com.meditrack.app.data.model.NotificationType
import com.meditrack.app.data.repository.NotificationPreferenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that handles medicine alarm triggers.
 * This receiver starts the alarm service for sound/vibration and shows
 * a full-screen intent for high-priority medical alerts.
 */
class MedicineAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
        const val EXTRA_MEDICINE_ID = "extra_medicine_id"
        const val EXTRA_DOSAGE = "extra_dosage"
        const val EXTRA_IS_REPEATING = "extra_is_repeating"
        const val EXTRA_REMINDER_TIME = "extra_reminder_time"

        private const val CHANNEL_ID = "medicine_alarm_channel"
        private const val CHANNEL_NAME = "Medicine Alarms"
        private const val TAG = "MedicineAlarmReceiver"

        // Keep for backward compatibility with NotificationActionReceiver
        fun stopAlarmSound() {
            // Now handled by AlarmService
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm received with action: ${intent.action}")

        // Ignore boot completed or other system broadcasts - only process alarm intents
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            Log.d(TAG, "Ignoring boot broadcast - handled by BootReceiver")
            return
        }

        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)

        // Validate that this is a real alarm intent with valid data
        if (alarmId == -1 || alarmId == 0) {
            Log.w(TAG, "Invalid alarm ID ($alarmId), ignoring broadcast")
            return
        }

        val medicineName = intent.getStringExtra(EXTRA_MEDICINE_NAME)
        if (medicineName.isNullOrEmpty()) {
            Log.w(TAG, "No medicine name provided, ignoring broadcast")
            return
        }

        val medicineId = intent.getStringExtra(EXTRA_MEDICINE_ID) ?: ""
        val dosage = intent.getStringExtra(EXTRA_DOSAGE) ?: ""
        val isRepeating = intent.getBooleanExtra(EXTRA_IS_REPEATING, false)
        val reminderTime = intent.getStringExtra(EXTRA_REMINDER_TIME)

        Log.d(TAG, "Processing valid alarm: id=$alarmId, medicine=$medicineName")

        // Acquire a wake lock to ensure the device wakes up
        val wakeLock = acquireWakeLock(context)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val preferenceRepo = NotificationPreferenceRepository()
                val isAllowed = preferenceRepo.isNotificationAllowed(
                    type = NotificationType.PRESCRIPTION,
                    bypassQuietHours = true
                )

                if (isAllowed) {
                    // Start alarm playback only when medicine reminders are enabled.
                    startAlarmService(context, alarmId, medicineName, medicineId, dosage)

                    val notificationShown = showAlarmNotification(
                        context = context,
                        notificationId = alarmId,
                        medicineName = medicineName,
                        medicineId = medicineId,
                        dosage = dosage,
                        reminderTime = reminderTime
                    )

                    if (notificationShown) {
                        Log.d(TAG, "Notification shown for $medicineName")
                    } else {
                        Log.w(TAG, "Visual notification unavailable for $medicineName; alarm audio remains active")
                    }
                } else {
                    Log.d(TAG, "Medicine reminder disabled for $medicineName; skipping alarm playback")
                }

                // If this is a repeating alarm, schedule the next occurrence
                if (isRepeating && reminderTime != null) {
                    scheduleNextAlarm(context, intent, reminderTime)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed processing medicine alarm: ${e.message}", e)
            } finally {
                releaseWakeLockLater(wakeLock)
                pendingResult.finish()
            }
        }
    }

    private fun releaseWakeLockLater(wakeLock: PowerManager.WakeLock?) {
        wakeLock?.let {
            Handler(Looper.getMainLooper()).postDelayed({
                if (it.isHeld) {
                    it.release()
                }
            }, 5000)
        }
    }

    private fun acquireWakeLock(context: Context): PowerManager.WakeLock? {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "MediTrack:AlarmReceiverWakeLock"
            ).apply {
                acquire(30 * 1000L) // 30 seconds max
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock: ${e.message}")
            null
        }
    }

    private fun startAlarmService(
        context: Context,
        alarmId: Int,
        medicineName: String,
        medicineId: String,
        dosage: String
    ) {
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START_ALARM
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmService.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(AlarmService.EXTRA_MEDICINE_ID, medicineId)
            putExtra(AlarmService.EXTRA_DOSAGE, dosage)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "Alarm service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start alarm service: ${e.message}")
        }
    }

    private fun showAlarmNotification(
        context: Context,
        notificationId: Int,
        medicineName: String,
        medicineId: String,
        dosage: String,
        reminderTime: String?
    ): Boolean {
        if (!AlarmPermissionHelper.hasNotificationPermission(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; cannot display reminder notification")
            return false
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val canUseFullScreen = AlarmPermissionHelper.canUseFullScreenIntent(context)

        // Create notification channel with alarm sound
        createNotificationChannel(notificationManager)

        // Full-screen intent for lock screen display
        val fullScreenIntent = Intent(context, AlarmFullScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlarmFullScreenActivity.EXTRA_ALARM_ID, notificationId)
            putExtra(AlarmFullScreenActivity.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(AlarmFullScreenActivity.EXTRA_MEDICINE_ID, medicineId)
            putExtra(AlarmFullScreenActivity.EXTRA_DOSAGE, dosage)
            putExtra(EXTRA_REMINDER_TIME, reminderTime)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Mark as taken action
        val takenIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_TAKEN
            putExtra(EXTRA_MEDICINE_NAME, medicineName)
            putExtra(EXTRA_MEDICINE_ID, medicineId)
            putExtra(EXTRA_ALARM_ID, notificationId)
            putExtra(EXTRA_REMINDER_TIME, reminderTime)
        }
        val takenPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1000,
            takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            putExtra(EXTRA_MEDICINE_NAME, medicineName)
            putExtra(EXTRA_MEDICINE_ID, medicineId)
            putExtra(EXTRA_DOSAGE, dosage)
            putExtra(EXTRA_ALARM_ID, notificationId)
            putExtra(EXTRA_REMINDER_TIME, reminderTime)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Skip/Dismiss action
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS
            putExtra(EXTRA_MEDICINE_NAME, medicineName)
            putExtra(EXTRA_MEDICINE_ID, medicineId)
            putExtra(EXTRA_ALARM_ID, notificationId)
            putExtra(EXTRA_REMINDER_TIME, reminderTime)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 3000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenSettingsPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 4000,
            AlarmPermissionHelper.createFullScreenSettingsIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("⏰ Time to take $medicineName")
            .setContentText(if (dosage.isNotEmpty()) "Dosage: $dosage" else "Don't forget your medicine!")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("It's time to take your medicine!\n\n💊 $medicineName\n${if (dosage.isNotEmpty()) "📋 Dosage: $dosage" else ""}"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(fullScreenPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .addAction(R.drawable.ic_check, "✓ Take", takenPendingIntent)
            .addAction(R.drawable.ic_clock, "⏰ Snooze", snoozePendingIntent)
            .addAction(R.drawable.ic_close, "✗ Skip", dismissPendingIntent)
            .setDefaults(0) // We handle sound/vibration via service
        
        if (canUseFullScreen) {
            notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
        } else {
            notificationBuilder.addAction(
                R.drawable.ic_settings,
                "Enable popup",
                fullScreenSettingsPendingIntent
            )
            Log.w(TAG, "Full-screen alarm permission is disabled; showing heads-up fallback")
        }

        val notification = notificationBuilder.build()

        return try {
            notificationManager.notify(notificationId, notification)
            Log.d(TAG, "Alarm notification shown for $medicineName")
            true
        } catch (se: SecurityException) {
            Log.e(TAG, "Failed to show alarm notification due to permission error: ${se.message}")
            false
        }
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Get alarm sound URI
            val alarmSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alarm notifications for medicine reminders"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setBypassDnd(true) // Bypass Do Not Disturb
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setSound(alarmSound, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun scheduleNextAlarm(context: Context, originalIntent: Intent, reminderTime: String) {
        // This is handled by AlarmScheduler with setRepeating, but we can manually
        // reschedule for better reliability on newer Android versions
        try {
            val alarmScheduler = AlarmScheduler(context)
            val medicineId = originalIntent.getStringExtra(EXTRA_MEDICINE_ID) ?: return
            val medicineName = originalIntent.getStringExtra(EXTRA_MEDICINE_NAME) ?: return
            val dosage = originalIntent.getStringExtra(EXTRA_DOSAGE) ?: ""

            // Schedule next occurrence for tomorrow at the same time
            alarmScheduler.scheduleNextDailyAlarm(
                medicineName = medicineName,
                medicineId = medicineId,
                dosage = dosage,
                reminderTime = reminderTime
            )
            Log.d(TAG, "Next daily alarm scheduled for $medicineName at $reminderTime")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule next alarm: ${e.message}")
        }
    }
}
