package com.meditrack.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for caching health logs locally.
 * Persists across app restarts to enable offline access to vital signs history.
 *
 * Used when:
 * - User is offline and opens health logs
 * - Health log data stale (older than 7 days)
 * - Network request fails with error
 */
@Entity(
    tableName = "cached_health_logs",
    indices = [
        Index("userId"),
        Index("date"),
        Index("alertLevel")
    ]
)
data class CachedHealthLog(
    @PrimaryKey
    val id: String,

    // ── User Reference ────────────────────────────────
    val userId: String,

    // ── Vital Signs ───────────────────────────────────
    val bloodPressureSystolic: Int? = null,       // e.g. 120
    val bloodPressureDiastolic: Int? = null,      // e.g. 80
    val heartRate: Int? = null,                    // bpm
    val temperature: Double? = null,               // Celsius
    val bloodSugar: Int? = null,                   // mg/dL
    val weight: Double? = null,                    // kg
    val oxygenSaturation: Int? = null,            // SpO2 %

    // ── Symptom Notes ──────────────────────────────
    val symptoms: String = "",                     // User-entered notes
    val mood: String = "",                         // GOOD, OKAY, BAD, CRITICAL
    val energyLevel: String = "",                  // HIGH, MEDIUM, LOW

    // ── Date & Time ────────────────────────────────
    val date: Long,                                // Timestamp of log entry
    val loggedAt: Long,                            // When actually recorded

    // ── Alert Status ────────────────────────────────
    val alertLevel: String = "NORMAL",            // NORMAL, WARNING, CRITICAL
    val alertMessage: String = "",
    val requiresAttention: Boolean = false,

    // ── Medical Context ─────────────────────────────
    val lastAppointmentId: String? = null,

    // ── Cache Metadata ────────────────────────────
    val cachedAt: Long,                            // When cached locally
    val syncedAt: Long? = null,                    // When last synced to Firestore

    // ── Server Timestamps ─────────────────────────────
    val createdAt: Long,
    val updatedAt: Long
) {
    constructor() : this(
        id = "",
        userId = "",
        date = System.currentTimeMillis(),
        loggedAt = System.currentTimeMillis(),
        cachedAt = System.currentTimeMillis(),
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}
