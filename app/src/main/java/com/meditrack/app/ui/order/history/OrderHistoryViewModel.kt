package com.meditrack.app.ui.order.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.model.OrderStatus
import com.meditrack.app.data.model.RefillOrder
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * ViewModel for Order History screen.
 * Provides real-time lists of active and past orders.
 */
@HiltViewModel
class OrderHistoryViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {

    companion object {
        /** Statuses considered "active" (in progress) */
        private val ACTIVE_STATUSES = setOf(
            OrderStatus.PENDING,
            OrderStatus.CONFIRMED,
            OrderStatus.PREPARING,
            OrderStatus.READY,
            OrderStatus.SHIPPED
        )

        /** Statuses considered "past" (completed) */
        private val PAST_STATUSES = setOf(
            OrderStatus.DELIVERED,
            OrderStatus.CANCELLED
        )
    }

    // ─────────────── Loading State ───────────────

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ─────────────── Orders ───────────────

    /** All orders from Firestore (real-time) */
    private val allOrders: StateFlow<List<RefillOrder>> = orderRepository.getOrdersFlow()
        .map { resource ->
            when (resource) {
                is Resource.Loading -> {
                    _isLoading.value = true
                    emptyList()
                }
                is Resource.Success -> {
                    _isLoading.value = false
                    _errorMessage.value = null
                    resource.data
                }
                is Resource.Error -> {
                    _isLoading.value = false
                    _errorMessage.value = resource.message
                    emptyList()
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Active orders (PENDING, CONFIRMED, PREPARING, READY, SHIPPED) */
    val activeOrders: StateFlow<List<RefillOrder>> = allOrders
        .map { orders -> orders.filter { it.status in ACTIVE_STATUSES } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Past orders (DELIVERED, CANCELLED) */
    val pastOrders: StateFlow<List<RefillOrder>> = allOrders
        .map { orders -> orders.filter { it.status in PAST_STATUSES } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Whether there are any orders at all */
    val hasOrders: StateFlow<Boolean> = allOrders
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ─────────────── Actions ───────────────

    /**
     * Clear error message after it's been shown.
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
