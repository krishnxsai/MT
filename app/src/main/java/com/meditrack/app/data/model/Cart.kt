package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a shopping cart for medicine orders.
 * Stored in the `carts` Firestore collection.
 * Each user has one active cart at a time.
 */
data class Cart(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val items: List<CartItem> = emptyList(),
    val selectedPharmacyId: String? = null,

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null,

    /** Auto-clear cart after this time (24 hours from last update) */
    val expiresAt: Date? = null
) {
    constructor() : this(id = "")

    /** Check if cart has any items */
    val isEmpty: Boolean
        get() = items.isEmpty()

    /** Get total number of items */
    val itemCount: Int
        get() = items.size

    /** Get total quantity across all items */
    val totalQuantity: Int
        get() = items.sumOf { it.quantity }

    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "items" to items.map { it.toMap() },
        "selectedPharmacyId" to selectedPharmacyId,
        "createdAt" to createdAt,
        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
        "expiresAt" to expiresAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Cart {
            @Suppress("UNCHECKED_CAST")
            val itemsList = (map["items"] as? List<Map<String, Any?>>)?.map {
                CartItem.fromMap(it)
            } ?: emptyList()

            return Cart(
                id = id,
                userId = map["userId"] as? String ?: "",
                items = itemsList,
                selectedPharmacyId = map["selectedPharmacyId"] as? String,
                createdAt = (map["createdAt"] as? Timestamp)?.toDate(),
                updatedAt = (map["updatedAt"] as? Timestamp)?.toDate(),
                expiresAt = (map["expiresAt"] as? Timestamp)?.toDate()
            )
        }

        /** Create an empty cart for a user */
        fun empty(userId: String): Cart = Cart(
            userId = userId,
            items = emptyList()
        )
    }
}

/**
 * Represents a single item in the cart.
 */
data class CartItem(
    val medicineId: String = "",
    val medicineName: String = "",
    val medicineDosage: String = "",
    val medicineUnit: String = "",
    val quantity: Int = 1,

    /** True if this item was added from a low-stock alert */
    @get:PropertyName("fromLowStockAlert") @set:PropertyName("fromLowStockAlert")
    var fromLowStockAlert: Boolean = false,

    /** Reference to the alert that triggered this addition */
    val alertId: String? = null,

    /** Prescription ID if medicine requires prescription */
    val prescriptionId: String? = null,

    /** Whether this medicine requires a prescription */
    @get:PropertyName("prescriptionRequired") @set:PropertyName("prescriptionRequired")
    var prescriptionRequired: Boolean = false
) {
    constructor() : this(medicineId = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "medicineId" to medicineId,
        "medicineName" to medicineName,
        "medicineDosage" to medicineDosage,
        "medicineUnit" to medicineUnit,
        "quantity" to quantity,
        "fromLowStockAlert" to fromLowStockAlert,
        "alertId" to alertId,
        "prescriptionId" to prescriptionId,
        "prescriptionRequired" to prescriptionRequired
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): CartItem = CartItem(
            medicineId = map["medicineId"] as? String ?: "",
            medicineName = map["medicineName"] as? String ?: "",
            medicineDosage = map["medicineDosage"] as? String ?: "",
            medicineUnit = map["medicineUnit"] as? String ?: "",
            quantity = (map["quantity"] as? Number)?.toInt() ?: 1,
            fromLowStockAlert = map["fromLowStockAlert"] as? Boolean ?: false,
            alertId = map["alertId"] as? String,
            prescriptionId = map["prescriptionId"] as? String,
            prescriptionRequired = map["prescriptionRequired"] as? Boolean ?: false
        )

        /** Create a cart item from a Medicine object */
        fun fromMedicine(
            medicine: Medicine,
            quantity: Int = medicine.totalQuantity.takeIf { it > 0 } ?: 30,
            fromAlert: Boolean = false,
            alertId: String? = null
        ): CartItem = CartItem(
            medicineId = medicine.id,
            medicineName = medicine.name,
            medicineDosage = medicine.dosage,
            medicineUnit = medicine.unit,
            quantity = quantity,
            fromLowStockAlert = fromAlert,
            alertId = alertId,
            prescriptionId = medicine.prescriptionId.takeIf { it.isNotEmpty() },
            prescriptionRequired = medicine.prescribedByDoctor
        )
    }
}
