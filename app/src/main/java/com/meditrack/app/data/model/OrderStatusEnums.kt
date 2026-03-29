package com.meditrack.app.data.model

import com.meditrack.app.R

/**
 * Order lifecycle status.
 * Follows state machine: PENDING → CONFIRMED → PREPARING → READY → SHIPPED → DELIVERED
 * Can transition to CANCELLED at any point.
 */
enum class OrderStatus {
    PENDING,      // Order placed, awaiting pharmacy confirmation
    CONFIRMED,    // Pharmacy confirmed order, payment received
    PREPARING,    // Pharmacy preparing order (picking, packaging)
    READY,        // Order ready for pickup/delivery
    SHIPPED,      // Out for delivery
    DELIVERED,    // Delivered to customer
    CANCELLED,    // Order cancelled
    RETURNED;     // Order returned

    fun displayName(): String = name.lowercase().replace("_", " ")

    fun getDisplayLabel(): String = when (this) {
        PENDING -> "Waiting for confirmation"
        CONFIRMED -> "Order confirmed"
        PREPARING -> "Preparing your order"
        READY -> "Ready for pickup"
        SHIPPED -> "Out for delivery"
        DELIVERED -> "Delivered"
        CANCELLED -> "Cancelled"
        RETURNED -> "Returned"
    }

    fun getStatusColor(): Int = when (this) {
        PENDING -> R.color.warning                    // Orange
        CONFIRMED -> R.color.success                  // Green
        PREPARING -> R.color.info                     // Blue
        READY -> R.color.info                         // Blue
        SHIPPED -> R.color.warning                    // Orange
        DELIVERED -> R.color.success                  // Green
        CANCELLED -> R.color.error                    // Red
        RETURNED -> R.color.tertiary                  // Purple
    }

    fun getStatusIcon(): Int = when (this) {
        PENDING -> android.R.drawable.ic_menu_recent_history
        CONFIRMED -> android.R.drawable.ic_menu_view
        PREPARING -> android.R.drawable.ic_menu_edit
        READY -> android.R.drawable.ic_menu_save
        SHIPPED -> android.R.drawable.ic_menu_send
        DELIVERED -> android.R.drawable.ic_menu_info_details
        CANCELLED -> android.R.drawable.ic_menu_close_clear_cancel
        RETURNED -> android.R.drawable.ic_menu_revert
    }

    fun isTerminal(): Boolean = this in listOf(DELIVERED, CANCELLED, RETURNED)
    fun isPending(): Boolean = this in listOf(PENDING, CONFIRMED)
    fun isInProgress(): Boolean = this in listOf(PREPARING, READY, SHIPPED)
}

/**
 * Delivery type for orders.
 */
enum class DeliveryType {
    DELIVERY,  // Delivery to address
    PICKUP;    // Pickup from pharmacy

    fun displayName(): String = when (this) {
        DELIVERY -> "Delivery"
        PICKUP -> "Pickup"
    }
}
