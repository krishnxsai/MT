package com.example.meditrack.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a medicine refill order.
 * Stored in the top-level `orders` Firestore collection.
 *
 * Lifecycle: PENDING → CONFIRMED → SHIPPED → DELIVERED
 *                    └→ CANCELLED
 */
data class RefillOrder(
    @DocumentId
    val id: String = "",
    val userId: String = "",

    // ── Medicine reference ───────────────────────────────────────
    val medicineId: String = "",
    val medicineName: String = "",
    val medicineDosage: String = "",
    val medicineUnit: String = "",

    // ── Order details ────────────────────────────────────────────
    val quantity: Int = 0,
    val status: OrderStatus = OrderStatus.PENDING,
    val notes: String = "",

    /** Optional prescription reference for controlled medications. */
    val prescriptionId: String = "",

    // ── Pharmacy info (future extension) ─────────────────────────
    val pharmacyName: String = "",
    val pharmacyId: String = "",

    // ── Status change tracking ───────────────────────────────────
    val statusHistory: List<OrderStatusEntry> = emptyList(),
    val cancelReason: String = "",

    // ── Timestamps ───────────────────────────────────────────────
    val estimatedDelivery: Date? = null,
    val deliveredAt: Date? = null,

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    constructor() : this(id = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "medicineId" to medicineId,
        "medicineName" to medicineName,
        "medicineDosage" to medicineDosage,
        "medicineUnit" to medicineUnit,
        "quantity" to quantity,
        "status" to status.name,
        "notes" to notes,
        "prescriptionId" to prescriptionId,
        "pharmacyName" to pharmacyName,
        "pharmacyId" to pharmacyId,
        "statusHistory" to statusHistory.map { it.toMap() },
        "cancelReason" to cancelReason,
        "estimatedDelivery" to estimatedDelivery,
        "deliveredAt" to deliveredAt,
        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): RefillOrder {
            @Suppress("UNCHECKED_CAST")
            val historyList = (map["statusHistory"] as? List<Map<String, Any?>>)?.map {
                OrderStatusEntry.fromMap(it)
            } ?: emptyList()

            return RefillOrder(
                id = id,
                userId = map["userId"] as? String ?: "",
                medicineId = map["medicineId"] as? String ?: "",
                medicineName = map["medicineName"] as? String ?: "",
                medicineDosage = map["medicineDosage"] as? String ?: "",
                medicineUnit = map["medicineUnit"] as? String ?: "",
                quantity = (map["quantity"] as? Number)?.toInt() ?: 0,
                status = try {
                    OrderStatus.valueOf(map["status"] as? String ?: "PENDING")
                } catch (_: Exception) { OrderStatus.PENDING },
                notes = map["notes"] as? String ?: "",
                prescriptionId = map["prescriptionId"] as? String ?: "",
                pharmacyName = map["pharmacyName"] as? String ?: "",
                pharmacyId = map["pharmacyId"] as? String ?: "",
                statusHistory = historyList,
                cancelReason = map["cancelReason"] as? String ?: "",
                estimatedDelivery = (map["estimatedDelivery"] as? Timestamp)?.toDate(),
                deliveredAt = (map["deliveredAt"] as? Timestamp)?.toDate(),
                createdAt = (map["createdAt"] as? Timestamp)?.toDate(),
                updatedAt = (map["updatedAt"] as? Timestamp)?.toDate()
            )
        }
    }
}

enum class OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    fun displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Single entry in the order status change trail.
 */
data class OrderStatusEntry(
    val status: String = "",
    val changedAt: Date? = null,
    val note: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "status" to status,
        "changedAt" to changedAt,
        "note" to note
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): OrderStatusEntry = OrderStatusEntry(
            status = map["status"] as? String ?: "",
            changedAt = (map["changedAt"] as? Timestamp)?.toDate(),
            note = map["note"] as? String ?: ""
        )
    }
}

