package com.meditrack.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for caching medicines locally.
 * Persists across app restarts to enable offline access.
 *
 * Used when:
 * - User is offline and opens medicine list
 * - Medicine data stale (older than 24 hours)
 * - Network request fails with error
 */
@Entity(
    tableName = "cached_medicines",
    indices = [Index("userId"), Index("active")]
)
data class CachedMedicine(
    @PrimaryKey
    val id: String,

    // ── User Reference ────────────────────────────────
    val userId: String,

    // ── Medicine Details ───────────────────────────────
    val name: String,
    val dosage: String,                    // e.g. "500mg", "10ml"
    val frequency: String,                 // e.g. "2x daily", "Once at night"
    val refillable: Boolean = false,
    val quantity: Int,                     // Current quantity
    val active: Boolean = true,

    // ── Medical Info ───────────────────────────────────
    val sideEffects: String = "",
    val warnings: String = "",
    val expiryDate: Long? = null,          // Timestamp
    val expiryMonthYear: String = "",
    val expiryWarningDays: Int = 30,

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
        name = "",
        dosage = "",
        frequency = "",
        quantity = 0,
        cachedAt = System.currentTimeMillis(),
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}
