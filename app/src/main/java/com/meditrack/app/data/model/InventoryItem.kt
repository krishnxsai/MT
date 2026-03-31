package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import java.util.Locale

/**
 * Represents a medicine item in a pharmacy's inventory.
 * Stored in the `pharmacyInventory` Firestore collection.
 */
data class InventoryItem(
    @DocumentId
    val id: String = "",
    val pharmacyId: String = "",

    // ── Medicine info ────────────────────────────────────────────
    val medicineName: String = "",
    val medicineNameNormalized: String = "",
    val genericName: String = "",
    val category: String = "",
    val manufacturer: String = "",
    val batchNumber: String = "",

    // ── Stock & Pricing ─────────────────────────────────────────
    val stockQuantity: Int = 0,
    val unitPrice: Double = 0.0,
    val unit: String = "tablets",
    val lowStockThreshold: Int = 10,

    // ── Dates ───────────────────────────────────────────────────
    val expiryDate: Date? = null,

    val isActive: Boolean = true,

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    constructor() : this(id = "")

    val isLowStock: Boolean get() = stockQuantity <= lowStockThreshold
    val isExpired: Boolean get() = expiryDate != null && expiryDate.before(Date())

    fun toMap(): Map<String, Any?> {
        val normalizedName = normalizeMedicineName(
            if (medicineNameNormalized.isNotBlank()) medicineNameNormalized else medicineName
        )

        return mapOf(
            "pharmacyId" to pharmacyId,
            "medicineName" to medicineName,
            "medicineNameNormalized" to normalizedName,
            "genericName" to genericName,
            "category" to category,
            "manufacturer" to manufacturer,
            "batchNumber" to batchNumber,
            "stockQuantity" to stockQuantity,
            "unitPrice" to unitPrice,
            "unit" to unit,
            "lowStockThreshold" to lowStockThreshold,
            "expiryDate" to expiryDate,
            "isActive" to isActive,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
    }

    companion object {
        private fun normalizeMedicineName(value: String): String {
            return value.trim().lowercase(Locale.ROOT)
        }

        fun fromMap(id: String, map: Map<String, Any?>): InventoryItem = InventoryItem(
            id = id,
            pharmacyId = map["pharmacyId"] as? String ?: "",
            medicineName = map["medicineName"] as? String ?: "",
            medicineNameNormalized =
                (map["medicineNameNormalized"] as? String)?.takeIf { it.isNotBlank() }
                    ?: normalizeMedicineName(map["medicineName"] as? String ?: ""),
            genericName = map["genericName"] as? String ?: "",
            category = map["category"] as? String ?: "",
            manufacturer = map["manufacturer"] as? String ?: "",
            batchNumber = map["batchNumber"] as? String ?: "",
            stockQuantity = (map["stockQuantity"] as? Number)?.toInt() ?: 0,
            unitPrice = (map["unitPrice"] as? Number)?.toDouble() ?: 0.0,
            unit = map["unit"] as? String ?: "tablets",
            lowStockThreshold = (map["lowStockThreshold"] as? Number)?.toInt() ?: 10,
            expiryDate = (map["expiryDate"] as? Timestamp)?.toDate(),
            isActive = map["isActive"] as? Boolean ?: true,
            createdAt = (map["createdAt"] as? Timestamp)?.toDate(),
            updatedAt = (map["updatedAt"] as? Timestamp)?.toDate()
        )
    }
}

