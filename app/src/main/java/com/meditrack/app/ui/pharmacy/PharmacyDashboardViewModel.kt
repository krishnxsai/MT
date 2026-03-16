package com.meditrack.app.ui.pharmacy

import android.app.Application
import androidx.lifecycle.*
import com.meditrack.app.data.model.InventoryItem
import com.meditrack.app.data.model.OrderStatus
import com.meditrack.app.data.model.Pharmacy
import com.meditrack.app.data.model.RefillOrder
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.AuthRepository
import com.meditrack.app.data.repository.OrderRepository
import com.meditrack.app.data.repository.PharmacyInventoryRepository
import com.meditrack.app.data.repository.PharmacyRepository
import com.meditrack.app.util.PharmacyNotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class PharmacyDashboardViewModel @Inject constructor(
    application: Application,
    private val pharmacyRepository: PharmacyRepository,
    private val inventoryRepository: PharmacyInventoryRepository,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {

    private val _pharmacy = MutableLiveData<Resource<Pharmacy?>>()
    val pharmacy: LiveData<Resource<Pharmacy?>> = _pharmacy

    private val _orders = MutableLiveData<Resource<List<RefillOrder>>>()
    val orders: LiveData<Resource<List<RefillOrder>>> = _orders

    private val _orderCounts = MutableLiveData<Map<String, Int>>(
        mapOf(
            "total" to 0, "pending" to 0, "confirmed" to 0,
            "preparing" to 0, "ready" to 0, "delivered" to 0,
            "deliveredToday" to 0, "cancelled" to 0, "completed" to 0
        )
    )
    val orderCounts: LiveData<Map<String, Int>> = _orderCounts

    // ── Analytics ─────────────────────────────────────────────
    private val _revenueStats = MutableLiveData<Map<String, Double>>(
        mapOf("totalRevenue" to 0.0, "todayRevenue" to 0.0, "totalTransactions" to 0.0, "refundTotal" to 0.0)
    )
    val revenueStats: LiveData<Map<String, Double>> = _revenueStats

    private val _dailyRevenue = MutableLiveData<Double>()
    val dailyRevenue: LiveData<Double> = _dailyRevenue

    private val _topMedicines = MutableLiveData<List<Pair<String, Int>>>()
    val topMedicines: LiveData<List<Pair<String, Int>>> = _topMedicines

    // ── Inventory Alerts ──────────────────────────────────────
    private val _lowStockItems = MutableLiveData<List<InventoryItem>>(emptyList())
    val lowStockItems: LiveData<List<InventoryItem>> = _lowStockItems

    // ── Patient Names Cache ───────────────────────────────────
    private val _patientNames = MutableLiveData<Map<String, String>>(emptyMap())
    val patientNames: LiveData<Map<String, String>> = _patientNames

    // ── Status-update feedback ──────────────────────────────────
    private val _updateError = MutableLiveData<String?>()
    val updateError: LiveData<String?> = _updateError

    // ── Internal state ──────────────────────────────────────
    private var ordersJob: Job? = null
    private var lowStockJob: Job? = null
    private var previousOrderIds: Set<String> = emptySet()
    private val patientNameCache = mutableMapOf<String, String>()
    private var pharmacyLoaded = false

    /** Load the pharmacy document owned by the current user. */
    fun loadPharmacyProfile(forceRefresh: Boolean = false) {
        if (pharmacyLoaded && !forceRefresh) return
        _pharmacy.value = Resource.Loading
        viewModelScope.launch {
            when (val result = pharmacyRepository.getMyPharmacy()) {
                is Resource.Success -> {
                    val pharmacy = result.data
                    _pharmacy.value = Resource.Success(pharmacy)
                    pharmacyLoaded = true

                    pharmacy?.let {
                        startOrdersListener(it.id)
                        startLowStockListener(it.id)
                        loadAnalytics(it.id)
                    }
                }
                is Resource.Error -> {
                    _pharmacy.value = Resource.Error(result.message)
                    pharmacyLoaded = false
                }
                is Resource.Loading -> {}
            }
        }
    }

    /** Start real-time listener for orders via repository Flow. */
    private fun startOrdersListener(pharmacyId: String) {
        ordersJob?.cancel()
        _orders.value = Resource.Loading

        ordersJob = viewModelScope.launch {
            pharmacyRepository.getPharmacyOrdersFlow(pharmacyId).collectLatest { result ->
                when (result) {
                    is Resource.Success -> {
                        val orders = result.data
                        _orders.value = Resource.Success(orders)
                        computeOrderCounts(orders)

                        // Detect new orders for notifications
                        val currentOrderIds = orders.map { it.id }.toSet()
                        val newOrderIds = currentOrderIds - previousOrderIds
                        if (previousOrderIds.isNotEmpty() && newOrderIds.isNotEmpty()) {
                            val newOrders = orders.filter { it.id in newOrderIds && it.status == OrderStatus.PENDING }
                            for (order in newOrders) {
                                PharmacyNotificationHelper.notifyNewOrder(
                                    getApplication(),
                                    order.medicineName,
                                    order.quantity
                                )
                            }
                        }
                        previousOrderIds = currentOrderIds

                        computeTopMedicines(orders)
                        loadPatientNames(orders)
                    }
                    is Resource.Error -> {
                        _orders.value = Resource.Error(result.message)
                    }
                    is Resource.Loading -> {
                        _orders.value = Resource.Loading
                    }
                }
            }
        }
    }

    /** Start real-time listener for low-stock inventory items. */
    private fun startLowStockListener(pharmacyId: String) {
        lowStockJob?.cancel()
        lowStockJob = viewModelScope.launch {
            inventoryRepository.getLowStockItemsFlow(pharmacyId).collectLatest { result ->
                when (result) {
                    is Resource.Success -> _lowStockItems.value = result.data
                    else -> _lowStockItems.value = emptyList()
                }
            }
        }
    }

    /** Compute expanded order counts from order list. */
    private fun computeOrderCounts(orders: List<RefillOrder>) {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time

        val counts = mapOf(
            "total" to orders.size,
            "pending" to orders.count { it.status == OrderStatus.PENDING },
            "confirmed" to orders.count { it.status == OrderStatus.CONFIRMED },
            "preparing" to orders.count { it.status == OrderStatus.PREPARING },
            "ready" to orders.count { it.status == OrderStatus.SHIPPED },
            "delivered" to orders.count { it.status == OrderStatus.DELIVERED },
            "deliveredToday" to orders.count {
                it.status == OrderStatus.DELIVERED &&
                        it.deliveredAt != null && it.deliveredAt.after(todayStart)
            },
            "cancelled" to orders.count { it.status == OrderStatus.CANCELLED },
            "completed" to orders.count {
                it.status == OrderStatus.DELIVERED || it.status == OrderStatus.CONFIRMED
            }
        )
        _orderCounts.value = counts
    }

    /** Load patient display names for orders using batched query. */
    private fun loadPatientNames(orders: List<RefillOrder>) {
        val unknownIds = orders.map { it.userId }.distinct().filter { it !in patientNameCache }
        if (unknownIds.isEmpty()) return

        viewModelScope.launch {
            when (val result = authRepository.getUserDisplayNames(unknownIds)) {
                is Resource.Success -> {
                    patientNameCache.putAll(result.data)
                    _patientNames.value = patientNameCache.toMap()
                }
                else -> { /* silently ignore — names are a best-effort cache */ }
            }
        }
    }

    /** Load analytics data. */
    private fun loadAnalytics(pharmacyId: String) {
        viewModelScope.launch {
            when (val result = pharmacyRepository.getRevenueStats(pharmacyId)) {
                is Resource.Success -> {
                    _revenueStats.value = result.data
                    _dailyRevenue.value = result.data["todayRevenue"] ?: 0.0
                }
                else -> {
                    _dailyRevenue.value = 0.0
                }
            }
        }
    }

    /** Compute top ordered medicines from order list. */
    private fun computeTopMedicines(orders: List<RefillOrder>) {
        val medicineCount = orders
            .groupBy { it.medicineName }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
        _topMedicines.value = medicineCount
    }

    /** Update order status via repository. */
    fun updateOrderStatus(orderId: String, newStatus: String) {
        viewModelScope.launch {
            when (val result = orderRepository.updateOrderStatus(orderId, newStatus)) {
                is Resource.Success -> {
                    _updateError.postValue(null)
                    // Handle refund for cancellations
                    if (newStatus == OrderStatus.CANCELLED.name) {
                        when (val orderResult = orderRepository.getOrder(orderId)) {
                            is Resource.Success -> {
                                if (orderResult.data.status == OrderStatus.CANCELLED) {
                                    pharmacyRepository.logRefundTransaction(orderResult.data)
                                }
                            }
                            else -> { /* order already updated, refund is best-effort */ }
                        }
                    }
                }
                is Resource.Error -> {
                    _updateError.postValue(result.message)
                }
                is Resource.Loading -> {}
            }
        }
    }

    /** Get the current pharmacy ID. */
    fun getPharmacyId(): String? {
        return (_pharmacy.value as? Resource.Success)?.data?.id
    }

    /** Remove listeners – call before sign-out to avoid PERMISSION_DENIED. */
    fun cleanup() {
        ordersJob?.cancel()
        ordersJob = null
        lowStockJob?.cancel()
        lowStockJob = null
        pharmacyLoaded = false
    }

    /** Sign out the current user via AuthRepository. */
    fun signOut() {
        cleanup()
        authRepository.signOutSync()
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
