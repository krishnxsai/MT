package com.meditrack.app.data.local.dao

import androidx.room.*
import com.meditrack.app.data.local.entities.PendingAction
import com.meditrack.app.data.local.entities.PendingActionStatus
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for pending offline actions.
 * Provides database access methods for managing queued write operations.
 */
@Dao
interface PendingActionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: PendingAction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActions(actions: List<PendingAction>)

    @Update
    suspend fun updateAction(action: PendingAction)

    @Delete
    suspend fun deleteAction(action: PendingAction)

    @Query("SELECT * FROM pending_actions WHERE id = :actionId")
    suspend fun getActionById(actionId: String): PendingAction?

    @Query("SELECT * FROM pending_actions WHERE status = :status ORDER BY timestamp ASC")
    suspend fun getActionsByStatus(status: String): List<PendingAction>

    @Query("SELECT * FROM pending_actions WHERE status = :status ORDER BY timestamp ASC")
    fun observeActionsByStatus(status: String): Flow<List<PendingAction>>

    @Query("SELECT * FROM pending_actions WHERE resourceType = :resourceType ORDER BY timestamp ASC")
    suspend fun getActionsByResourceType(resourceType: String): List<PendingAction>

    @Query("SELECT * FROM pending_actions WHERE resourceType = :resourceType ORDER BY timestamp ASC")
    fun observeActionsByResourceType(resourceType: String): Flow<List<PendingAction>>

    @Query("SELECT * FROM pending_actions WHERE resourceId = :resourceId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestActionForResource(resourceId: String): PendingAction?

    @Query("SELECT * FROM pending_actions WHERE status IN ('QUEUED', 'CONFLICT') AND nextRetryAt IS NULL ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getActionsReadyForSync(limit: Int = 50): List<PendingAction>

    @Query("SELECT * FROM pending_actions WHERE status IN ('QUEUED', 'CONFLICT') AND nextRetryAt <= :now ORDER BY nextRetryAt ASC LIMIT :limit")
    suspend fun getActionsReadyForRetry(now: Long, limit: Int = 50): List<PendingAction>

    @Query("SELECT COUNT(*) FROM pending_actions WHERE status IN ('QUEUED', 'CONFLICT', 'SYNCING')")
    suspend fun countPendingActions(): Int

    @Query("SELECT COUNT(*) FROM pending_actions WHERE status IN ('QUEUED', 'CONFLICT', 'SYNCING')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_actions WHERE resourceType = :resourceType AND status IN ('QUEUED', 'CONFLICT', 'SYNCING')")
    suspend fun countPendingByResourceType(resourceType: String): Int

    @Query("SELECT * FROM pending_actions WHERE status IN ('QUEUED', 'CONFLICT', 'SYNCING') ORDER BY timestamp ASC")
    suspend fun getAllPending(): List<PendingAction>

    @Query("SELECT * FROM pending_actions WHERE status IN ('QUEUED', 'CONFLICT', 'SYNCING') ORDER BY timestamp ASC")
    fun observeAllPending(): Flow<List<PendingAction>>

    @Query("SELECT * FROM pending_actions WHERE status = 'FAILED' AND updatedAt < :beforeDate ORDER BY updatedAt ASC")
    suspend fun getOldFailedActions(beforeDate: Long): List<PendingAction>

    @Query("SELECT * FROM pending_actions WHERE status = 'SUCCESS' AND syncedAt < :beforeDate ORDER BY syncedAt ASC")
    suspend fun getOldSuccessfulActions(beforeDate: Long): List<PendingAction>

    @Query("DELETE FROM pending_actions WHERE id = :actionId")
    suspend fun deleteActionById(actionId: String)

    @Query("DELETE FROM pending_actions WHERE status = 'SUCCESS' AND syncedAt IS NOT NULL AND syncedAt < :beforeDate")
    suspend fun deleteOldSuccessfulActions(beforeDate: Long): Int

    @Query("DELETE FROM pending_actions WHERE status = 'FAILED' AND updatedAt < :beforeDate")
    suspend fun deleteOldFailedActions(beforeDate: Long): Int

    @Query("DELETE FROM pending_actions WHERE resourceId = :resourceId")
    suspend fun deleteActionsForResource(resourceId: String)

    @Query("DELETE FROM pending_actions WHERE userId = :userId")
    suspend fun deleteActionsForUser(userId: String)

    @Query("DELETE FROM pending_actions")
    suspend fun clearAllActions()

    @Query("SELECT * FROM pending_actions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentActions(limit: Int = 100): List<PendingAction>
}
