package com.meditrack.app.data.local.repository

import android.util.Log
import com.meditrack.app.data.local.dao.PendingActionDao
import com.meditrack.app.data.local.entities.PendingAction
import com.meditrack.app.data.local.entities.PendingActionStatus
import com.meditrack.app.data.local.entities.ResourceType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository for managing pending offline actions.
 * Provides database access for queuing, syncing, and retrying write operations.
 *
 * **Responsibilities**:
 * - Queue offline write operations (CREATE/UPDATE/DELETE)
 * - Track retry attempts with exponential backoff
 * - Expose pending actions for UI feedback
 * - Support sync processing via SyncWorker
 * - Cleanup old actions (success/failed)
 *
 * **Usage**:
 * ```
 * // Queue offline action
 * val action = PendingAction(
 *     actionType = PendingAction.ACTION_UPDATE,
 *     resourceType = PendingAction.RESOURCE_MEDICINE,
 *     resourceId = medicineId,
 *     userId = userId,
 *     dataJson = medicineJsonData
 * )
 * repository.queueAction(action)
 *
 * // Get actions ready to sync
 * val toSync = repository.getActionsReadyForSync()
 * for (action in toSync) {
 *     try {
 *         syncToFirestore(action)
 *         repository.markSynced(action.id)
 *     } catch (e: Exception) {
 *         repository.markFailed(action.id, e.message)
 *     }
 * }
 * ```
 */
class PendingActionRepository(private val dao: PendingActionDao) {

    companion object {
        private const val TAG = "PendingActionRepository"
        private const val MAX_QUEUE_SIZE = 10_000   // User-configured: 10,000 actions
        private const val CLEANUP_AGE_MS = 7 * 24 * 60 * 60 * 1000L  // 7 days
    }

    /**
     * Queue an offline action (CREATE/UPDATE/DELETE).
     * Called when:
     * - User is offline and saves changes
     * - Network request fails
     * - Sync fails and needs retry
     *
     * Automatically handles queue overflow by dropping oldest QUEUED actions.
     */
    suspend fun queueAction(action: PendingAction) {
        try {
            // Check if action already exists (idempotency)
            val existing = dao.getLatestActionForResource(action.resourceId)
            if (existing != null && existing.status == PendingActionStatus.QUEUED.name) {
                // Update existing queued action (retry or modification)
                dao.updateAction(
                    action.copy(
                        id = existing.id,  // Preserve ID
                        retryCount = 0,    // Reset retry count
                        nextRetryAt = null,
                        status = PendingActionStatus.QUEUED.name,
                        timestamp = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "Updated pending action for resource ${action.resourceId}")
            } else {
                // Insert new action
                val pendingCount = dao.countPendingActions()
                if (pendingCount >= MAX_QUEUE_SIZE) {
                    // Queue full - drop oldest QUEUED actions
                    Log.w(TAG, "Queue full ($pendingCount). Dropping oldest QUEUED actions.")
                    dropOldestQueuedActions(MAX_QUEUE_SIZE / 10)  // Drop 10% to make room
                }

                dao.insertAction(
                    action.copy(
                        id = UUID.randomUUID().toString(),
                        status = PendingActionStatus.QUEUED.name,
                        timestamp = System.currentTimeMillis(),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "Queued new action: ${action.actionType} ${action.resourceType}/${action.resourceId}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing action", e)
            throw e
        }
    }

    /**
     * Get actions ready to sync (never synced before).
     * Returns up to 50 actions with status QUEUED or CONFLICT.
     */
    suspend fun getActionsReadyForSync(limit: Int = 50): List<PendingAction> {
        return try {
            dao.getActionsReadyForSync(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting actions ready for sync", e)
            emptyList()
        }
    }

    /**
     * Get actions ready for retry (previously failed, retry time reached).
     * Uses exponential backoff: 5min → 15min → 30min → 1h
     */
    suspend fun getActionsReadyForRetry(limit: Int = 50): List<PendingAction> {
        return try {
            val now = System.currentTimeMillis()
            dao.getActionsReadyForRetry(now, limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting actions ready for retry", e)
            emptyList()
        }
    }

    /**
     * Mark action as currently syncing (in-flight).
     * Called before attempting to sync to Firestore.
     */
    suspend fun markSyncing(actionId: String) {
        try {
            val action = dao.getActionById(actionId) ?: return
            dao.updateAction(
                action.copy(
                    status = PendingActionStatus.SYNCING.name,
                    updatedAt = System.currentTimeMillis()
                )
            )
            Log.d(TAG, "Marked action $actionId as SYNCING")
        } catch (e: Exception) {
            Log.e(TAG, "Error marking action as syncing", e)
        }
    }

    /**
     * Mark action as successfully synced.
     * Removes from retry queue; can be cleaned up after TTL.
     */
    suspend fun markSynced(actionId: String) {
        try {
            val action = dao.getActionById(actionId) ?: return
            dao.updateAction(
                action.copy(
                    status = PendingActionStatus.SUCCESS.name,
                    syncedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            Log.d(TAG, "Marked action $actionId as SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "Error marking action as synced", e)
        }
    }

    /**
     * Mark action as failed and schedule retry.
     * Uses exponential backoff based on retryCount.
     */
    suspend fun markFailed(actionId: String, errorMessage: String = "") {
        try {
            val action = dao.getActionById(actionId) ?: return

            if (action.retryCount + 1 >= action.maxRetries) {
                // Max retries exceeded - mark as failed permanently
                dao.updateAction(
                    action.copy(
                        status = PendingActionStatus.FAILED.name,
                        lastRetryError = errorMessage,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                Log.w(TAG, "Action $actionId failed permanently after ${action.retryCount} retries")
            } else {
                // Schedule for retry with exponential backoff
                val nextRetryTime = calculateNextRetryTime(action.retryCount)
                dao.updateAction(
                    action.copy(
                        retryCount = action.retryCount + 1,
                        status = PendingActionStatus.QUEUED.name,  // Back to QUEUED for retry
                        nextRetryAt = nextRetryTime,
                        lastRetryError = errorMessage,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                Log.d(
                    TAG,
                    "Scheduled action $actionId for retry (attempt ${action.retryCount + 1}) at $nextRetryTime"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking action as failed", e)
        }
    }

    /**
     * Mark action with conflict (local vs remote divergence).
     * Will be retried with conflict resolution applied.
     */
    suspend fun markConflict(actionId: String) {
        try {
            val action = dao.getActionById(actionId) ?: return
            dao.updateAction(
                action.copy(
                    status = PendingActionStatus.CONFLICT.name,
                    nextRetryAt = System.currentTimeMillis(),  // Retry immediately
                    updatedAt = System.currentTimeMillis()
                )
            )
            Log.d(TAG, "Marked action $actionId as CONFLICT")
        } catch (e: Exception) {
            Log.e(TAG, "Error marking action as conflict", e)
        }
    }

    /**
     * Get total count of pending actions.
     */
    suspend fun getPendingCount(): Int {
        return try {
            dao.countPendingActions()
        } catch (e: Exception) {
            Log.e(TAG, "Error counting pending actions", e)
            0
        }
    }

    /**
     * Observe pending action count (reactive).
     * Used by UI to show pending operations badge.
     */
    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    /**
     * Get all pending actions by resource type.
     */
    suspend fun getActionsForResourceType(resourceType: String): List<PendingAction> {
        return try {
            dao.getActionsByResourceType(resourceType)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting actions for resource type", e)
            emptyList()
        }
    }

    /**
     * Delete action (when no longer needed).
     */
    suspend fun deleteAction(actionId: String) {
        try {
            dao.deleteActionById(actionId)
            Log.d(TAG, "Deleted action $actionId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting action", e)
        }
    }

    /**
     * Delete all successful actions older than TTL.
     * Called periodically by CleanupWorker.
     */
    suspend fun cleanupOldSuccessfulActions(): Int {
        return try {
            val beforeDate = System.currentTimeMillis() - CLEANUP_AGE_MS
            val deleted = dao.deleteOldSuccessfulActions(beforeDate)
            Log.d(TAG, "Cleaned up $deleted old successful actions")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old actions", e)
            0
        }
    }

    /**
     * Delete all failed actions older than TTL.
     */
    suspend fun cleanupOldFailedActions(): Int {
        return try {
            val beforeDate = System.currentTimeMillis() - CLEANUP_AGE_MS
            val deleted = dao.deleteOldFailedActions(beforeDate)
            Log.d(TAG, "Cleaned up $deleted old failed actions")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old failed actions", e)
            0
        }
    }

    /**
     * Clear all pending actions for a user (e.g., on logout).
     */
    suspend fun clearActionsForUser(userId: String) {
        try {
            dao.deleteActionsForUser(userId)
            Log.d(TAG, "Cleared all actions for user $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing actions for user", e)
        }
    }

    /**
     * Calculate next retry time using exponential backoff.
     * - Attempt 1 (0 retries): 5 minutes
     * - Attempt 2 (1 retry): 15 minutes
     * - Attempt 3 (2 retries): 30 minutes
     * - Attempt 4+ (3+ retries): 1 hour
     */
    private fun calculateNextRetryTime(retryCount: Int): Long {
        val delayMs = when (retryCount) {
            0 -> 5 * 60 * 1000L        // 5 minutes
            1 -> 15 * 60 * 1000L       // 15 minutes
            2 -> 30 * 60 * 1000L       // 30 minutes
            else -> 60 * 60 * 1000L    // 1 hour
        }
        return System.currentTimeMillis() + delayMs
    }

    /**
     * Drop oldest QUEUED actions to prevent queue overflow.
     * Preserves SYNCING actions (in-flight).
     */
    private suspend fun dropOldestQueuedActions(count: Int) {
        try {
            val toDelete = dao.getActionsByStatus(PendingActionStatus.QUEUED.name)
                .sortedBy { it.timestamp }
                .take(count)

            toDelete.forEach { dao.deleteAction(it) }
            Log.w(TAG, "Dropped $count oldest queued actions due to queue overflow")
        } catch (e: Exception) {
            Log.e(TAG, "Error dropping oldest actions", e)
        }
    }
}
