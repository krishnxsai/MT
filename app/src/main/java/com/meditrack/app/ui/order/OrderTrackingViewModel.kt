package com.meditrack.app.ui.order

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.model.DeliveryTracking
import com.meditrack.app.data.model.RefillOrder
import com.meditrack.app.data.model.getDisplayStatus
import com.meditrack.app.data.repository.DeliveryTrackingRepository
import com.meditrack.app.data.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel for the order tracking screen.
 * Manages real-time order status updates from Firestore and live delivery GPS tracking.
 */
@HiltViewModel
class OrderTrackingViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val deliveryTrackingRepository: DeliveryTrackingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        const val EXTRA_ORDER_ID = "order_id"
    }

    private val orderId = savedStateHandle.get<String>(EXTRA_ORDER_ID) ?: ""

    // ── Main order data with real-time updates ────────────────────────
    /**
     * Real-time order data from Firestore.
     * Automatically updates when changes are detected in the database.
     */
    val order: StateFlow<RefillOrder?> = orderRepository
        .getOrderFlow(orderId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ── Real-time delivery tracking GPS data ────────────────────────────
    /**
     * Real-time delivery person GPS tracking location.
     * Only populated when order status is OUT_FOR_DELIVERY (SHIPPED in our enum).
     * Updates every 3-5 seconds from Firestore deliveryTracking collection.
     */
    val deliveryTracking: StateFlow<DeliveryTracking?> = deliveryTrackingRepository
        .trackDeliveryFlow(orderId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ── Derived state: Display status label ────────────────────────────
    /**
     * User-friendly status label (e.g., "Accepted", "Out for Delivery").
     */
    val statusLabel: StateFlow<String> = order
        .map { it?.getDisplayStatus() ?: "Unknown" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Unknown")

    // ── Derived state: Formatted estimated delivery time ────────────────
    /**
     * Estimated delivery time as a formatted string.
     */
    val estimatedDeliveryTime: StateFlow<String> = order
        .map { formatDeliveryTime(it?.estimatedDelivery) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Unknown")

    // ── Derived state: Delivery address ───────────────────────────────
    /**
     * Formatted delivery address for display.
     */
    val deliveryAddressText: StateFlow<String> = order
        .map { order ->
            order?.deliveryAddress?.let { addr ->
                buildString {
                    if (addr.fullAddress.isNotEmpty()) append(addr.fullAddress)
                    if (addr.landmark.isNotEmpty()) {
                        if (isNotEmpty()) append(", ")
                        append("Near ${addr.landmark}")
                    }
                }
            } ?: "No address provided"
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Loading...")

    // ── Helper functions ──────────────────────────────────────────────

    private fun formatDeliveryTime(date: Date?): String {
        if (date == null) return "Calculating..."
        return try {
            val format = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            format.format(date)
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Get pharmacy phone number for calling.
     * Used when user clicks "Call Pharmacy" button.
     */
    suspend fun getPharmacyPhoneForOrder(orderId: String): com.meditrack.app.data.model.Resource<String?> {
        return orderRepository.getPharmacyPhoneForOrder(orderId)
    }
}
