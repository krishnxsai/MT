package com.meditrack.app.data.local.dao

import androidx.room.*
import com.meditrack.app.data.local.entities.CachedAppointment
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for cached appointments.
 * Provides database access methods for offline appointment storage.
 */
@Dao
interface CachedAppointmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: CachedAppointment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointments(appointments: List<CachedAppointment>)

    @Update
    suspend fun updateAppointment(appointment: CachedAppointment)

    @Delete
    suspend fun deleteAppointment(appointment: CachedAppointment)

    @Query("SELECT * FROM cached_appointments WHERE id = :appointmentId")
    suspend fun getAppointmentById(appointmentId: String): CachedAppointment?

    @Query("SELECT * FROM cached_appointments WHERE userId = :userId AND appointmentDate >= :futureDate ORDER BY appointmentDate ASC")
    suspend fun getUpcomingAppointments(userId: String, futureDate: Long): List<CachedAppointment>

    @Query("SELECT * FROM cached_appointments WHERE userId = :userId AND appointmentDate >= :futureDate ORDER BY appointmentDate ASC")
    fun observeUpcomingAppointments(userId: String, futureDate: Long): Flow<List<CachedAppointment>>

    @Query("SELECT * FROM cached_appointments WHERE userId = :userId AND appointmentDate < :pastDate ORDER BY appointmentDate DESC")
    suspend fun getPastAppointments(userId: String, pastDate: Long): List<CachedAppointment>

    @Query("SELECT * FROM cached_appointments WHERE doctorId = :doctorId AND appointmentDate >= :futureDate ORDER BY appointmentDate ASC")
    suspend fun getDoctorUpcomingAppointments(doctorId: String, futureDate: Long): List<CachedAppointment>

    @Query("SELECT * FROM cached_appointments WHERE userId = :userId ORDER BY appointmentDate DESC")
    suspend fun getAllAppointmentsByUser(userId: String): List<CachedAppointment>

    @Query("SELECT * FROM cached_appointments WHERE userId = :userId ORDER BY appointmentDate DESC")
    fun observeAllAppointmentsByUser(userId: String): Flow<List<CachedAppointment>>

    @Query("SELECT * FROM cached_appointments WHERE status IN ('SCHEDULED', 'COMPLETED') AND appointmentDate BETWEEN :startDate AND :endDate ORDER BY appointmentDate ASC")
    suspend fun getAppointmentsByDateRange(startDate: Long, endDate: Long): List<CachedAppointment>

    @Query("SELECT COUNT(*) FROM cached_appointments WHERE userId = :userId AND status = 'SCHEDULED' AND appointmentDate >= :futureDate")
    suspend fun countUpcomingAppointments(userId: String, futureDate: Long): Int

    @Query("SELECT * FROM cached_appointments WHERE cachedAt < :ageThreshold")
    suspend fun getStaleCache(ageThreshold: Long): List<CachedAppointment>

    @Query("DELETE FROM cached_appointments WHERE userId = :userId AND syncedAt IS NOT NULL AND updatedAt < :beforeDate")
    suspend fun deleteSyncedOldAppointments(userId: String, beforeDate: Long): Int

    @Query("DELETE FROM cached_appointments WHERE userId = :userId")
    suspend fun deleteAllAppointmentsByUser(userId: String)

    @Query("DELETE FROM cached_appointments")
    suspend fun clearAllCache()
}
