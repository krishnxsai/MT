package com.meditrack.app.ui.order.unified

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.analytics.RefillDetectionEngine
import com.meditrack.app.data.model.*
import com.meditrack.app.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.*

/**
 * ViewModel for the unified medicine ordering screen.
 * Combines low-stock medicines, pharmacy selection, and cart management.
 */
@HiltViewModel
class UnifiedOrderViewModel @Inject constructor(
    private val medicineRepository: MedicineRepository,
    private val pharmacyRepository: PharmacyRepository,
    private val orderRepository: OrderRepository,
    private val refillAlertRepository: RefillAlertRepository
) : ViewModel() {

    // ==================== UI STATE ====================

    /**
     * Main UI state for the unified order screen.
     */
    sealed class UiState {
        object Loading : UiState()
        data class Success(
            val lowStockMedicines: List<LowStockItem>,
            val nearbyPharmacies: List<PharmacyWithDistance>,
            val searchResults: List<Medicine>
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    /**
     * Medicine with low stock information.
     */
    data class LowStockItem(
        val medicine: Medicine,
        val urgency: RefillUrgency,
        val daysRemaining: Int,
        val stockPercentage: Int,
        val statusText: String,
        val isInCart: Boolean = false
    )

    /**
     * Pharmacy with distance from user.
     */
    data class PharmacyWithDistance(
        val pharmacy: Pharmacy,
        val distanceKm: Double,
        val distanceText: String,
        val isSelected: Boolean = false
    )

    /**
     * Cart item with pricing information.
     */
    data class CartItemWithPrice(
        val cartItem: CartItem,
        val unitPrice: Double,
        val totalPrice: Double,
        val isAvailable: Boolean
    )

    /**
     * Navigation events.
     */
    sealed class NavigationEvent {
        data class ToOrderConfirmation(val orderId: String) : NavigationEvent()
        data class ToPharmacyMap(val pharmacies: List<Pharmacy>) : NavigationEvent()
        data class ToOrderTracking(val orderId: String) : NavigationEvent()
        data class ToPayment(val orderId: String, val amount: Double) : NavigationEvent()
    }

    // ==================== STATE FLOWS ====================

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _cartWithPrices = MutableStateFlow<List<CartItemWithPrice>>(emptyList())
    val cartWithPrices: StateFlow<List<CartItemWithPrice>> = _cartWithPrices.asStateFlow()

    private val _selectedPharmacy = MutableStateFlow<Pharmacy?>(null)
    val selectedPharmacy: StateFlow<Pharmacy?> = _selectedPharmacy.asStateFlow()

    private val _userLocation = MutableStateFlow<Location?>(null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isPlacingOrder = MutableStateFlow(false)
    val isPlacingOrder: StateFlow<Boolean> = _isPlacingOrder.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Computed totals
    val cartSubtotal: StateFlow<Double> = _cartWithPrices.map { items ->
        items.sumOf { it.totalPrice }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val cartItemCount: StateFlow<Int> = _cart.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val deliveryFee: StateFlow<Double> = _selectedPharmacy.map { pharmacy ->
        if (pharmacy?.isDeliveryAvailable == true) 20.0 else 0.0
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val cartTotal: StateFlow<Double> = combine(cartSubtotal, deliveryFee) { subtotal, fee ->
        subtotal + fee
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    // ==================== INITIALIZATION ====================

    init {
        loadData()
        observeCartChanges()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            try {
                // Load medicines and pharmacies in parallel
                val medicinesDeferred = async { medicineRepository.getMedicines() }
                val pharmaciesDeferred = async { pharmacyRepository.getPharmacies() }

                val medicinesResult = medicinesDeferred.await()
                val pharmaciesResult = pharmaciesDeferred.await()

                val lowStockMedicines = when (medicinesResult) {
                    is Resource.Success -> processLowStockMedicines(medicinesResult.data)
                    else -> emptyList()
                }

                val pharmacies = when (pharmaciesResult) {
                    is Resource.Success -> processPharmacies(pharmaciesResult.data)
                    else -> emptyList()
                }

                _uiState.value = UiState.Success(
                    lowStockMedicines = lowStockMedicines,
                    nearbyPharmacies = pharmacies,
                    searchResults = emptyList()
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to load data")
            }
        }
    }

    private fun processLowStockMedicines(medicines: List<Medicine>): List<LowStockItem> {
        val cartMedicineIds = _cart.value.map { it.medicineId }.toSet()

        return medicines
            .filter { it.isActive && it.isRefillTrackingEnabled }
            .mapNotNull { medicine ->
                val info = RefillDetectionEngine.analyzeMedicine(medicine)
                if (info.urgency == RefillUrgency.NONE) null
                else LowStockItem(
                    medicine = medicine,
                    urgency = info.urgency,
                    daysRemaining = info.daysRemaining,
                    stockPercentage = info.stockPercentage,
                    statusText = info.statusText,
                    isInCart = medicine.id in cartMedicineIds
                )
            }
            .sortedByDescending { it.urgency.ordinal }
    }

    private fun processPharmacies(pharmacies: List<Pharmacy>): List<PharmacyWithDistance> {
        val userLoc = _userLocation.value
        val selectedId = _selectedPharmacy.value?.id

        return pharmacies.map { pharmacy ->
            val distance = if (userLoc != null) {
                calculateDistance(
                    userLoc.latitude, userLoc.longitude,
                    pharmacy.latitude, pharmacy.longitude
                )
            } else {
                Double.MAX_VALUE
            }

            PharmacyWithDistance(
                pharmacy = pharmacy,
                distanceKm = distance,
                distanceText = formatDistance(distance),
                isSelected = pharmacy.id == selectedId
            )
        }.sortedBy { it.distanceKm }
    }

    private fun observeCartChanges() {
        viewModelScope.launch {
            _cart.collect { cartItems ->
                // Update cart items with mock pricing (in real app, fetch from pharmacy inventory)
                val priced = cartItems.map { item ->
                    val unitPrice = 50.0 // Mock price - should come from pharmacy inventory
                    CartItemWithPrice(
                        cartItem = item,
                        unitPrice = unitPrice,
                        totalPrice = unitPrice * item.quantity,
                        isAvailable = true
                    )
                }
                _cartWithPrices.value = priced

                // Refresh low-stock list to update isInCart status
                refreshLowStockStatus()
            }
        }
    }

    private fun refreshLowStockStatus() {
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            val cartMedicineIds = _cart.value.map { it.medicineId }.toSet()
            val updatedLowStock = currentState.lowStockMedicines.map { item ->
                item.copy(isInCart = item.medicine.id in cartMedicineIds)
            }
            _uiState.value = currentState.copy(lowStockMedicines = updatedLowStock)
        }
    }

    // ==================== USER LOCATION ====================

    fun updateUserLocation(location: Location) {
        _userLocation.value = location
        // Refresh pharmacy distances
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is UiState.Success) {
                val pharmaciesResult = pharmacyRepository.getPharmacies()
                if (pharmaciesResult is Resource.Success) {
                    val updatedPharmacies = processPharmacies(pharmaciesResult.data)
                    _uiState.value = currentState.copy(nearbyPharmacies = updatedPharmacies)
                }
            }
        }
    }

    // ==================== CART OPERATIONS ====================

    fun addToCart(medicine: Medicine, fromAlert: Boolean = false, alertId: String? = null) {
        val existingItem = _cart.value.find { it.medicineId == medicine.id }
        if (existingItem != null) {
            // Already in cart, don't add again
            return
        }

        val newItem = CartItem.fromMedicine(
            medicine = medicine,
            quantity = medicine.totalQuantity.takeIf { it > 0 } ?: 30,
            fromAlert = fromAlert,
            alertId = alertId
        )

        _cart.value = _cart.value + newItem
    }

    fun removeFromCart(medicineId: String) {
        _cart.value = _cart.value.filter { it.medicineId != medicineId }
    }

    fun updateQuantity(medicineId: String, quantity: Int) {
        if (quantity <= 0) {
            removeFromCart(medicineId)
            return
        }

        _cart.value = _cart.value.map { item ->
            if (item.medicineId == medicineId) {
                item.copy(quantity = quantity)
            } else {
                item
            }
        }
    }

    fun clearCart() {
        _cart.value = emptyList()
        _selectedPharmacy.value = null
    }

    fun isInCart(medicineId: String): Boolean {
        return _cart.value.any { it.medicineId == medicineId }
    }

    // ==================== PHARMACY SELECTION ====================

    fun selectPharmacy(pharmacy: Pharmacy) {
        _selectedPharmacy.value = pharmacy

        // Update pharmacy list to show selection
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            val updatedPharmacies = currentState.nearbyPharmacies.map { item ->
                item.copy(isSelected = item.pharmacy.id == pharmacy.id)
            }
            _uiState.value = currentState.copy(nearbyPharmacies = updatedPharmacies)
        }
    }

    fun clearPharmacySelection() {
        _selectedPharmacy.value = null

        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            val updatedPharmacies = currentState.nearbyPharmacies.map { item ->
                item.copy(isSelected = false)
            }
            _uiState.value = currentState.copy(nearbyPharmacies = updatedPharmacies)
        }
    }

    fun selectPharmacyById(pharmacyId: String) {
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            val pharmacy = currentState.nearbyPharmacies.find { it.pharmacy.id == pharmacyId }?.pharmacy
            pharmacy?.let { selectPharmacy(it) }
        }
    }

    // ==================== SEARCH ====================

    fun search(query: String) {
        _searchQuery.value = query

        if (query.length < 2) {
            val currentState = _uiState.value
            if (currentState is UiState.Success) {
                _uiState.value = currentState.copy(searchResults = emptyList())
            }
            return
        }

        viewModelScope.launch {
            val result = medicineRepository.getMedicines()
            if (result is Resource.Success) {
                val filtered = result.data.filter { medicine ->
                    medicine.name.contains(query, ignoreCase = true) ||
                    medicine.dosage.contains(query, ignoreCase = true)
                }
                val currentState = _uiState.value
                if (currentState is UiState.Success) {
                    _uiState.value = currentState.copy(searchResults = filtered)
                }
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            _uiState.value = currentState.copy(searchResults = emptyList())
        }
    }

    // ==================== ORDER PLACEMENT ====================

    fun placeOrder(deliveryAddress: DeliveryAddress? = null, notes: String = "") {
        val pharmacy = _selectedPharmacy.value
        if (pharmacy == null) {
            _error.value = "Please select a pharmacy first"
            return
        }

        val cartItems = _cart.value
        if (cartItems.isEmpty()) {
            _error.value = "Your cart is empty"
            return
        }

        viewModelScope.launch {
            _isPlacingOrder.value = true

            try {
                // Build order items
                val orderItems = _cartWithPrices.value.map { cartItemWithPrice ->
                    OrderItem(
                        medicineId = cartItemWithPrice.cartItem.medicineId,
                        medicineName = cartItemWithPrice.cartItem.medicineName,
                        medicineDosage = cartItemWithPrice.cartItem.medicineDosage,
                        quantity = cartItemWithPrice.cartItem.quantity,
                        unitPrice = cartItemWithPrice.unitPrice,
                        totalPrice = cartItemWithPrice.totalPrice,
                        prescriptionRequired = cartItemWithPrice.cartItem.prescriptionRequired,
                        prescriptionId = cartItemWithPrice.cartItem.prescriptionId
                    )
                }

                val subtotal = cartSubtotal.value
                val fee = deliveryFee.value
                val total = cartTotal.value

                // Calculate estimated delivery time
                val deliveryMinutes = pharmacy.estimatedDeliveryTime.filter { it.isDigit() }.toIntOrNull() ?: 30
                val estimatedDeliveryTime = java.util.Date(System.currentTimeMillis() + deliveryMinutes * 60 * 1000L)

                // Create the order
                val order = RefillOrder(
                    userId = "", // Will be set by repository
                    pharmacyId = pharmacy.id,
                    pharmacyName = pharmacy.name,
                    items = orderItems,
                    subtotal = subtotal,
                    deliveryFee = fee,
                    totalAmount = total,
                    deliveryAddress = deliveryAddress,
                    deliveryType = if (deliveryAddress != null) DeliveryType.DELIVERY else DeliveryType.PICKUP,
                    estimatedDeliveryMinutes = deliveryMinutes,
                    estimatedDelivery = estimatedDeliveryTime,
                    notes = notes,
                    status = OrderStatus.PENDING
                )

                // Place the order
                when (val result = orderRepository.placeOrder(order)) {
                    is Resource.Success -> {
                        val orderId = result.data

                        // Mark alerts as actioned
                        cartItems.filter { it.fromLowStockAlert && it.alertId != null }
                            .forEach { item ->
                                item.alertId?.let { alertId ->
                                    refillAlertRepository.markOrderPlaced(item.medicineId, orderId)
                                }
                            }

                        // Clear cart
                        clearCart()

                        // Navigate to payment screen (feature flag check will be done in Activity)
                        _navigationEvent.emit(NavigationEvent.ToPayment(orderId, total))
                    }
                    is Resource.Error -> {
                        _error.value = result.message ?: "Failed to place order"
                    }
                    is Resource.Loading -> {
                        // Ignore
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to place order"
            } finally {
                _isPlacingOrder.value = false
            }
        }
    }

    // ==================== MAP VIEW ====================

    fun openPharmacyMap() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is UiState.Success) {
                val pharmacies = currentState.nearbyPharmacies.map { it.pharmacy }
                _navigationEvent.emit(NavigationEvent.ToPharmacyMap(pharmacies))
            }
        }
    }

    // ==================== HELPERS ====================

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun formatDistance(distanceKm: Double): String {
        return when {
            distanceKm == Double.MAX_VALUE -> "Unknown"
            distanceKm < 1.0 -> "${(distanceKm * 1000).toInt()} m"
            else -> String.format("%.1f km", distanceKm)
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun refresh() {
        loadData()
    }
}
