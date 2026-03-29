package com.meditrack.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room entity for tracking pending offline actions.
 * Used to queue write operations (CREATE/UPDATE/DELETE) that occur while offline,
 * then sync to Firestore when connectivity restored.
 *
 * Supports:
 * - Medicines: Update active status, adjust quantity
 * - Appointments: Create new appointment
 * - Health Logs: Create new health log entry
 *  - Orders: Create new order
 *
 * Retry Logic:
 * - Tracks retry count and next retry time
 * - Exponential backoff: 5min → 15min → 30min → 1h
 * - Removes after maxRetries exceeded
 * - Can be manually retried via UI
 */
@Entity(
    tableName = "pending_actions",
    indices = [
        Index("resourceType"),
        Index("status"),
        Index("timestamp"),
        Index("nextRetryAt"),
        Index("resourceId")
    ]
)
data class PendingAction(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // ── Action Type ────────────────────────────────────
    val actionType: String,                        // CREATE, UPDATE, DELETE
    val resourceType: String,                      // MEDICINE, APPOINTMENT, HEALTHLOG, ORDER

    // ── Resource Reference ────────────────────────────
    val resourceId: String,                        // medicine ID, appointment ID, etc.
    val userId: String,                            // Owner/patient ID

    // ── Data Payload ───────────────────────────────
    // Serialized JSON of full data to sync (no sensitive data)
    val dataJson: String,

    // ── Retry Tracking ────────────────────────────
    val retryCount: Int = 0,
    val maxRetries: Int = 5,
    val nextRetryAt: Long? = null,                 // When to retry next
    val lastRetryError: String = "",

    // ── Status ────────────────────────────────────
    val status: String,                            // QUEUED, SYNCING, SUCCESS, FAILED
    val syncedAt: Long? = null,

    // ── Timestamps ────────────────────────────────
    val timestamp: Long = System.currentTimeMillis(),      // When action occurred
    val createdAt: Long = System.currentTimeMillis(),      // When queued
    val updatedAt: Long = System.currentTimeMillis()       // Last status update
) {
    companion object {
        const val ACTION_CREATE = "CREATE"
        const val ACTION_UPDATE = "UPDATE"
        const val ACTION_DELETE = "DELETE"

        const val RESOURCE_MEDICINE = "MEDICINE"
        const val RESOURCE_APPOINTMENT = "APPOINTMENT"
        const val RESOURCE_HEALTHLOG = "HEALTHLOG"
        const val RESOURCE_ORDER = "ORDER"

        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_SYNCING = "SYNCING"
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_FAILED = "FAILED"
    }

    constructor() : this(
        id = "",
        actionType = ACTION_CREATE,
        resourceType = RESOURCE_MEDICINE,
        resourceId = "",
        userId = "",
        dataJson = "{}",
        status = STATUS_QUEUED
    )
}

/**
 * Enum for resource types in pending actions
 */
enum class ResourceType {
    MEDICINE,
    APPOINTMENT,
    HEALTHLOG,
    ORDER,
    PRESCRIPTION,
    CANCELLATION_REQUEST
}

/**
 * Enum for action types
 */
enum class ActionType {
    CREATE,
    UPDATE,
    DELETE
}

/**
 * Enum for pending action status
 */
enum class PendingActionStatus {
    QUEUED,           // Waiting to be processed
    SYNCING,          // Currently being synced to Firestore
    SUCCESS,          // Synced successfully
    FAILED,           // Failed after max retries
    CONFLICT          // Conflict detected during sync
}
