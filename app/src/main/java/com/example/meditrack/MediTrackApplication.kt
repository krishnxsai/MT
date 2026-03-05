package com.example.meditrack

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.meditrack.data.sync.DataSyncWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import java.util.concurrent.TimeUnit

class MediTrackApplication : Application() {

    companion object {
        private const val TAG = "MediTrackApp"
        const val NOTIFICATION_CHANNEL_ALARM = "medicine_alarm_channel"
        const val NOTIFICATION_CHANNEL_GENERAL = "general_channel"
        const val NOTIFICATION_CHANNEL_SYNC = "sync_channel"
        private const val SYNC_WORK_NAME = "periodic_data_sync"
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        
        // Enable Firestore offline persistence with cache settings
        enableFirestoreOfflinePersistence()
        
        // Create notification channels
        createNotificationChannels()
        
        // Schedule periodic sync work
        schedulePeriodicSync()

        Log.d(TAG, "MediTrack Application initialized")
    }

    /**
     * Enable Firestore offline persistence for reliable offline support.
     * Uses persistent cache with 100MB limit.
     */
    private fun enableFirestoreOfflinePersistence() {
        try {
            val firestore = FirebaseFirestore.getInstance()
            
            val cacheSettings = PersistentCacheSettings.newBuilder()
                .setSizeBytes(100 * 1024 * 1024) // 100 MB cache
                .build()
            
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(cacheSettings)
                .build()
            
            firestore.firestoreSettings = settings
            
            Log.d(TAG, "Firestore offline persistence enabled with 100MB cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure Firestore persistence: ${e.message}")
        }
    }

    /**
     * Schedule periodic sync using WorkManager.
     * Syncs offline data when network is available.
     */
    private fun schedulePeriodicSync() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<DataSyncWorker>(
                15, TimeUnit.MINUTES,  // Repeat interval
                5, TimeUnit.MINUTES    // Flex interval
            )
                .setConstraints(constraints)
                .addTag("sync")
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )

            Log.d(TAG, "Periodic sync scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule periodic sync: ${e.message}")
        }
    }

    /**
     * Create notification channels for Android 8.0+
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // High-priority alarm channel for medicine reminders
            val alarmChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ALARM,
                "Medicine Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical medicine reminder alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                
                // Set alarm sound
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setSound(
                    alarmSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(alarmChannel)

            // General notifications channel
            val generalChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_GENERAL,
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications"
            }
            notificationManager.createNotificationChannel(generalChannel)

            // Sync notifications channel (low priority)
            val syncChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_SYNC,
                "Sync Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background sync notifications"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(syncChannel)

            Log.d(TAG, "Notification channels created")
        }
    }
}

