package com.meditrack.app.data.sync

import android.content.Context
import android.util.Log
import com.meditrack.app.data.local.MediTrackDatabase
import com.meditrack.app.data.local.entities.PendingAction
import com.meditrack.app.data.local.repository.PendingActionRepository
import com.meditrack.app.data.local.service.CacheManager
import kotlinx.coroutines.flow.Flow

/**
 * High-level API for offline data management.
 *
 * **Purpose**: Single entry point for all offline data operations.
 *
 * **Capabilities**:
 * - Queue offline actions (medicines, appointments, health logs, orders)
 * - Track pending operations count
 * - Manage cache lifecycle
 * - Coordinate worker scheduling
 *
 * **Usage**:
 * ```kotlin
 * val offlineManager = OfflineDataManager(context)
 *
 * // Queue offline action
 * val action = PendingAction(
 *     actionType = PendingAction.ACTION_CREATE,
 *     resourceType = PendingAction.RESOURCE_MEDICINE,
 *     resourceId = "med123",
 *     userId = currentUserId,
 *     dataJson = medicineJson
 * )
 * offlineManager.queueAction(action)
 *
 * // Monitor pending count
 * offlineManager.observePendingCount().collect { count ->
 *     updateUI(count)
 * }
 *
 * // Clean cache on logout
 * offlineManager.clearUserData(userId)
 * ```
 */
class OfflineDataManager(private val context: Context) {

    companion object {
        private const val TAG = "OfflineDataManager"
        private var instance: OfflineDataManager? = null

        /**
         * Get singleton instance.
         */
        fun getInstance(context: Context): OfflineDataManager {
            return instance ?: synchronized(this) {
                OfflineDataManager(context).also { instance = it }
            }
        }
    }

    private val database = MediTrackDatabase.getInstance(context)
    private val pendingActionRepository = PendingActionRepository(database.pendingActionDao())
    private val cacheManager = CacheManager(
        database.cachedMedicineDao(),
        database.cachedAppointmentDao(),
        database.cachedHealthLogDao()
    )

    /**
     * Initialize offline infrastructure.
     * Called once during app startup.
     */
    fun initialize() {
        Log.d(TAG, "Initializing offline data infrastructure")
        try {
            // Schedule sync workers
            SyncScheduler.scheduleSyncJobs(context)
            Log.d(TAG, "Offline infrastructure initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing offline infrastructure", e)
        }
    }

    /**
     * Queue an offline action.
     * Used when app is offline or operation fails.
     *
     * @param action PendingAction to queue
     */
    suspend fun queueAction(action: PendingAction) {
        try {
            Log.d(TAG, "Queueing action: ${action.actionType} ${action.resourceType}/${action.resourceId}")
            pendingActionRepository.queueAction(action)
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing action", e)
            throw e
        }
    }

    /**
     * Get actions ready to sync (never synced before).
     *
     * @param limit Max number of actions to return (default 50)
     * @return List of actions ready for sync
     */
    suspend fun getActionsReadyForSync(limit: Int = 50): List<PendingAction> {
        return try {
            pendingActionRepository.getActionsReadyForSync(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting actions ready for sync", e)
            emptyList()
        }
    }

    /**
     * Get actions ready for retry (previously failed).
     *
     * @param limit Max number of actions to return (default 50)
     * @return List of actions ready for retry
     */
    suspend fun getActionsReadyForRetry(limit: Int = 50): List<PendingAction> {
        return try {
            pendingActionRepository.getActionsReadyForRetry(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting actions ready for retry", e)
            emptyList()
        }
    }

    /**
     * Mark action as successfully synced.
     *
     * @param actionId Action ID to mark as synced
     */
    suspend fun markActionSynced(actionId: String) {
        try {
            pendingActionRepository.markSynced(actionId)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking action as synced", e)
        }
    }

    /**
     * Mark action as failed and schedule retry.
     *
     * @param actionId Action ID to mark as failed
     * @param errorMessage Error message to store
     */
    suspend fun markActionFailed(actionId: String, errorMessage: String = "") {
        try {
            pendingActionRepository.markFailed(actionId, errorMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking action as failed", e)
        }
    }

    /**
     * Mark action with conflict.
     *
     * @param actionId Action ID with conflict
     */
    suspend fun markActionConflict(actionId: String) {
        try {
            pendingActionRepository.markConflict(actionId)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking action as conflict", e)
        }
    }

    /**
     * Get total count of pending actions.
     *
     * @return Count of pending actions
     */
    suspend fun getPendingCount(): Int {
        return try {
            pendingActionRepository.getPendingCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending count", e)
            0
        }
    }

    /**
     * Observe pending action count (reactive).
     * Used by UI to show pending operations badge.
     *
     * @return Flow of pending action counts
     */
    fun observePendingCount(): Flow<Int> {
        return pendingActionRepository.observePendingCount()
    }

    /**
     * Check if medicine cache is fresh.
     *
     * @return True if cache is fresh and can be used
     */
    suspend fun isMedicineCacheFresh(): Boolean {
        return try {
            cacheManager.isMedicineCacheFresh()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking medicine cache freshness", e)
            false
        }
    }

    /**
     * Check if appointment cache is fresh.
     */
    suspend fun isAppointmentCacheFresh(): Boolean {
        return try {
            cacheManager.isAppointmentCacheFresh()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking appointment cache freshness", e)
            false
        }
    }

    /**
     * Check if health log cache is fresh.
     */
    suspend fun isHealthLogCacheFresh(): Boolean {
        return try {
            cacheManager.isHealthLogCacheFresh()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking health log cache freshness", e)
            false
        }
    }

    /**
     * Clear all cache for a user (on logout).
     *
     * @param userId User ID to clear cache for
     */
    suspend fun clearUserData(userId: String) {
        try {
            Log.d(TAG, "Clearing all offline data for user $userId")
            cacheManager.clearUserCache(userId)
            pendingActionRepository.clearActionsForUser(userId)
            Log.d(TAG, "Cleared all offline data for user $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing user data", e)
        }
    }

    /**
     * Force cache refresh on network reconnect.
     */
    suspend fun invalidateAllCache() {
        try {
            Log.d(TAG, "Invalidating all cache (forcing refresh)")
            cacheManager.invalidateAllCache()
        } catch (e: Exception) {
            Log.e(TAG, "Error invalidating cache", e)
        }
    }

    /**
     * Trigger immediate sync.
     * Use this after network reconnect or user action.
     */
    fun triggerImmediateSync() {
        Log.d(TAG, "Triggering immediate sync")
        SyncScheduler.triggerImmediateSync(context)
    }

    /**
     * Cleanup infrastructure on app shutdown.
     */
    fun shutdown() {
        Log.d(TAG, "Shutting down offline infrastructure")
        SyncScheduler.cancelAllWorkers(context)
        instance = null
    }
}
