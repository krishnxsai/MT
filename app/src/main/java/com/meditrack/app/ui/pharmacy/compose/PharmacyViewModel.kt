package com.meditrack.app.ui.pharmacy.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.model.InventoryItem
import com.meditrack.app.data.model.OrderStatus as DataOrderStatus
import com.meditrack.app.data.model.RefillOrder
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.AuthRepository
import com.meditrack.app.data.repository.OrderRepository
import com.meditrack.app.data.repository.PharmacyInventoryRepository
import com.meditrack.app.data.repository.PharmacyRepository
import com.meditrack.app.data.repository.UserProfileSummary
import com.meditrack.app.domain.usecase.AcceptOrderResult
import com.meditrack.app.domain.usecase.VerifyAndAcceptOrderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class PharmacyViewModel @Inject constructor(
    private val pharmacyRepository: PharmacyRepository,
    private val inventoryRepository: PharmacyInventoryRepository,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val verifyAndAcceptOrderUseCase: VerifyAndAcceptOrderUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PharmacyDashboardUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PharmacyDashboardEvent>()
    val events = _events.asSharedFlow()

    private var pharmacyId: String? = null
    private var pharmacyName: String = "Pharmacy Dashboard"

    private var isPharmacyLoading = false
    private var isOrdersLoading = false
    private var hasLoadedPharmacy = false

    private var rawOrders: List<RefillOrder> = emptyList()
    private var lowStockItems: List<InventoryItem> = emptyList()
    private var revenueStats: Map<String, Double> = emptyMap()

    private val patientProfiles = mutableMapOf<String, UserProfileSummary>()
    private val inFlightProfileIds = mutableSetOf<String>()

    private var ordersJob: Job? = null
    private var lowStockJob: Job? = null

    init {
        loadDashboard(forceRefresh = false)
    }

    fun refresh() {
        loadDashboard(forceRefresh = true)
    }

    fun onTabSelected(tab: DashboardTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        rebuildUiState()
    }

    fun onToggleOrderExpanded(orderId: String) {
        _uiState.update { state ->
            val updatedExpanded = state.expandedOrderIds.toMutableSet().apply {
                if (contains(orderId)) remove(orderId) else add(orderId)
            }
            state.copy(expandedOrderIds = updatedExpanded)
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun advanceOrderStatus(orderId: String) {
        val order = rawOrders.firstOrNull { it.id == orderId } ?: return

        viewModelScope.launch {
            val result = when (order.status) {
                DataOrderStatus.PENDING -> confirmPendingOrder(order)
                DataOrderStatus.CONFIRMED -> orderRepository.updateOrderStatus(
                    orderId = order.id,
                    newStatus = DataOrderStatus.PREPARING,
                    note = "Order preparation started"
                )
                DataOrderStatus.PREPARING -> orderRepository.updateOrderStatus(
                    orderId = order.id,
                    newStatus = DataOrderStatus.READY,
                    note = "Order ready for handoff"
                )
                DataOrderStatus.READY -> orderRepository.updateOrderStatus(
                    orderId = order.id,
                    newStatus = DataOrderStatus.SHIPPED,
                    note = "Order out for delivery"
                )
                DataOrderStatus.SHIPPED -> orderRepository.updateOrderStatus(
                    orderId = order.id,
                    newStatus = DataOrderStatus.DELIVERED,
                    note = "Order delivered"
                )
                DataOrderStatus.DELIVERED,
                DataOrderStatus.CANCELLED,
                DataOrderStatus.RETURNED -> Resource.Error("No further action available for this order")
            }

            when (result) {
                is Resource.Success -> {
                    emitMessage("Order updated")
                    pharmacyId?.let { loadRevenueStats(it) }
                }
                is Resource.Error -> {
                    emitMessage(result.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun rejectOrder(orderId: String) {
        viewModelScope.launch {
            val result = orderRepository.updateOrderStatus(
                orderId = orderId,
                newStatus = DataOrderStatus.CANCELLED,
                note = "Rejected by pharmacy"
            )
            when (result) {
                is Resource.Success -> emitMessage("Order rejected")
                is Resource.Error -> emitMessage(result.message)
                is Resource.Loading -> Unit
            }
        }
    }

    fun signOut() {
        authRepository.signOutSync()
    }

    private fun loadDashboard(forceRefresh: Boolean) {
        if (isPharmacyLoading) return
        if (hasLoadedPharmacy && !forceRefresh) return

        isPharmacyLoading = true
        if (forceRefresh) {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        }
        rebuildUiState()

        viewModelScope.launch {
            when (val result = pharmacyRepository.getMyPharmacy()) {
                is Resource.Success -> {
                    isPharmacyLoading = false
                    hasLoadedPharmacy = true

                    val pharmacy = result.data
                    if (pharmacy == null) {
                        pharmacyId = null
                        rawOrders = emptyList()
                        lowStockItems = emptyList()
                        rebuildUiState(
                            errorMessage = "Complete pharmacy profile setup to view your dashboard"
                        )
                        _uiState.update { it.copy(isRefreshing = false) }
                        return@launch
                    }

                    val previousPharmacyId = pharmacyId
                    pharmacyId = pharmacy.id
                    pharmacyName = pharmacy.name.ifBlank { "Pharmacy Dashboard" }

                    if (previousPharmacyId != pharmacy.id || ordersJob == null) {
                        startOrdersListener(pharmacy.id)
                    }
                    if (previousPharmacyId != pharmacy.id || lowStockJob == null) {
                        startLowStockListener(pharmacy.id)
                    }

                    loadRevenueStats(pharmacy.id)
                    rebuildUiState(errorMessage = null)
                    _uiState.update { it.copy(isRefreshing = false) }
                }
                is Resource.Error -> {
                    isPharmacyLoading = false
                    hasLoadedPharmacy = true
                    rebuildUiState(errorMessage = result.message)
                    _uiState.update { it.copy(isRefreshing = false) }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun startOrdersListener(pharmacyId: String) {
        ordersJob?.cancel()
        isOrdersLoading = true
        rebuildUiState()

        ordersJob = viewModelScope.launch {
            pharmacyRepository.getPharmacyOrdersFlow(pharmacyId).collectLatest { result ->
                when (result) {
                    is Resource.Loading -> {
                        isOrdersLoading = true
                        rebuildUiState()
                    }
                    is Resource.Success -> {
                        isOrdersLoading = false
                        rawOrders = result.data
                            .sortedByDescending { it.createdAt?.time ?: it.updatedAt?.time ?: 0L }

                        loadMissingPatientProfiles(rawOrders)
                        rebuildUiState(errorMessage = null)
                    }
                    is Resource.Error -> {
                        isOrdersLoading = false
                        rebuildUiState(errorMessage = result.message)
                        emitMessage(result.message)
                    }
                }
            }
        }
    }

    private fun startLowStockListener(pharmacyId: String) {
        lowStockJob?.cancel()
        lowStockJob = viewModelScope.launch {
            inventoryRepository.getLowStockItemsFlow(pharmacyId).collectLatest { result ->
                lowStockItems = when (result) {
                    is Resource.Success -> result.data
                    else -> emptyList()
                }
                rebuildUiState()
            }
        }
    }

    private fun loadRevenueStats(pharmacyId: String) {
        viewModelScope.launch {
            when (val result = pharmacyRepository.getRevenueStats(pharmacyId)) {
                is Resource.Success -> {
                    revenueStats = result.data
                    rebuildUiState()
                }
                is Resource.Error -> {
                    // Keep fallback revenue computed from delivered orders.
                    rebuildUiState()
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun loadMissingPatientProfiles(orders: List<RefillOrder>) {
        val missing = orders
            .mapNotNull { it.userId.takeIf(String::isNotBlank) }
            .distinct()
            .filter { it !in patientProfiles && it !in inFlightProfileIds }

        if (missing.isEmpty()) return

        inFlightProfileIds.addAll(missing)
        viewModelScope.launch {
            when (val result = authRepository.getUserProfileSummaries(missing)) {
                is Resource.Success -> {
                    patientProfiles.putAll(result.data)
                    rebuildUiState()
                }
                is Resource.Error -> {
                    // Patient metadata is best-effort; keep rendering generic patient labels.
                }
                is Resource.Loading -> Unit
            }
            inFlightProfileIds.removeAll(missing.toSet())
        }
    }

    private suspend fun confirmPendingOrder(order: RefillOrder): Resource<Unit> {
        when (val stockCheck = ensureSufficientStock(order)) {
            is Resource.Error -> return stockCheck
            is Resource.Loading,
            is Resource.Success -> Unit
        }

        if (order.prescriptionId.isBlank()) {
            return orderRepository.updateOrderStatus(
                orderId = order.id,
                newStatus = DataOrderStatus.CONFIRMED,
                note = "Order confirmed"
            )
        }

        return when (val result = verifyAndAcceptOrderUseCase.execute(order)) {
            is AcceptOrderResult.Accepted -> Resource.Success(Unit)
            is AcceptOrderResult.StatusAdvanced -> Resource.Success(Unit)
            is AcceptOrderResult.PrescriptionInvalid -> Resource.Error("Prescription invalid: ${result.reason}")
            is AcceptOrderResult.Error -> Resource.Error(result.message)
        }
    }

    private suspend fun ensureSufficientStock(order: RefillOrder): Resource<Unit> {
        val resolvedPharmacyId = order.pharmacyId.takeIf { it.isNotBlank() }
            ?: pharmacyId
            ?: return Resource.Success(Unit)

        val requestedQuantities = linkedMapOf<String, Int>()

        if (order.items.isNotEmpty()) {
            order.items.forEach { item ->
                val medicineName = item.medicineName.trim()
                if (medicineName.isBlank()) return@forEach
                requestedQuantities[medicineName] =
                    (requestedQuantities[medicineName] ?: 0) + item.quantity.coerceAtLeast(1)
            }
        } else {
            val medicineName = order.medicineName.trim()
            if (medicineName.isNotBlank()) {
                requestedQuantities[medicineName] = order.quantity.coerceAtLeast(1)
            }
        }

        if (requestedQuantities.isEmpty()) {
            return Resource.Success(Unit)
        }

        return when (
            val result = inventoryRepository.getMedicinesWithSufficientStock(
                pharmacyId = resolvedPharmacyId,
                medicineNames = requestedQuantities.keys.toList(),
                quantities = requestedQuantities
            )
        ) {
            is Resource.Success -> {
                val unavailable = requestedQuantities.keys.filter { key -> key !in result.data.keys }
                if (unavailable.isEmpty()) {
                    Resource.Success(Unit)
                } else {
                    Resource.Error("Insufficient stock for: ${unavailable.joinToString()}")
                }
            }
            is Resource.Error -> Resource.Error(result.message)
            is Resource.Loading -> Resource.Success(Unit)
        }
    }

    private fun rebuildUiState(errorMessage: String? = _uiState.value.errorMessage) {
        val currentState = _uiState.value
        val query = currentState.searchQuery.trim().lowercase(Locale.ROOT)

        val mappedOrders = rawOrders
            .mapNotNull { mapOrderToUi(it) }
            .sortedByDescending { it.timestamp }

        val activeOrders = mappedOrders.filter { it.status != OrderStatus.DELIVERED }
        val deliveredOrders = mappedOrders.filter { it.status == OrderStatus.DELIVERED }

        val filteredActiveOrders = if (query.isBlank()) {
            activeOrders
        } else {
            activeOrders.filter { it.matchesQuery(query) }
        }

        _uiState.update {
            it.copy(
                isLoading = isPharmacyLoading || (isOrdersLoading && rawOrders.isEmpty()),
                errorMessage = errorMessage,
                pharmacyId = pharmacyId.orEmpty(),
                pharmacyName = pharmacyName,
                activeOrders = filteredActiveOrders,
                deliveredOrders = deliveredOrders,
                pendingCount = activeOrders.count { order -> order.status == OrderStatus.PENDING },
                preparingCount = activeOrders.count { order ->
                    order.status == OrderStatus.CONFIRMED || order.status == OrderStatus.PREPARING
                },
                readyCount = activeOrders.count { order ->
                    order.status == OrderStatus.READY || order.status == OrderStatus.OUT_FOR_DELIVERY
                },
                deliveredCount = deliveredOrders.size,
                notificationCount = activeOrders.count { order -> order.status == OrderStatus.PENDING },
                analytics = computeAnalytics(rawOrders),
                forecast = computeForecast(rawOrders),
                inventorySummary = lowStockItems
                    .sortedBy { item -> item.stockQuantity }
                    .map { item ->
                        InventorySummary(
                            medicineName = item.medicineName,
                            stockQuantity = item.stockQuantity,
                            lowStockThreshold = item.lowStockThreshold
                        )
                    }
            )
        }
    }

    private fun mapOrderToUi(order: RefillOrder): Order? {
        val mappedStatus = mapStatus(order.status) ?: return null

        val profile = patientProfiles[order.userId]
        val patient = Patient(
            id = order.userId,
            name = profile?.displayName?.takeIf { it.isNotBlank() } ?: "Patient",
            phone = profile?.phoneNumber?.takeIf { it.isNotBlank() }
                ?: order.deliveryAddress?.contactPhone.orEmpty()
        )

        val items = if (order.items.isNotEmpty()) {
            order.items.map { item ->
                val linePrice = when {
                    item.totalPrice > 0.0 -> item.totalPrice
                    item.unitPrice > 0.0 -> item.unitPrice * item.quantity.coerceAtLeast(1)
                    else -> 0.0
                }
                OrderItem(
                    medicineName = item.medicineName.ifBlank { "Medicine" },
                    dosage = item.medicineDosage,
                    quantity = item.quantity.coerceAtLeast(1),
                    price = linePrice
                )
            }
        } else {
            val quantity = order.quantity.coerceAtLeast(1)
            val linePrice = when {
                order.totalAmount > 0.0 -> order.totalAmount
                order.subtotal > 0.0 -> order.subtotal
                else -> 0.0
            }
            listOf(
                OrderItem(
                    medicineName = order.medicineName.ifBlank { "Medicine" },
                    dosage = order.medicineDosage,
                    quantity = quantity,
                    price = linePrice
                )
            )
        }

        val resolvedTotal = when {
            order.totalAmount > 0.0 -> order.totalAmount
            order.subtotal > 0.0 -> order.subtotal + order.deliveryFee - order.discount
            else -> items.sumOf { it.price }
        }

        val timestamp = order.createdAt?.time ?: order.updatedAt?.time ?: 0L

        return Order(
            id = order.id,
            patient = patient,
            items = items,
            totalAmount = resolvedTotal,
            status = mappedStatus,
            timestamp = timestamp
        )
    }

    private fun mapStatus(status: DataOrderStatus): OrderStatus? = when (status) {
        DataOrderStatus.PENDING -> OrderStatus.PENDING
        DataOrderStatus.CONFIRMED -> OrderStatus.CONFIRMED
        DataOrderStatus.PREPARING -> OrderStatus.PREPARING
        DataOrderStatus.READY -> OrderStatus.READY
        DataOrderStatus.SHIPPED -> OrderStatus.OUT_FOR_DELIVERY
        DataOrderStatus.DELIVERED -> OrderStatus.DELIVERED
        DataOrderStatus.CANCELLED,
        DataOrderStatus.RETURNED -> null
    }

    private fun computeAnalytics(orders: List<RefillOrder>): AnalyticsData {
        val todayStart = startOfDayMillis(System.currentTimeMillis())

        val totalOrdersToday = orders.count { order ->
            val timestamp = order.createdAt?.time ?: order.updatedAt?.time ?: 0L
            timestamp >= todayStart
        }

        val completedOrders = orders.count { it.status == DataOrderStatus.DELIVERED }
        val trackableOrders = orders.count {
            it.status != DataOrderStatus.CANCELLED && it.status != DataOrderStatus.RETURNED
        }

        val completionRate = if (trackableOrders == 0) {
            0.0
        } else {
            completedOrders.toDouble() / trackableOrders.toDouble()
        }

        val deliveredRevenue = orders
            .filter { it.status == DataOrderStatus.DELIVERED }
            .sumOf { order ->
                when {
                    order.totalAmount > 0.0 -> order.totalAmount
                    order.subtotal > 0.0 -> order.subtotal + order.deliveryFee - order.discount
                    else -> 0.0
                }
            }

        val statsRevenue = revenueStats["totalRevenue"] ?: 0.0
        val totalRevenue = if (statsRevenue > 0.0) statsRevenue else deliveredRevenue

        return AnalyticsData(
            totalOrdersToday = totalOrdersToday,
            totalRevenue = totalRevenue,
            completionRate = completionRate,
            topMedicines = computeTopMedicines(orders)
        )
    }

    private fun computeTopMedicines(orders: List<RefillOrder>): List<TopMedicineStat> {
        val medicineUnits = mutableMapOf<String, Int>()

        orders
            .filter { it.status != DataOrderStatus.CANCELLED && it.status != DataOrderStatus.RETURNED }
            .forEach { order ->
                if (order.items.isNotEmpty()) {
                    order.items.forEach { item ->
                        val medicineName = item.medicineName.ifBlank { "Medicine" }
                        val quantity = item.quantity.coerceAtLeast(1)
                        medicineUnits[medicineName] = (medicineUnits[medicineName] ?: 0) + quantity
                    }
                } else {
                    val medicineName = order.medicineName.ifBlank { "Medicine" }
                    val quantity = order.quantity.coerceAtLeast(1)
                    medicineUnits[medicineName] = (medicineUnits[medicineName] ?: 0) + quantity
                }
            }

        return medicineUnits.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { TopMedicineStat(medicineName = it.key, unitsSold = it.value) }
    }

    private fun computeForecast(orders: List<RefillOrder>): ForecastData {
        val orderTimes = orders.mapNotNull { order ->
            order.createdAt?.time ?: order.updatedAt?.time
        }

        if (orderTimes.isEmpty()) {
            return ForecastData(message = "Not enough data")
        }

        val dailyOrderCounts = buildDailyOrderSeries(orderTimes, lookbackDays = 14)
        val daysWithOrders = dailyOrderCounts.count { count -> count > 0 }
        val recentOrders = dailyOrderCounts.takeLast(7)
        val movingAverage = recentOrders.average()

        if (daysWithOrders < 3) {
            return ForecastData(
                hasEnoughData = false,
                movingAverage = movingAverage,
                recentOrders = recentOrders,
                projectedOrders = emptyList(),
                message = "Not enough data"
            )
        }

        val projectedOrders = generateMovingAverageProjection(
            historicalDailyOrders = dailyOrderCounts,
            forecastDays = 7,
            movingWindowDays = 7
        )

        return ForecastData(
            hasEnoughData = true,
            movingAverage = movingAverage,
            recentOrders = recentOrders,
            projectedOrders = projectedOrders,
            message = "Projected with 7-day moving average"
        )
    }

    private fun buildDailyOrderSeries(orderTimes: List<Long>, lookbackDays: Int): List<Int> {
        val countsByDay = orderTimes
            .groupBy { time -> startOfDayMillis(time) }
            .mapValues { (_, entries) -> entries.size }

        val startDay = Calendar.getInstance().apply {
            timeInMillis = startOfDayMillis(System.currentTimeMillis())
            add(Calendar.DAY_OF_YEAR, -(lookbackDays - 1))
        }

        return buildList {
            repeat(lookbackDays) {
                val dayKey = startOfDayMillis(startDay.timeInMillis)
                add(countsByDay[dayKey] ?: 0)
                startDay.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    }

    private fun generateMovingAverageProjection(
        historicalDailyOrders: List<Int>,
        forecastDays: Int,
        movingWindowDays: Int
    ): List<Int> {
        val rollingSeries = historicalDailyOrders.toMutableList()
        val projections = mutableListOf<Int>()

        repeat(forecastDays) {
            val window = rollingSeries.takeLast(movingWindowDays).ifEmpty { listOf(0) }
            val projectedValue = window.average().roundToInt().coerceAtLeast(0)
            projections.add(projectedValue)
            rollingSeries.add(projectedValue)
        }

        return projections
    }

    private fun startOfDayMillis(epochMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = epochMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun Order.matchesQuery(normalizedQuery: String): Boolean {
        val patientMatch = patient.name.lowercase(Locale.ROOT).contains(normalizedQuery)
        val idMatch = id.lowercase(Locale.ROOT).contains(normalizedQuery)
        val medicineMatch = items.any { item ->
            item.medicineName.lowercase(Locale.ROOT).contains(normalizedQuery)
        }
        return patientMatch || idMatch || medicineMatch
    }

    private suspend fun emitMessage(message: String) {
        _events.emit(PharmacyDashboardEvent.ShowMessage(message))
    }

    override fun onCleared() {
        super.onCleared()
        ordersJob?.cancel()
        lowStockJob?.cancel()
    }
}
