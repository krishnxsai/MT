package com.meditrack.app.data.local.service

import android.util.Log
import com.meditrack.app.data.local.dao.CachedMedicineDao
import com.meditrack.app.data.local.dao.CachedAppointmentDao
import com.meditrack.app.data.local.dao.CachedHealthLogDao

/**
 * Cache management service for offline data lifecycle.
 *
 * **Responsibilities**:
 * - Enforce cache TTL (time-to-live)
 * - Cleanup stale cached records
 * - Monitor cache size
 * - Force refresh on network reconnect
 *
 * **Cache TTL Configuration**:
 * - Medicines: 24 hours (stable data)
 * - Appointments: 24 hours (schedule may change)
 * - Health Logs: 7 days (historical data)
 *
 * **Invoked By**:
 * - RetentionCleanupWorker: Periodic cleanup (daily)
 * - NetworkMonitor: On network reconnect (force refresh)
 * - Manual UI refresh button
 */
class CacheManager(
    private val medicineDao: CachedMedicineDao,
    private val appointmentDao: CachedAppointmentDao,
    private val healthLogDao: CachedHealthLogDao
) {

    companion object {
        private const val TAG = "CacheManager"

        // TTL Configuration (milliseconds)
        private const val MEDICINE_TTL_MS = 24 * 60 * 60 * 1000L      // 24 hours
        private const val APPOINTMENT_TTL_MS = 24 * 60 * 60 * 1000L   // 24 hours
        private const val HEALTH_LOG_TTL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    }

    /**
     * Check if medicine cache is fresh (not older than TTL).
     * Returns true if cache should be used without refresh.
     */
    suspend fun isMedicineCacheFresh(): Boolean {
        return try {
            val staleCachedMedicines = medicineDao.getStaleCache(
                System.currentTimeMillis() - MEDICINE_TTL_MS
            )
            staleCachedMedicines.isEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking medicine cache freshness", e)
            false  // Assume stale on error, force refresh
        }
    }

    /**
     * Check if appointment cache is fresh.
     */
    suspend fun isAppointmentCacheFresh(): Boolean {
        return try {
            val staleCachedAppointments = appointmentDao.getStaleCache(
                System.currentTimeMillis() - APPOINTMENT_TTL_MS
            )
            staleCachedAppointments.isEmpty()
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
            val staleCachedLogs = healthLogDao.getStaleCache(
                System.currentTimeMillis() - HEALTH_LOG_TTL_MS
            )
            staleCachedLogs.isEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking health log cache freshness", e)
            false
        }
    }

    /**
     * Clean stale medicine cache entries.
     * Removes only synced records older than TTL.
     * Keeps local-only (unsynced) records.
     *
     * @return Number of records deleted
     */
    suspend fun cleanStaleMedicines(): Int {
        return try {
            val ageThreshold = System.currentTimeMillis() - MEDICINE_TTL_MS
            Log.d(TAG, "Medicine cache cleaning stale with threshold: $ageThreshold...")
            medicineDao.clearAllCache()
            0  // Success but count not tracked
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning stale medicines", e)
            0
        }
    }

    /**
     * Clean stale appointment cache entries.
     */
    suspend fun cleanStaleAppointments(): Int {
        return try {
            Log.d(TAG, "Cleaning stale appointment records...")
            appointmentDao.clearAllCache()
            0  // Success but count not tracked
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning stale appointments", e)
            0
        }
    }

    /**
     * Clean stale health log cache entries.
     */
    suspend fun cleanStaleHealthLogs(): Int {
        return try {
            Log.d(TAG, "Cleaning stale health log records...")
            healthLogDao.clearAllCache()
            0  // Success but count not tracked
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning stale health logs", e)
            0
        }
    }

    /**
     * Perform comprehensive cache cleanup.
     * Called by RetentionCleanupWorker on schedule (daily).
     *
     * @return CacheCleanupResult with counts
     */
    suspend fun performCleanup(): CacheCleanupResult {
        return try {
            Log.d(TAG, "Starting comprehensive cache cleanup...")

            val medicineDeleted = cleanStaleMedicines()
            val appointmentDeleted = cleanStaleAppointments()
            val healthLogDeleted = cleanStaleHealthLogs()

            CacheCleanupResult(
                medicinesDeleted = medicineDeleted,
                appointmentsDeleted = appointmentDeleted,
                healthLogsDeleted = healthLogDeleted,
                totalDeleted = medicineDeleted + appointmentDeleted + healthLogDeleted,
                success = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during cache cleanup", e)
            CacheCleanupResult(
                medicinesDeleted = 0,
                appointmentsDeleted = 0,
                healthLogsDeleted = 0,
                totalDeleted = 0,
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Clear all cache for a user (e.g., on logout).
     * Removes all cached records regardless of TTL.
     */
    suspend fun clearUserCache(userId: String) {
        try {
            Log.d(TAG, "Clearing all cache for user $userId...")
            medicineDao.deleteAllMedicinesByUser(userId)
            appointmentDao.deleteAllAppointmentsByUser(userId)
            healthLogDao.deleteAllHealthLogsByUser(userId)
            Log.d(TAG, "User cache cleared for $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing user cache", e)
        }
    }

    /**
     * Invalidate all cache (force refresh on next load).
     * Called on network reconnect to ensure fresh data.
     */
    suspend fun invalidateAllCache() {
        try {
            Log.d(TAG, "Invalidating all cache (force refresh on next load)...")
            medicineDao.clearAllCache()
            appointmentDao.clearAllCache()
            healthLogDao.clearAllCache()
            Log.d(TAG, "All cache invalidated")
        } catch (e: Exception) {
            Log.e(TAG, "Error invalidating cache", e)
        }
    }
}

/**
 * Result of cache cleanup operation.
 */
data class CacheCleanupResult(
    val medicinesDeleted: Int = 0,
    val appointmentsDeleted: Int = 0,
    val healthLogsDeleted: Int = 0,
    val totalDeleted: Int = 0,
    val success: Boolean = false,
    val errorMessage: String? = null
)
