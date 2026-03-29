package com.meditrack.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for caching appointments locally.
 * Persists across app restarts to enable offline access to appointment schedule.
 *
 * Used when:
 * - User is offline and opens appointments list
 * - Appointment data stale (older than 24 hours)
 * - Network request fails with error
 */
@Entity(
    tableName = "cached_appointments",
    indices = [
        Index("userId"),
        Index("doctorId"),
        Index("appointmentDate"),
        Index("status")
    ]
)
data class CachedAppointment(
    @PrimaryKey
    val id: String,

    // ── Participants ───────────────────────────────────
    val userId: String,                    // Patient ID
    val doctorId: String,                  // Doctor ID

    // ── Appointment Details ────────────────────────────
    val doctorName: String,
    val specialization: String,
    val appointmentDate: Long,             // Timestamp (date + time)
    val duration: Int = 30,                // Minutes
    val status: String,                    // SCHEDULED, COMPLETED, CANCELLED

    // ── Telemedicine ───────────────────────────────────
    val isTelemedicine: Boolean = false,
    val callUrl: String = "",
    val meetingId: String = "",

    // ── Location ───────────────────────────────────────
    val location: String = "",             // Physical location or "Online"

    // ── Cancellation Details ───────────────────────────
    val cancellationFee: Double = 0.0,
    val cancellationDeadlineHours: Int = 2,
    val cancellationFeeApplied: Double = 0.0,

    // ── Notes ──────────────────────────────────────────
    val notes: String = "",
    val reasonForVisit: String = "",

    // ── Cache Metadata ────────────────────────────────
    val cachedAt: Long,                    // When cached locally
    val syncedAt: Long? = null,            // When last synced to Firestore

    // ── Server Timestamps ─────────────────────────────
    val createdAt: Long,
    val updatedAt: Long
) {
    constructor() : this(
        id = "",
        userId = "",
        doctorId = "",
        doctorName = "",
        specialization = "",
        appointmentDate = System.currentTimeMillis(),
        status = "SCHEDULED",
        cachedAt = System.currentTimeMillis(),
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}
