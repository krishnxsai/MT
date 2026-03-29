package com.meditrack.app.data.model

/**
 * Represents the pricing breakdown for an order.
 * Includes items, delivery fee, taxes, and total amount.
 */
data class PricingBreakdown(
    val itemBreakdown: List<ItemPrice> = emptyList(),
    val itemsSubtotal: Double = 0.0,           // Sum of (item price * quantity)
    val deliveryFee: Double = 0.0,             // Based on distance and time window
    val discount: Double = 0.0,                // Promotional discount (if any)
    val gst: Double = 0.0,                     // GST 18% on subtotal + delivery
    val totalAmount: Double = 0.0,             // itemsSubtotal + deliveryFee - discount + gst
    val currency: String = "INR",
    val estimatedDeliveryMinutes: Int = 0      // ETA in minutes
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "itemBreakdown" to itemBreakdown.map { it.toMap() },
        "itemsSubtotal" to itemsSubtotal,
        "deliveryFee" to deliveryFee,
        "discount" to discount,
        "gst" to gst,
        "totalAmount" to totalAmount,
        "currency" to currency,
        "estimatedDeliveryMinutes" to estimatedDeliveryMinutes
    )
}

/**
 * Individual item in the pricing breakdown.
 */
data class ItemPrice(
    val medicineId: String = "",
    val medicineName: String = "",
    val quantity: Int = 0,
    val unitPrice: Double = 0.0,              // Price per unit
    val totalPrice: Double = 0.0              // unitPrice * quantity
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "medicineId" to medicineId,
        "medicineName" to medicineName,
        "quantity" to quantity,
        "unitPrice" to unitPrice,
        "totalPrice" to totalPrice
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): ItemPrice = ItemPrice(
            medicineId = map["medicineId"] as? String ?: "",
            medicineName = map["medicineName"] as? String ?: "",
            quantity = (map["quantity"] as? Number)?.toInt() ?: 0,
            unitPrice = (map["unitPrice"] as? Number)?.toDouble() ?: 0.0,
            totalPrice = (map["totalPrice"] as? Number)?.toDouble() ?: 0.0
        )
    }
}
