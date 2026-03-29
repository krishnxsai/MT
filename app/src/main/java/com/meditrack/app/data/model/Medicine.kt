package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Medicine(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val dosage: String = "",
    val unit: String = "",
    val instructions: String = "",
    val reminderTimes: List<String> = emptyList(), // List of times like "08:00", "14:00"
    val repeatType: RepeatType = RepeatType.DAILY,
    val color: String = "teal", // Color identifier
    @get:PropertyName("isActive") @set:PropertyName("isActive")
    var isActive: Boolean = true,
    val alarmIds: List<Int> = emptyList(), // Alarm IDs for cancellation

    /** Non-null when this medicine was created by a doctor prescription. */
    val prescriptionId: String = "",
    /** True if this medicine was prescribed by a doctor — patient should not edit core fields. */
    @get:PropertyName("prescribedByDoctor") @set:PropertyName("prescribedByDoctor")
    var prescribedByDoctor: Boolean = false,

    // ── Refill tracking fields ──────────────────────────────────
    /** Current pills/units remaining. -1 means not tracked. */
    val currentQuantity: Int = -1,
    /** Total quantity per refill (e.g., 30 tablets). -1 means not set. */
    val totalQuantity: Int = -1,
    /** Alert when quantity falls below this. Default 5. */
    val lowStockThreshold: Int = 5,
    /** Whether the user wants low-stock reminders. */
    @get:PropertyName("refillReminderEnabled") @set:PropertyName("refillReminderEnabled")
    var refillReminderEnabled: Boolean = false,
    /** Date of the most recent refill. */
    val lastRefillDate: Date? = null,

    // ── Expiry tracking (GAP 3 FIX) ──────────────────────────────
    /** Medication expiry date */
    val expiryDate: Date? = null,
    /** Expiry month/year in "MM/YYYY" format for display */
    val expiryMonthYear: String = "",
    /** Alert user when expiry is within this many days */
    val expiryWarningDays: Int = 30,

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    constructor() : this(id = "")

    /** True if refill tracking is enabled (quantity is set). */
    val isRefillTrackingEnabled: Boolean
        get() = currentQuantity >= 0

    /** True if current stock is at or below the low-stock threshold. */
    val isLowStock: Boolean
        get() = isRefillTrackingEnabled && currentQuantity <= lowStockThreshold

    /** True if completely out of stock. */
    val isOutOfStock: Boolean
        get() = isRefillTrackingEnabled && currentQuantity == 0

    /** Percentage of stock remaining (0..100). Returns -1 if not tracked. */
    val stockPercentage: Int
        get() = if (totalQuantity > 0 && currentQuantity >= 0)
            ((currentQuantity.toFloat() / totalQuantity) * 100).toInt().coerceIn(0, 100)
        else -1

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "userId" to userId,
            "name" to name,
            "dosage" to dosage,
            "unit" to unit,
            "instructions" to instructions,
            "reminderTimes" to reminderTimes,
            "repeatType" to repeatType.name,
            "color" to color,
            "isActive" to isActive,
            "alarmIds" to alarmIds,
            "prescriptionId" to prescriptionId,
            "prescribedByDoctor" to prescribedByDoctor,
            "currentQuantity" to currentQuantity,
            "totalQuantity" to totalQuantity,
            "lowStockThreshold" to lowStockThreshold,
            "refillReminderEnabled" to refillReminderEnabled,
            "lastRefillDate" to lastRefillDate,
            "expiryDate" to expiryDate,
            "expiryMonthYear" to expiryMonthYear,
            "expiryWarningDays" to expiryWarningDays,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Medicine {
            @Suppress("UNCHECKED_CAST")
            return Medicine(
                id = id,
                userId = map["userId"] as? String ?: "",
                name = map["name"] as? String ?: "",
                dosage = map["dosage"] as? String ?: "",
                unit = map["unit"] as? String ?: "",
                instructions = map["instructions"] as? String ?: "",
                reminderTimes = (map["reminderTimes"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                repeatType = try {
                    RepeatType.valueOf(map["repeatType"] as? String ?: "DAILY")
                } catch (e: Exception) {
                    RepeatType.DAILY
                },
                color = map["color"] as? String ?: "teal",
                isActive = map["isActive"] as? Boolean ?: true,
                alarmIds = (map["alarmIds"] as? List<*>)?.filterIsInstance<Number>()?.map { it.toInt() } ?: emptyList(),
                prescriptionId = map["prescriptionId"] as? String ?: "",
                prescribedByDoctor = map["prescribedByDoctor"] as? Boolean ?: false,
                currentQuantity = (map["currentQuantity"] as? Number)?.toInt() ?: -1,
                totalQuantity = (map["totalQuantity"] as? Number)?.toInt() ?: -1,
                lowStockThreshold = (map["lowStockThreshold"] as? Number)?.toInt() ?: 5,
                refillReminderEnabled = map["refillReminderEnabled"] as? Boolean ?: false,
                lastRefillDate = (map["lastRefillDate"] as? Timestamp)?.toDate(),
                createdAt = (map["createdAt"] as? Timestamp)?.toDate() ?: map["createdAt"] as? Date,
                updatedAt = (map["updatedAt"] as? Timestamp)?.toDate() ?: map["updatedAt"] as? Date
            )
        }
    }
}

enum class RepeatType {
    DAILY,
    WEEKLY,
    AS_NEEDED
}

/**
 * Urgency level for medicine refill alerts.
 * Used to prioritize and style alerts in the UI.
 */
enum class RefillUrgency {
    /** Stock is fine, no alert needed */
    NONE,
    /** Stock is low but some days remaining */
    LOW,
    /** 0-2 days of stock remaining */
    URGENT,
    /** No stock remaining */
    OUT_OF_STOCK
}
