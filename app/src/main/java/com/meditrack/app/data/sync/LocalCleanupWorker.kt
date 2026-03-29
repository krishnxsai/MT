package com.meditrack.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meditrack.app.data.local.MediTrackDatabase
import com.meditrack.app.data.local.repository.PendingActionRepository
import com.meditrack.app.data.local.service.CacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager task for cleaning up local Room database.
 * Runs daily to remove old pending actions and enforce cache TTL.
 *
 * **Responsibilities**:
 * - Clean up old successful actions (7+ days)
 * - Clean up old failed actions (7+ days)
 * - Enforce cache TTL (medicines 24h, appointments 24h, health logs 7d)
 * - Monitor pending action queue size
 * - Clear cache on network reconnect
 *
 * **Triggered**: Daily via WorkManager (flexible 24h window)
 */
class LocalCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "LocalCleanupWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting local database cleanup job")

            val database = MediTrackDatabase.getInstance(applicationContext)
            val pendingActionDao = database.pendingActionDao()
            val pendingActionRepository = PendingActionRepository(pendingActionDao)
            val cacheManager = CacheManager(
                database.cachedMedicineDao(),
                database.cachedAppointmentDao(),
                database.cachedHealthLogDao()
            )

            // ── Step 1: Clean up old pending actions ──
            val successfulDeleted = pendingActionRepository.cleanupOldSuccessfulActions()
            val failedDeleted = pendingActionRepository.cleanupOldFailedActions()
            Log.d(
                TAG,
                "Cleaned up pending actions: $successfulDeleted successful, $failedDeleted failed"
            )

            // ── Step 2: Clean up stale cache entries ──
            val cacheCleanupResult = cacheManager.performCleanup()
            Log.d(
                TAG,
                "Cache cleanup: medicines=${ cacheCleanupResult.medicinesDeleted}, " +
                    "appointments=${cacheCleanupResult.appointmentsDeleted}, " +
                    "healthLogs=${cacheCleanupResult.healthLogsDeleted}, " +
                    "total=${cacheCleanupResult.totalDeleted}"
            )

            // ── Step 3: Monitor queue size ──
            val pendingCount = pendingActionRepository.getPendingCount()
            if (pendingCount > 1000) {
                Log.w(TAG, "Pending action queue is large: $pendingCount items")
            } else {
                Log.d(TAG, "Pending action queue size: $pendingCount items")
            }

            Log.d(TAG, "Local database cleanup completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Local cleanup worker failed: ${e.message}", e)
            Result.retry()  // Retry on next scheduled run
        }
    }
}
