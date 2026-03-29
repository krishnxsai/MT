package com.meditrack.app.data.model

/**
 * Data retention policy for MediTrack.
 * Defines how long different data types are retained before deletion.
 *
 * Compliant with:
 * - Medical Records: 3 years (Indian medical law requirement)
 * - Tax Records: 6 years (Indian tax law)
 * - Audit Trails: 18 months (default)
 */
data class DataRetentionPolicy(
    // ── Retention Periods ──────────────────────────────
    val prescriptionRetentionDays: Int = 365 * 3,      // 3 years - medical law
    val orderHistoryRetentionDays: Int = (365 * 1.5).toInt(),   // 18 months - audit/compliance
    val chatMessageRetentionDays: Int = 365 * 2,       // 2 years - communication archive
    val healthLogRetentionDays: Int = 365 * 5,         // 5 years - long-term health tracking
    val auditLogRetentionDays: Int = 365 * 3,          // 3 years - regulatory requirement
    val appointmentRetentionDays: Int = 365 * 2,       // 2 years - appointment history
    val transactionRetentionDays: Int = 365 * 3,       // 3 years - financial records

    // ── Grace Periods ──────────────────────────────────
    val deletionGracePeriodDays: Int = 30,             // 30 days before permanent deletion
    val requestDeletionGracePeriodDays: Int = 30,      // User can recover account within 30 days

    // ── Cleanup Configuration ──────────────────────────
    val cleanupEnabledDays: Int = 1,                   // Run daily
    val cleanupScheduleHour: Int = 2,                  // 2 AM (low traffic)
    val batchDeleteSize: Int = 1000,                   // Delete up to 1000 docs at a time
    val maxConcurrentBatches: Int = 5                  // Max 5 parallel delete batches
) {
    companion object {
        fun default(): DataRetentionPolicy = DataRetentionPolicy()
    }
}

/**
 * Represents a user's data deletion request.
 * Provides a grace period for account recovery.
 */
data class DeletionRequest(
    val userId: String = "",
    val requestedAt: Long = System.currentTimeMillis(),
    val scheduledDeleteDate: Long = 0L,  // System.currentTimeMillis() + (30 days in ms)
    val status: DeletionStatus = DeletionStatus.PENDING,
    val reason: String = "",
    val cancelledAt: Long? = null
)

enum class DeletionStatus {
    PENDING,        // Awaiting grace period (user can cancel)
    CONFIRMED,      // After grace period, ready to delete
    DELETED,        // Fully deleted
    CANCELLED       // User cancelled deletion request
}
