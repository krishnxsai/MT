package com.meditrack.app.data.local.dao

import androidx.room.*
import com.meditrack.app.data.local.entities.CachedHealthLog
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for cached health logs.
 * Provides database access methods for offline health log storage.
 */
@Dao
interface CachedHealthLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthLog(log: CachedHealthLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthLogs(logs: List<CachedHealthLog>)

    @Update
    suspend fun updateHealthLog(log: CachedHealthLog)

    @Delete
    suspend fun deleteHealthLog(log: CachedHealthLog)

    @Query("SELECT * FROM cached_health_logs WHERE id = :logId")
    suspend fun getHealthLogById(logId: String): CachedHealthLog?

    @Query("SELECT * FROM cached_health_logs WHERE userId = :userId ORDER BY date DESC LIMIT 60")
    suspend fun getRecentHealthLogs(userId: String): List<CachedHealthLog>

    @Query("SELECT * FROM cached_health_logs WHERE userId = :userId ORDER BY date DESC LIMIT 60")
    fun observeRecentHealthLogs(userId: String): Flow<List<CachedHealthLog>>

    @Query("SELECT * FROM cached_health_logs WHERE userId = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getHealthLogsByDateRange(userId: String, startDate: Long, endDate: Long): List<CachedHealthLog>

    @Query("SELECT * FROM cached_health_logs WHERE userId = :userId AND alertLevel IN ('WARNING', 'CRITICAL') ORDER BY date DESC")
    suspend fun getAlertHealthLogs(userId: String): List<CachedHealthLog>

    @Query("SELECT * FROM cached_health_logs WHERE userId = :userId AND alertLevel IN ('WARNING', 'CRITICAL') ORDER BY date DESC")
    fun observeAlertHealthLogs(userId: String): Flow<List<CachedHealthLog>>

    @Query("SELECT * FROM cached_health_logs WHERE userId = :userId AND requiresAttention = 1 ORDER BY date DESC")
    suspend fun getAttentionRequiredLogs(userId: String): List<CachedHealthLog>

    @Query("SELECT * FROM cached_health_logs WHERE userId = :userId AND date = (SELECT MAX(date) FROM cached_health_logs WHERE userId = :userId)")
    suspend fun getLatestHealthLog(userId: String): CachedHealthLog?

    @Query("SELECT COUNT(*) FROM cached_health_logs WHERE userId = :userId AND alertLevel IN ('WARNING', 'CRITICAL')")
    suspend fun countAlertLogs(userId: String): Int

    @Query("SELECT * FROM cached_health_logs WHERE cachedAt < :ageThreshold")
    suspend fun getStaleCache(ageThreshold: Long): List<CachedHealthLog>

    @Query("SELECT AVG(bloodPressureSystolic) FROM cached_health_logs WHERE userId = :userId AND date >= :sinceDate")
    suspend fun getAverageBPSystolic(userId: String, sinceDate: Long): Double?

    @Query("SELECT AVG(bloodPressureDiastolic) FROM cached_health_logs WHERE userId = :userId AND date >= :sinceDate")
    suspend fun getAverageBPDiastolic(userId: String, sinceDate: Long): Double?

    @Query("SELECT AVG(heartRate) FROM cached_health_logs WHERE userId = :userId AND date >= :sinceDate")
    suspend fun getAverageHeartRate(userId: String, sinceDate: Long): Double?

    @Query("DELETE FROM cached_health_logs WHERE userId = :userId AND syncedAt IS NOT NULL AND updatedAt < :beforeDate")
    suspend fun deleteSyncedOldLogs(userId: String, beforeDate: Long): Int

    @Query("DELETE FROM cached_health_logs WHERE userId = :userId")
    suspend fun deleteAllHealthLogsByUser(userId: String)

    @Query("DELETE FROM cached_health_logs")
    suspend fun clearAllCache()
}
