package com.meditrack.app

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
import com.meditrack.app.data.sync.DataSyncWorker
import com.meditrack.app.data.sync.RetentionCleanupWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class MediTrackApplication : Application() {

    companion object {
        private const val TAG = "MediTrackApp"
        const val NOTIFICATION_CHANNEL_ALARM = "medicine_alarm_channel"
        const val NOTIFICATION_CHANNEL_GENERAL = "general_channel"
        const val NOTIFICATION_CHANNEL_SYNC = "sync_channel"
        const val NOTIFICATION_CHANNEL_ORDERS = "meditrack_orders"
        private const val SYNC_WORK_NAME = "periodic_data_sync"
        private const val RETENTION_CLEANUP_WORK_NAME = "retention_cleanup"
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        // Configure Remote Config early so key lookups have default values.
        initializeRemoteConfig()
        
        // Enable Firestore offline persistence with cache settings
        enableFirestoreOfflinePersistence()
        
        // Create notification channels
        createNotificationChannels()

        // Schedule periodic sync work
        schedulePeriodicSync()

        // Schedule data retention cleanup (daily at 2 AM)
        scheduleRetentionCleanup()

        Log.d(TAG, "MediTrack Application initialized")
    }

    private fun initializeRemoteConfig() {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val isDebuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val fetchInterval = if (isDebuggable) 0L else 3600L

            val settings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(fetchInterval)
                .build()

            remoteConfig.setConfigSettingsAsync(settings)
            remoteConfig.setDefaultsAsync(
                mapOf(
                    "razorpay_key_id" to ""
                )
            )

            remoteConfig.fetchAndActivate()
                .addOnSuccessListener { activated ->
                    Log.d(TAG, "Remote Config initialized (activated=$activated)")
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Remote Config init fetch failed: ${error.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Remote Config: ${e.message}")
        }
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
     * Schedule daily data retention cleanup at 2 AM (low traffic).
     * Runs daily to clean up expired records according to retention policy.
     */
    private fun scheduleRetentionCleanup() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val retentionRequest = PeriodicWorkRequestBuilder<RetentionCleanupWorker>(
                1, TimeUnit.DAYS  // Run daily
            )
                .setConstraints(constraints)
                .setInitialDelay(2, TimeUnit.HOURS)  // First run: 2 hours from app start
                .addTag("retention")
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                RETENTION_CLEANUP_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                retentionRequest
            )

            Log.d(TAG, "Data retention cleanup scheduled daily at 2 AM")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule retention cleanup: ${e.message}")
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

            // Order status notifications channel (high priority for patient alerts)
            val ordersChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ORDERS,
                "Order Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications about your medicine order status"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(ordersChannel)

            Log.d(TAG, "Notification channels created")
        }
    }
}

