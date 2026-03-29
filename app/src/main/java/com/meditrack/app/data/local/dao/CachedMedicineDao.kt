package com.meditrack.app.data.local.dao

import androidx.room.*
import com.meditrack.app.data.local.entities.CachedMedicine
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for cached medicines.
 * Provides database access methods for offline medicine storage.
 */
@Dao
interface CachedMedicineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicine(medicine: CachedMedicine): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicines(medicines: List<CachedMedicine>)

    @Update
    suspend fun updateMedicine(medicine: CachedMedicine)

    @Delete
    suspend fun deleteMedicine(medicine: CachedMedicine)

    @Query("SELECT * FROM cached_medicines WHERE id = :medicineId")
    suspend fun getMedicineById(medicineId: String): CachedMedicine?

    @Query("SELECT * FROM cached_medicines WHERE userId = :userId AND active = 1 ORDER BY updatedAt DESC")
    suspend fun getActiveMedicinesByUser(userId: String): List<CachedMedicine>

    @Query("SELECT * FROM cached_medicines WHERE userId = :userId ORDER BY updatedAt DESC")
    suspend fun getAllMedicinesByUser(userId: String): List<CachedMedicine>

    @Query("SELECT * FROM cached_medicines WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeMedicinesByUser(userId: String): Flow<List<CachedMedicine>>

    @Query("SELECT * FROM cached_medicines WHERE userId = :userId AND active = 1 ORDER BY updatedAt DESC")
    fun observeActiveMedicinesByUser(userId: String): Flow<List<CachedMedicine>>

    @Query("SELECT * FROM cached_medicines WHERE cachedAt < :ageThreshold")
    suspend fun getStaleCache(ageThreshold: Long): List<CachedMedicine>

    @Query("SELECT COUNT(*) FROM cached_medicines WHERE userId = :userId")
    suspend fun countMedicinesByUser(userId: String): Int

    @Query("DELETE FROM cached_medicines WHERE userId = :userId AND syncedAt IS NOT NULL AND updatedAt < :beforeDate")
    suspend fun deleteSyncedOldMedicines(userId: String, beforeDate: Long): Int

    @Query("DELETE FROM cached_medicines WHERE userId = :userId")
    suspend fun deleteAllMedicinesByUser(userId: String)

    @Query("DELETE FROM cached_medicines")
    suspend fun clearAllCache()
}
