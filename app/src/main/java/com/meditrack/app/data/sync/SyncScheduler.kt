package com.meditrack.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Manages WorkManager job scheduling for offline sync infrastructure.
 *
 * **Workers Managed**:
 * 1. DataSyncWorker - Sync pending offline actions to Firestore (every 15 minutes)
 * 2. RetentionCleanupWorker - Cleanup old Firestore records (daily at 2am)
 * 3. LocalCleanupWorker - Cleanup local Room database (daily at 3am)
 *
 * **Usage**:
 * ```kotlin
 * val scheduler = SyncScheduler(context)
 * scheduler.scheduleSyncJobs()  // Call on app startup
 * ```
 */
object SyncScheduler {

    private const val TAG = "SyncScheduler"

    // Worker tags for identification
    private const val TAG_DATA_SYNC = "data_sync"
    private const val TAG_RETENTION_CLEANUP = "retention_cleanup"
    private const val TAG_LOCAL_CLEANUP = "local_cleanup"

    /**
     * Schedule all offline sync workers.
     * Should be called once during app initialization.
     */
    fun scheduleSyncJobs(context: Context) {
        Log.d(TAG, "Scheduling sync infrastructure workers")

        scheduleDataSyncWorker(context)
        scheduleRetentionCleanupWorker(context)
        scheduleLocalCleanupWorker(context)

        Log.d(TAG, "All workers scheduled successfully")
    }

    /**
     * Schedule DataSyncWorker to run every 15 minutes.
     * Syncs pending offline actions to Firestore when network is available.
     *
     * **Constraints**:
     * - Requires network connectivity
     * - Prefers charging to save battery
     * - Allows idle device state
     */
    private fun scheduleDataSyncWorker(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<DataSyncWorker>(
            15,          // Interval
            TimeUnit.MINUTES,
            5,           // Flex interval for scheduling flexibility
            TimeUnit.MINUTES
        )
            .addTag(TAG_DATA_SYNC)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(false)  // Sync even on low battery
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,   // Initial backoff
                TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "data_sync_job",
            ExistingPeriodicWorkPolicy.KEEP,  // Don't reschedule if already running
            workRequest
        )

        Log.d(TAG, "DataSyncWorker scheduled: 15 minutes interval")
    }

    /**
     * Schedule RetentionCleanupWorker to run daily at 2am.
     * Cleans up old Firestore records according to data retention policy.
     *
     * **Constraints**:
     * - Prefers idle device
     * - Prefers charging
     * - Requires network (to delete from Firestore)
     */
    private fun scheduleRetentionCleanupWorker(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<RetentionCleanupWorker>(
            24,          // Daily interval
            TimeUnit.HOURS,
            1,           // 1 hour flex window
            TimeUnit.HOURS
        )
            .addTag(TAG_RETENTION_CLEANUP)
            // This will run at 2am if scheduled during that time
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresDeviceIdle(true)
                    .setRequiresCharging(true)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                24,   // 1 day initial backoff
                TimeUnit.HOURS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "retention_cleanup_job",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        Log.d(TAG, "RetentionCleanupWorker scheduled: daily interval (prefers 2am)")
    }

    /**
     * Schedule LocalCleanupWorker to run daily at 3am.
     * Cleans up local Room database (old pending actions, stale cache).
     *
     * **Constraints**:
     * - Prefers idle device
     * - Prefers charging
     * - Does NOT require network (local cleanup only)
     */
    private fun scheduleLocalCleanupWorker(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<LocalCleanupWorker>(
            24,          // Daily interval
            TimeUnit.HOURS,
            1,           // 1 hour flex window
            TimeUnit.HOURS
        )
            .addTag(TAG_LOCAL_CLEANUP)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresDeviceIdle(true)
                    .setRequiresCharging(true)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                24,   // 1 day initial backoff
                TimeUnit.HOURS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "local_cleanup_job",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        Log.d(TAG, "LocalCleanupWorker scheduled: daily interval (prefers 3am)")
    }

    /**
     * Trigger immediate sync (after network reconnect or user action).
     * Overrides normal schedule constraints (except for network).
     */
    fun triggerImmediateSync(context: Context) {
        Log.d(TAG, "Triggering immediate data sync")

        val workRequest = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .addTag("immediate_sync")
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                5,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "immediate_sync",
            ExistingWorkPolicy.REPLACE,  // Cancel previous, start new
            workRequest
        )
    }

    /**
     * Cancel all scheduled sync workers.
     * Useful for logout or cleanup scenarios.
     */
    fun cancelAllWorkers(context: Context) {
        Log.d(TAG, "Cancelling all sync workers")
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_DATA_SYNC)
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_RETENTION_CLEANUP)
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_LOCAL_CLEANUP)
        Log.d(TAG, "All sync workers cancelled")
    }

    /**
     * Get current worker status.
     * Useful for debugging and UI display.
     */
    fun getWorkerStatus(context: Context): WorkerStatusInfo {
        try {
            val dataSyncInfos = WorkManager.getInstance(context)
                .getWorkInfosByTagLiveData(TAG_DATA_SYNC)
            val retentionInfos = WorkManager.getInstance(context)
                .getWorkInfosByTagLiveData(TAG_RETENTION_CLEANUP)
            val localCleanupInfos = WorkManager.getInstance(context)
                .getWorkInfosByTagLiveData(TAG_LOCAL_CLEANUP)

            return WorkerStatusInfo(
                dataSyncActive = dataSyncInfos.value?.any { it.state == WorkInfo.State.RUNNING } ?: false,
                retentionCleanupActive = retentionInfos.value?.any { it.state == WorkInfo.State.RUNNING } ?: false,
                localCleanupActive = localCleanupInfos.value?.any { it.state == WorkInfo.State.RUNNING } ?: false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting worker status", e)
            return WorkerStatusInfo()
        }
    }
}

/**
 * Current status of sync workers.
 */
data class WorkerStatusInfo(
    val dataSyncActive: Boolean = false,
    val retentionCleanupActive: Boolean = false,
    val localCleanupActive: Boolean = false
)
