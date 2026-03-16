package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a medicine item in a pharmacy's inventory.
 * Stored as a subcollection: `pharmacies/{pharmacyId}/inventory/{itemId}`
 *
 * Schema:
 * - pharmacies/{pharmacyId}/inventory/{itemId}
 *   - medicineId: String (reference to global medicine catalog)
 *   - medicineName: String (denormalized for quick display)
 *   - genericName: String (for search matching)
 *   - quantity: Int (current stock count)
 *   - unitPrice: Double (pharmacy-specific pricing)
 *   - isAvailable: Boolean (quick flag for filtering)
 *   - lowStockThreshold: Int (when to alert pharmacy)
 *   - expiryDate: Date? (optional expiry tracking)
 *   - updatedAt: Timestamp
 */
data class PharmacyInventoryItem(
    @DocumentId
    val id: String = "",

    /** Reference to the global medicine catalog. */
    val medicineId: String = "",

    /** Denormalized medicine name for quick display. */
    val medicineName: String = "",

    /** Generic name for search matching (e.g., "paracetamol" for "Dolo 650"). */
    val genericName: String = "",

    /** Dosage information (e.g., "500mg", "10ml"). */
    val dosage: String = "",

    /** Current stock quantity. */
    val quantity: Int = 0,

    /** Pharmacy-specific price per unit. */
    val unitPrice: Double = 0.0,

    /** Quick availability flag (quantity > 0). */
    @get:PropertyName("isAvailable") @set:PropertyName("isAvailable")
    var isAvailable: Boolean = true,

    /** Threshold for low stock alerts. */
    val lowStockThreshold: Int = 10,

    /** Optional expiry date for batch tracking. */
    val expiryDate: Date? = null,

    /** Batch number for tracking. */
    val batchNumber: String = "",

    /** Whether this item requires prescription. */
    @get:PropertyName("prescriptionRequired") @set:PropertyName("prescriptionRequired")
    var prescriptionRequired: Boolean = false,

    @ServerTimestamp
    val updatedAt: Date? = null
) {
    constructor() : this(id = "")

    /** Check if stock is low based on threshold. */
    val isLowStock: Boolean
        get() = quantity <= lowStockThreshold && quantity > 0

    /** Check if item is out of stock. */
    val isOutOfStock: Boolean
        get() = quantity <= 0

    fun toMap(): Map<String, Any?> = mapOf(
        "medicineId" to medicineId,
        "medicineName" to medicineName,
        "genericName" to genericName,
        "dosage" to dosage,
        "quantity" to quantity,
        "unitPrice" to unitPrice,
        "isAvailable" to isAvailable,
        "lowStockThreshold" to lowStockThreshold,
        "expiryDate" to expiryDate,
        "batchNumber" to batchNumber,
        "prescriptionRequired" to prescriptionRequired
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): PharmacyInventoryItem = PharmacyInventoryItem(
            id = id,
            medicineId = map["medicineId"] as? String ?: "",
            medicineName = map["medicineName"] as? String ?: "",
            genericName = map["genericName"] as? String ?: "",
            dosage = map["dosage"] as? String ?: "",
            quantity = (map["quantity"] as? Number)?.toInt() ?: 0,
            unitPrice = (map["unitPrice"] as? Number)?.toDouble() ?: 0.0,
            isAvailable = map["isAvailable"] as? Boolean ?: true,
            lowStockThreshold = (map["lowStockThreshold"] as? Number)?.toInt() ?: 10,
            expiryDate = (map["expiryDate"] as? Timestamp)?.toDate(),
            batchNumber = map["batchNumber"] as? String ?: "",
            prescriptionRequired = map["prescriptionRequired"] as? Boolean ?: false,
            updatedAt = (map["updatedAt"] as? Timestamp)?.toDate()
        )
    }
}

/**
 * Result of a pharmacy inventory check for multiple medicines.
 * Used when matching pharmacies to patient order requirements.
 */
data class PharmacyInventoryMatch(
    val pharmacyId: String,
    val pharmacy: Pharmacy,

    /** Map of medicineId -> available quantity. */
    val availableItems: Map<String, PharmacyInventoryItem>,

    /** Medicine IDs that are NOT available at this pharmacy. */
    val unavailableItems: List<String>,

    /** Whether ALL requested medicines are available. */
    val hasAllItems: Boolean,

    /** Total price for all available items. */
    val estimatedTotal: Double
) {
    /** Percentage of requested items available (0.0 - 1.0). */
    val fulfillmentRatio: Float
        get() = if (availableItems.isEmpty() && unavailableItems.isEmpty()) 0f
                else availableItems.size.toFloat() / (availableItems.size + unavailableItems.size)
}
