package com.meditrack.app.data.model

/**
 * Represents a delivery time window for an order.
 * Users can select preferred delivery time slots.
 */
data class DeliveryWindow(
    val type: WindowType = WindowType.FLEXIBLE,
    val startTime: String = "06:00",  // HH:mm format
    val endTime: String = "22:00"     // HH:mm format
) {
    fun getDisplayName(): String = when (type) {
        WindowType.MORNING -> "Morning (6:00 AM - 12:00 PM)"
        WindowType.AFTERNOON -> "Afternoon (12:00 PM - 6:00 PM)"
        WindowType.EVENING -> "Evening (6:00 PM - 10:00 PM)"
        WindowType.FLEXIBLE -> "Flexible (Any time)"
    }

    fun getDeliveryMultiplier(): Double = when (type) {
        WindowType.MORNING -> 1.0      // Base rate
        WindowType.AFTERNOON -> 1.0    // Base rate
        WindowType.EVENING -> 1.5      // 50% premium for evening delivery
        WindowType.FLEXIBLE -> 0.8     // 20% discount for flexible
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "type" to type.name,
        "startTime" to startTime,
        "endTime" to endTime
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): DeliveryWindow = DeliveryWindow(
            type = try {
                WindowType.valueOf(map["type"] as? String ?: "FLEXIBLE")
            } catch (_: Exception) {
                WindowType.FLEXIBLE
            },
            startTime = map["startTime"] as? String ?: "06:00",
            endTime = map["endTime"] as? String ?: "22:00"
        )

        fun morning(): DeliveryWindow = DeliveryWindow(
            type = WindowType.MORNING,
            startTime = "06:00",
            endTime = "12:00"
        )

        fun afternoon(): DeliveryWindow = DeliveryWindow(
            type = WindowType.AFTERNOON,
            startTime = "12:00",
            endTime = "18:00"
        )

        fun evening(): DeliveryWindow = DeliveryWindow(
            type = WindowType.EVENING,
            startTime = "18:00",
            endTime = "22:00"
        )

        fun flexible(): DeliveryWindow = DeliveryWindow(
            type = WindowType.FLEXIBLE,
            startTime = "06:00",
            endTime = "22:00"
        )
    }
}

enum class WindowType {
    MORNING,     // 06:00 - 12:00 (1.0x, base rate)
    AFTERNOON,   // 12:00 - 18:00 (1.0x, base rate)
    EVENING,     // 18:00 - 22:00 (1.5x, premium for rush)
    FLEXIBLE     // 06:00 - 22:00 (0.8x, discount for flexibility)
}
