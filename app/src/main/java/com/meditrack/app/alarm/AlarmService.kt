package com.meditrack.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.meditrack.app.R
import com.meditrack.app.MediTrackApplication

/**
 * Foreground service to handle alarm sound and vibration reliably.
 * This ensures the alarm continues playing even when the app is in background
 * and properly handles audio focus for medical-grade alerts.
 *
 * Medical-grade features:
 * - Bypasses silent/DND mode using USAGE_ALARM audio stream
 * - Continuous vibration until user interaction
 * - Maximum volume enforcement for alarm stream
 * - Proper audio focus handling
 * - Wake lock to prevent device sleep during alarm
 */
class AlarmService : Service() {

    companion object {
        private const val TAG = "AlarmService"
        const val CHANNEL_ID = "medicine_alarm_service_channel"
        private const val CHANNEL_NAME = "Medicine Alarm Service"
        private const val NOTIFICATION_ID = 99999

        // Maximum alarm duration (5 minutes) as safety measure
        private const val MAX_ALARM_DURATION_MS = 5 * 60 * 1000L

        const val ACTION_START_ALARM = "com.meditrack.app.ACTION_START_ALARM"
        const val ACTION_STOP_ALARM = "com.meditrack.app.ACTION_STOP_ALARM"

        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
        const val EXTRA_MEDICINE_ID = "extra_medicine_id"
        const val EXTRA_DOSAGE = "extra_dosage"

        private var instance: AlarmService? = null

        fun stopAlarm(context: Context) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop alarm service: ${e.message}")
            }
        }

        fun isRunning(): Boolean = instance != null
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var originalVolume: Int = 0
    private var originalRingerMode: Int = AudioManager.RINGER_MODE_NORMAL
    private var audioFocusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private var alarmTimeoutRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // Store original ringer mode for restoration later
        audioManager?.let {
            originalRingerMode = it.ringerMode
            originalVolume = it.getStreamVolume(AudioManager.STREAM_ALARM)
        }

        Log.d(TAG, "AlarmService created, original ringer mode: $originalRingerMode")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALARM -> {
                val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, 0)
                val medicineName = intent.getStringExtra(EXTRA_MEDICINE_NAME) ?: "Medicine"
                val medicineId = intent.getStringExtra(EXTRA_MEDICINE_ID) ?: ""
                val dosage = intent.getStringExtra(EXTRA_DOSAGE) ?: ""

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        createServiceNotification(medicineName),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, createServiceNotification(medicineName))
                }
                acquireWakeLock()
                startAlarm(alarmId, medicineName, medicineId, dosage)

                // Set up safety timeout to auto-stop alarm after max duration
                scheduleAlarmTimeout()
            }
            ACTION_STOP_ALARM -> {
                cancelAlarmTimeout()
                stopAlarmSound()
                stopVibration()
                releaseAudioFocus()
                releaseWakeLock()
                restoreVolume()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Schedule a timeout to auto-stop alarm as a safety measure.
     * This prevents the alarm from ringing indefinitely if something goes wrong.
     */
    private fun scheduleAlarmTimeout() {
        cancelAlarmTimeout()
        alarmTimeoutRunnable = Runnable {
            Log.w(TAG, "Alarm timeout reached, auto-stopping")
            stopAlarmSound()
            stopVibration()
            releaseAudioFocus()
            releaseWakeLock()
            restoreVolume()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        handler.postDelayed(alarmTimeoutRunnable!!, MAX_ALARM_DURATION_MS)
    }

    private fun cancelAlarmTimeout() {
        alarmTimeoutRunnable?.let { handler.removeCallbacks(it) }
        alarmTimeoutRunnable = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelAlarmTimeout()
        stopAlarmSound()
        stopVibration()
        releaseAudioFocus()
        releaseWakeLock()
        restoreVolume()
        instance = null
        Log.d(TAG, "AlarmService destroyed")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Service channel for medicine alarm playback"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createServiceNotification(medicineName: String): Notification {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("Medicine Alarm Active")
            .setContentText("Alarm for $medicineName is ringing")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(R.drawable.ic_close, "Stop", stopPendingIntent)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MediTrack:AlarmWakeLock"
            ).apply {
                acquire(10 * 60 * 1000L) // 10 minutes max
            }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
            Log.d(TAG, "WakeLock released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock: ${e.message}")
        }
    }

    private fun startAlarm(alarmId: Int, medicineName: String, medicineId: String, dosage: String) {
        // Request audio focus for alarm
        requestAudioFocus()

        // Set volume to maximum for alarm
        setMaxVolume()

        // Start sound
        playAlarmSound()

        // Start vibration
        startVibration()

        Log.d(TAG, "Alarm started for $medicineName (ID: $alarmId)")
    }

    /**
     * Request audio focus with USAGE_ALARM to ensure sound plays even in silent mode.
     */
    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build()

                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .build()

                audioManager?.requestAudioFocus(audioFocusRequest!!)
                Log.d(TAG, "Audio focus requested for alarm")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request audio focus: ${e.message}")
        }
    }

    /**
     * Release audio focus when alarm stops.
     */
    private fun releaseAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { request ->
                    audioManager?.abandonAudioFocusRequest(request)
                }
                audioFocusRequest = null
            }
            Log.d(TAG, "Audio focus released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release audio focus: ${e.message}")
        }
    }

    /**
     * Set alarm volume to maximum to ensure it's heard even in silent mode.
     * The STREAM_ALARM bypasses ringer mode silencing by default on most devices.
     */
    private fun setMaxVolume() {
        try {
            audioManager?.let { am ->
                // Store original values for restoration
                originalVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)

                // Get and set maximum alarm volume
                val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    maxVolume,
                    AudioManager.FLAG_ALLOW_RINGER_MODES // Allow alarm to play regardless of ringer mode
                )

                Log.d(TAG, "Alarm volume set to max: $maxVolume (was: $originalVolume)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set volume: ${e.message}")
        }
    }

    private fun restoreVolume() {
        try {
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
            Log.d(TAG, "Volume restored to: $originalVolume")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore volume: ${e.message}")
        }
    }

    private fun playAlarmSound() {
        try {
            stopAlarmSound()

            val alarmUri = resolveAlarmSoundUri()
            if (alarmUri == null) {
                Log.i(TAG, "Alarm sound disabled for reminder channel; skipping audio playback")
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                setDataSource(this@AlarmService, alarmUri)
                isLooping = true
                setOnPreparedListener { mp ->
                    mp.start()
                    Log.d(TAG, "Alarm sound started playing")
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm sound: ${e.message}")
        }
    }

    private fun resolveAlarmSoundUri(): Uri? {
        val fallbackSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val channelSound = notificationManager
                ?.getNotificationChannel(MediTrackApplication.NOTIFICATION_CHANNEL_ALARM)
                ?.sound

            if (channelSound != null) {
                Log.d(TAG, "Using reminder channel sound: $channelSound")
                return channelSound
            }

            val channelExists = notificationManager
                ?.getNotificationChannel(MediTrackApplication.NOTIFICATION_CHANNEL_ALARM) != null
            if (channelExists) {
                Log.w(TAG, "Reminder channel exists without a sound; using default alarm fallback")
                return fallbackSound
            }
        }

        return fallbackSound
    }

    private fun stopAlarmSound() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
            Log.d(TAG, "Alarm sound stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop alarm sound: ${e.message}")
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

            // Medical-grade continuous vibration pattern
            // Pattern: vibrate 1000ms, pause 500ms, vibrate 1000ms, pause 500ms (repeating)
            val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000, 500)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0)
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, amplitudes, 0)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
            Log.d(TAG, "Vibration started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration: ${e.message}")
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
            vibrator = null
            Log.d(TAG, "Vibration stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop vibration: ${e.message}")
        }
    }
}

