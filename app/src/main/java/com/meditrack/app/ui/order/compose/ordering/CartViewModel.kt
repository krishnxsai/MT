package com.meditrack.app.ui.order.compose.ordering

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.model.DeliveryAddress
import com.meditrack.app.data.model.DeliveryType
import com.meditrack.app.data.model.OrderItem
import com.meditrack.app.data.model.Pharmacy
import com.meditrack.app.data.model.RefillOrder
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.CartItem as PersistedCartItem
import com.meditrack.app.data.model.Medicine as DomainMedicine
import com.meditrack.app.data.repository.CartRepository
import com.meditrack.app.data.repository.MedicineRepository
import com.meditrack.app.data.repository.OrderRepository
import com.meditrack.app.data.repository.PharmacyInventoryRepository
import com.meditrack.app.data.repository.PharmacyRepository
import com.meditrack.app.util.FeatureFlags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_MEDICINE_PRICE = 50.0
private const val LOW_STOCK_LIMIT = 5
private const val DEFAULT_STOCK = 99

/**
 * Requested cart medicine contract for Compose ordering UI.
 */
data class Medicine(
    val id: String,
    val name: String,
    val mg: String,
    val price: Double,
    val stock: Int,
    val prescriptionRequired: Boolean,
    val originalName: String
)

/**
 * Requested cart item contract for Compose ordering UI.
 */
data class CartItem(
    val medicine: Medicine,
    val quantity: Int
)

enum class StockTag {
    OUT,
    URGENT
}

data class OrderingUiState(
    val isLoading: Boolean = true,
    val isPlacingOrder: Boolean = false,
    val searchQuery: String = "",
    val medicines: List<Medicine> = emptyList(),
    val lowStockMedicines: List<Medicine> = emptyList(),
    val cartItems: List<CartItem> = emptyList(),
    val pharmacies: List<Pharmacy> = emptyList(),
    val selectedPharmacy: Pharmacy? = null,
    val deliveryAddress: String = "",
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val total: Double = 0.0,
    val prescriptionRequiredCount: Int = 0
)

sealed interface CartUiEvent {
    data class ShowMessage(val message: String) : CartUiEvent
    data class LaunchPayment(val orderId: String, val amount: Double) : CartUiEvent
    data class LaunchTracking(val orderId: String) : CartUiEvent
}

@HiltViewModel
class CartViewModel @Inject constructor(
    private val medicineRepository: MedicineRepository,
    private val pharmacyRepository: PharmacyRepository,
    private val pharmacyInventoryRepository: PharmacyInventoryRepository,
    private val orderRepository: OrderRepository,
    private val cartRepository: CartRepository
) : ViewModel() {

    private data class CartSummary(
        val items: List<CartItem>,
        val subtotal: Double,
        val deliveryFee: Double,
        val total: Double
    )

    private data class CatalogState(
        val searchQuery: String,
        val medicines: List<Medicine>,
        val lowStockMedicines: List<Medicine>
    )

    private data class ScreenContextState(
        val isLoading: Boolean,
        val isPlacingOrder: Boolean,
        val pharmacies: List<Pharmacy>,
        val selectedPharmacy: Pharmacy?,
        val deliveryAddress: String
    )

    private val _isLoading = MutableStateFlow(true)
    private val _isPlacingOrder = MutableStateFlow(false)
    private val _medicineMap = MutableStateFlow<Map<String, Medicine>>(emptyMap())
    private val _cartQuantities = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _searchQuery = MutableStateFlow("")
    private val _deliveryAddress = MutableStateFlow("")
    private val _pharmacies = MutableStateFlow<List<Pharmacy>>(emptyList())
    private val _selectedPharmacyId = MutableStateFlow<String?>(null)

    private val _events = MutableSharedFlow<CartUiEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    @OptIn(FlowPreview::class)
    private val debouncedSearchQuery = _searchQuery
        .debounce(250)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val cartItems: StateFlow<List<CartItem>> = combine(_medicineMap, _cartQuantities) { medicines, quantities ->
        quantities.mapNotNull { (medicineId, quantity) ->
            val medicine = medicines[medicineId] ?: return@mapNotNull null
            CartItem(medicine = medicine, quantity = quantity)
        }.sortedBy { it.medicine.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val subtotal: StateFlow<Double> = cartItems
        .map { items -> items.sumOf { it.medicine.price * it.quantity } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val deliveryFee: StateFlow<Double> = MutableStateFlow(0.0)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val total: StateFlow<Double> = combine(subtotal, deliveryFee) { sub, fee -> sub + fee }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val selectedPharmacy: StateFlow<Pharmacy?> = combine(_pharmacies, _selectedPharmacyId) { pharmacies, selectedId ->
        pharmacies.firstOrNull { it.id == selectedId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val filteredMedicines: StateFlow<List<Medicine>> = combine(_medicineMap, debouncedSearchQuery) { medicines, query ->
        val base = medicines.values.sortedBy { it.name.lowercase() }
        if (query.isBlank()) {
            base
        } else {
            base.filter { med ->
                med.name.contains(query, ignoreCase = true) || med.mg.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val lowStockMedicines: StateFlow<List<Medicine>> = _medicineMap
        .map { medicines ->
            medicines.values
                .filter { it.stock <= LOW_STOCK_LIMIT }
                .sortedBy { it.stock }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val cartSummary: StateFlow<CartSummary> = combine(
        cartItems,
        subtotal,
        deliveryFee,
        total
    ) { items, sub, fee, grand ->
        CartSummary(
            items = items,
            subtotal = sub,
            deliveryFee = fee,
            total = grand
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        CartSummary(emptyList(), 0.0, 0.0, 0.0)
    )

    private val catalogState: StateFlow<CatalogState> = combine(
        _searchQuery,
        filteredMedicines,
        lowStockMedicines
    ) { query, medicines, lowStock ->
        CatalogState(
            searchQuery = query,
            medicines = medicines,
            lowStockMedicines = lowStock
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        CatalogState("", emptyList(), emptyList())
    )

    private val screenContextState: StateFlow<ScreenContextState> = combine(
        _isLoading,
        _isPlacingOrder,
        _pharmacies,
        selectedPharmacy,
        _deliveryAddress
    ) { isLoading, isPlacingOrder, pharmacies, selectedPharmacy, deliveryAddress ->
        ScreenContextState(
            isLoading = isLoading,
            isPlacingOrder = isPlacingOrder,
            pharmacies = pharmacies,
            selectedPharmacy = selectedPharmacy,
            deliveryAddress = deliveryAddress
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ScreenContextState(
            isLoading = true,
            isPlacingOrder = false,
            pharmacies = emptyList(),
            selectedPharmacy = null,
            deliveryAddress = ""
        )
    )

    val uiState: StateFlow<OrderingUiState> = combine(
        catalogState,
        screenContextState,
        cartSummary
    ) { catalogState, screenContextState, cartSummary ->
        OrderingUiState(
            isLoading = screenContextState.isLoading,
            isPlacingOrder = screenContextState.isPlacingOrder,
            searchQuery = catalogState.searchQuery,
            medicines = catalogState.medicines,
            lowStockMedicines = catalogState.lowStockMedicines,
            cartItems = cartSummary.items,
            pharmacies = screenContextState.pharmacies,
            selectedPharmacy = screenContextState.selectedPharmacy,
            deliveryAddress = screenContextState.deliveryAddress,
            subtotal = cartSummary.subtotal,
            deliveryFee = cartSummary.deliveryFee,
            total = cartSummary.total,
            prescriptionRequiredCount = cartSummary.items.count { it.medicine.prescriptionRequired }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OrderingUiState())

    init {
        loadInitialData()
    }

    fun onSearchChanged(query: String) {
        _searchQuery.value = query
    }

    fun onDeliveryAddressChanged(address: String) {
        _deliveryAddress.value = address
    }

    fun selectPharmacy(pharmacyId: String) {
        _selectedPharmacyId.value = pharmacyId
        viewModelScope.launch {
            refreshPricesForSelectedPharmacy()
            persistCartState()
        }
    }

    fun addToCart(medicine: Medicine) {
        if (medicine.stock <= 0) {
            emitMessage("${medicine.name} is out of stock")
            return
        }

        val currentQty = _cartQuantities.value[medicine.id] ?: 0
        if (currentQty >= medicine.stock) {
            emitMessage("Only ${medicine.stock} units available for ${medicine.name}")
            return
        }

        _cartQuantities.update { it + (medicine.id to (currentQty + 1)) }
        persistCartStateAsync()
    }

    fun increaseQuantity(medicineId: String) {
        val medicine = _medicineMap.value[medicineId] ?: return
        val currentQty = _cartQuantities.value[medicineId] ?: 0

        if (currentQty <= 0) {
            addToCart(medicine)
            return
        }

        if (currentQty >= medicine.stock) {
            emitMessage("Stock exceeded for ${medicine.name}")
            return
        }

        _cartQuantities.update { it + (medicineId to (currentQty + 1)) }
        persistCartStateAsync()
    }

    fun decreaseQuantity(medicineId: String) {
        val currentQty = _cartQuantities.value[medicineId] ?: return
        if (currentQty <= 1) {
            removeFromCart(medicineId)
            return
        }

        _cartQuantities.update { it + (medicineId to (currentQty - 1)) }
        persistCartStateAsync()
    }

    fun removeFromCart(medicineId: String) {
        _cartQuantities.update { quantities ->
            quantities.toMutableMap().apply { remove(medicineId) }
        }
        persistCartStateAsync()
    }

    fun getQuantity(medicineId: String): Int = _cartQuantities.value[medicineId] ?: 0

    fun applyGpsAddress(address: String) {
        _deliveryAddress.value = address
    }

    fun refresh() {
        loadInitialData()
    }

    fun handleIncompletePayment(orderId: String, reason: String) {
        viewModelScope.launch {
            orderRepository.cancelOrderWithRefund(orderId = orderId, reason = reason)
        }
    }

    fun placeOrder() {
        val selectedPharmacyValue = selectedPharmacy.value
        val cartItemsValue = cartItems.value
        val address = _deliveryAddress.value.trim()

        if (selectedPharmacyValue == null) {
            emitMessage("Please select a pharmacy")
            return
        }

        if (cartItemsValue.isEmpty()) {
            emitMessage("Your cart is empty")
            return
        }

        if (address.isBlank()) {
            emitMessage("Please enter delivery address")
            return
        }

        val stockIssue = cartItemsValue.firstOrNull { it.quantity > it.medicine.stock }
        if (stockIssue != null) {
            emitMessage("Only ${stockIssue.medicine.stock} units available for ${stockIssue.medicine.name}")
            return
        }

        viewModelScope.launch {
            _isPlacingOrder.value = true

            val orderItems = cartItemsValue.map {
                OrderItem(
                    medicineId = it.medicine.id,
                    medicineName = it.medicine.name,
                    medicineDosage = it.medicine.mg,
                    quantity = it.quantity,
                    unitPrice = it.medicine.price,
                    totalPrice = it.medicine.price * it.quantity,
                    prescriptionRequired = it.medicine.prescriptionRequired
                )
            }

            val deliveryMinutes = parseDeliveryMinutes(selectedPharmacyValue.estimatedDeliveryTime)
            val subtotalValue = subtotal.value

            val order = RefillOrder(
                pharmacyId = selectedPharmacyValue.id,
                pharmacyName = selectedPharmacyValue.name,
                items = orderItems,
                subtotal = subtotalValue,
                deliveryFee = deliveryFee.value,
                totalAmount = subtotalValue + deliveryFee.value,
                deliveryAddress = DeliveryAddress(fullAddress = address),
                deliveryType = DeliveryType.DELIVERY,
                estimatedDeliveryMinutes = deliveryMinutes,
                notes = "Order placed from compose flow"
            )

            when (val result = orderRepository.placeOrder(order)) {
                is Resource.Success -> {
                    clearLocalCartState()
                    if (FeatureFlags.isEnabled(FeatureFlags.PAYMENT_ENABLED)) {
                        _events.emit(CartUiEvent.LaunchPayment(result.data, subtotalValue))
                    } else {
                        _events.emit(CartUiEvent.LaunchTracking(result.data))
                    }
                }
                is Resource.Error -> {
                    emitMessage(result.message ?: "Failed to place order")
                }
                is Resource.Loading -> Unit
            }

            _isPlacingOrder.value = false
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _isLoading.value = true

            val medicinesDeferred = async { medicineRepository.getMedicines() }
            val pharmaciesDeferred = async { pharmacyRepository.getPharmacies() }

            val medicinesResult = medicinesDeferred.await()
            val pharmaciesResult = pharmaciesDeferred.await()

            if (medicinesResult is Resource.Success) {
                _medicineMap.value = medicinesResult.data.associateBy(
                    keySelector = { it.id },
                    valueTransform = { it.toUiMedicine() }
                )
            } else if (medicinesResult is Resource.Error) {
                emitMessage(medicinesResult.message ?: "Failed to load medicines")
            }

            if (pharmaciesResult is Resource.Success) {
                _pharmacies.value = pharmaciesResult.data
                if (_selectedPharmacyId.value == null) {
                    _selectedPharmacyId.value = pharmaciesResult.data.firstOrNull()?.id
                }
            } else if (pharmaciesResult is Resource.Error) {
                emitMessage(pharmaciesResult.message ?: "Failed to load pharmacies")
            }

            restorePersistedCart()
            refreshPricesForSelectedPharmacy()
            _isLoading.value = false
        }
    }

    private suspend fun restorePersistedCart() {
        when (val persisted = cartRepository.getCart()) {
            is Resource.Success -> {
                val cart = persisted.data ?: return
                val fallbackMedicines = mutableMapOf<String, Medicine>()
                val quantities = mutableMapOf<String, Int>()

                cart.items.forEach { item ->
                    val quantity = item.quantity.coerceAtLeast(1)
                    quantities[item.medicineId] = quantity

                    if (!_medicineMap.value.containsKey(item.medicineId)) {
                        fallbackMedicines[item.medicineId] = Medicine(
                            id = item.medicineId,
                            name = item.medicineName,
                            mg = item.medicineDosage,
                            price = DEFAULT_MEDICINE_PRICE,
                            stock = DEFAULT_STOCK,
                            prescriptionRequired = item.prescriptionRequired,
                            originalName = item.medicineName
                        )
                    }
                }

                if (fallbackMedicines.isNotEmpty()) {
                    _medicineMap.update { current -> current + fallbackMedicines }
                }
                _cartQuantities.value = quantities

                if (!cart.selectedPharmacyId.isNullOrBlank() && _pharmacies.value.any { it.id == cart.selectedPharmacyId }) {
                    _selectedPharmacyId.value = cart.selectedPharmacyId
                }
            }
            is Resource.Error -> {
                emitMessage("Unable to restore cart. Starting with empty cart.")
            }
            is Resource.Loading -> Unit
        }
    }

    private suspend fun refreshPricesForSelectedPharmacy() {
        val selected = selectedPharmacy.value ?: return
        val medicines = _medicineMap.value.values.toList()

        val updated = medicines.map { medicine ->
            viewModelScope.async {
                when (
                    val inventoryResult = pharmacyInventoryRepository.getInventoryItem(
                        pharmacyId = selected.id,
                        medicineName = medicine.originalName
                    )
                ) {
                    is Resource.Success -> {
                        val item = inventoryResult.data
                        if (item != null) {
                            medicine.copy(
                                price = item.unitPrice.takeIf { it > 0 } ?: medicine.price,
                                stock = item.stockQuantity.coerceAtLeast(0)
                            )
                        } else {
                            medicine
                        }
                    }
                    is Resource.Error -> medicine
                    is Resource.Loading -> medicine
                }
            }
        }.awaitAll()

        _medicineMap.value = updated.associateBy { it.id }

        // Remove any cart items that are now impossible to fulfill.
        _cartQuantities.update { quantities ->
            quantities.mapNotNull { (id, quantity) ->
                val stock = _medicineMap.value[id]?.stock ?: 0
                when {
                    stock <= 0 -> null
                    quantity > stock -> id to stock
                    else -> id to quantity
                }
            }.toMap()
        }
        persistCartState()
    }

    private fun clearLocalCartState() {
        _cartQuantities.value = emptyMap()
        _deliveryAddress.value = ""
        viewModelScope.launch {
            cartRepository.clearCart()
        }
    }

    private fun persistCartStateAsync() {
        viewModelScope.launch {
            persistCartState()
        }
    }

    private suspend fun persistCartState() {
        val persistedItems = cartItems.value.map {
            PersistedCartItem(
                medicineId = it.medicine.id,
                medicineName = it.medicine.name,
                medicineDosage = it.medicine.mg,
                medicineUnit = "",
                quantity = it.quantity,
                prescriptionRequired = it.medicine.prescriptionRequired
            )
        }

        when (val result = cartRepository.saveCart(persistedItems, _selectedPharmacyId.value)) {
            is Resource.Error -> emitMessage(result.message ?: "Unable to save cart")
            is Resource.Success -> Unit
            is Resource.Loading -> Unit
        }
    }

    private fun parseDeliveryMinutes(raw: String): Int {
        val minutes = raw.filter { it.isDigit() }.toIntOrNull()
        return minutes ?: 40
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(CartUiEvent.ShowMessage(message))
        }
    }

    private fun DomainMedicine.toUiMedicine(): Medicine {
        val effectiveStock = when {
            currentQuantity >= 0 -> currentQuantity
            totalQuantity > 0 -> totalQuantity
            else -> DEFAULT_STOCK
        }

        return Medicine(
            id = id,
            name = name,
            mg = dosage.ifBlank { unit.ifBlank { "Standard" } },
            price = DEFAULT_MEDICINE_PRICE,
            stock = effectiveStock,
            prescriptionRequired = prescribedByDoctor || prescriptionId.isNotBlank(),
            originalName = name
        )
    }
}
