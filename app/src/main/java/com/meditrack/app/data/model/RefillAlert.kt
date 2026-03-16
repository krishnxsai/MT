package com.meditrack.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a refill alert for a medicine that is running low or out of stock.
 * Stored in the `refillAlerts` Firestore collection.
 */
data class RefillAlert(
    @DocumentId
    val id: String = "",

    /** The patient who owns this alert */
    val userId: String = "",

    /** Reference to the medicine that triggered this alert */
    val medicineId: String = "",

    /** Denormalized medicine name for display */
    val medicineName: String = "",

    /** Denormalized medicine dosage for display */
    val medicineDosage: String = "",

    /** Current quantity when alert was created */
    val currentQuantity: Int = 0,

    /** Estimated days until stock depletes */
    val estimatedDaysLeft: Int = 0,

    /** Urgency level of this alert */
    val urgencyLevel: RefillUrgency = RefillUrgency.LOW,

    /** Whether the user has seen this alert */
    @get:PropertyName("isRead") @set:PropertyName("isRead")
    var isRead: Boolean = false,

    /** Whether the user has dismissed this alert */
    @get:PropertyName("isDismissed") @set:PropertyName("isDismissed")
    var isDismissed: Boolean = false,

    /** When the alert was created */
    @ServerTimestamp
    val createdAt: Date? = null,

    /** When the user took action (ordered refill) */
    val actionTakenAt: Date? = null,

    /** Link to the order placed for this alert */
    val orderId: String? = null
) {
    /** No-arg constructor for Firestore deserialization */
    constructor() : this(id = "")

    /**
     * Convert to a map for Firestore writes.
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "userId" to userId,
            "medicineId" to medicineId,
            "medicineName" to medicineName,
            "medicineDosage" to medicineDosage,
            "currentQuantity" to currentQuantity,
            "estimatedDaysLeft" to estimatedDaysLeft,
            "urgencyLevel" to urgencyLevel.name,
            "isRead" to isRead,
            "isDismissed" to isDismissed,
            "createdAt" to createdAt,
            "actionTakenAt" to actionTakenAt,
            "orderId" to orderId
        )
    }

    companion object {
        /**
         * Create a RefillAlert from a Firestore document map.
         */
        fun fromMap(id: String, map: Map<String, Any?>): RefillAlert {
            return RefillAlert(
                id = id,
                userId = map["userId"] as? String ?: "",
                medicineId = map["medicineId"] as? String ?: "",
                medicineName = map["medicineName"] as? String ?: "",
                medicineDosage = map["medicineDosage"] as? String ?: "",
                currentQuantity = (map["currentQuantity"] as? Number)?.toInt() ?: 0,
                estimatedDaysLeft = (map["estimatedDaysLeft"] as? Number)?.toInt() ?: 0,
                urgencyLevel = try {
                    RefillUrgency.valueOf(map["urgencyLevel"] as? String ?: "LOW")
                } catch (e: Exception) {
                    RefillUrgency.LOW
                },
                isRead = map["isRead"] as? Boolean ?: false,
                isDismissed = map["isDismissed"] as? Boolean ?: false,
                createdAt = (map["createdAt"] as? com.google.firebase.Timestamp)?.toDate(),
                actionTakenAt = (map["actionTakenAt"] as? com.google.firebase.Timestamp)?.toDate(),
                orderId = map["orderId"] as? String
            )
        }
    }
}
