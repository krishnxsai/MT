package com.example.meditrack.ui.order

import androidx.lifecycle.*
import com.example.meditrack.data.model.*
import com.example.meditrack.data.repository.OrderRepository
import com.example.meditrack.data.repository.PharmacyRepository
import com.example.meditrack.data.repository.PrescriptionVerification
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel for medicine refill orders, pharmacy selection,
 * prescription verification, stock management, and transaction logging.
 */
class OrderViewModel : ViewModel() {

    private val repository = OrderRepository()
    private val pharmacyRepository = PharmacyRepository()

    // ── All orders ──
    private val _orders = MutableLiveData<Resource<List<RefillOrder>>>()
    val orders: LiveData<Resource<List<RefillOrder>>> = _orders

    // ── Orders for a specific medicine ──
    private val _medicineOrders = MutableLiveData<Resource<List<RefillOrder>>>()
    val medicineOrders: LiveData<Resource<List<RefillOrder>>> = _medicineOrders

    // ── Low stock medicines ──
    private val _lowStockMedicines = MutableLiveData<Resource<List<Medicine>>>()
    val lowStockMedicines: LiveData<Resource<List<Medicine>>> = _lowStockMedicines

    // ── Action results ──
    private val _placeOrderResult = MutableLiveData<Resource<RefillOrder>>()
    val placeOrderResult: LiveData<Resource<RefillOrder>> = _placeOrderResult

    private val _actionResult = MutableLiveData<Resource<Unit>>()
    val actionResult: LiveData<Resource<Unit>> = _actionResult

    // ── Pharmacies ──
    private val _pharmacies = MutableLiveData<Resource<List<Pharmacy>>>()
    val pharmacies: LiveData<Resource<List<Pharmacy>>> = _pharmacies

    // ── Prescription verification ──
    private val _prescriptionVerification = MutableLiveData<Resource<PrescriptionVerification>>()
    val prescriptionVerification: LiveData<Resource<PrescriptionVerification>> = _prescriptionVerification

    // ── Transactions ──
    private val _transactions = MutableLiveData<Resource<List<OrderTransaction>>>()
    val transactions: LiveData<Resource<List<OrderTransaction>>> = _transactions

    // ═══════════════════════════════════════════════════════════
    // Order Listing
    // ═══════════════════════════════════════════════════════════

    fun loadOrders() {
        viewModelScope.launch {
            repository.getOrdersFlow().collectLatest {
                _orders.postValue(it)
            }
        }
    }

    fun loadOrdersForMedicine(medicineId: String) {
        viewModelScope.launch {
            repository.getOrdersForMedicineFlow(medicineId).collectLatest {
                _medicineOrders.postValue(it)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Place Order (with pharmacy + prescription + transaction)
    // ═══════════════════════════════════════════════════════════

    fun placeRefillOrder(medicine: Medicine, quantity: Int, notes: String = "") {
        viewModelScope.launch {
            _placeOrderResult.postValue(Resource.Loading)
            val result = repository.placeRefillOrder(medicine, quantity, notes)
            _placeOrderResult.postValue(result)

            // Log the transaction if order was placed successfully
            if (result is Resource.Success) {
                val verification = _prescriptionVerification.value
                val isVerified = (verification as? Resource.Success)?.data?.isValid ?: false
                pharmacyRepository.logOrderTransaction(
                    order = result.data,
                    pharmacy = null,
                    prescriptionVerified = isVerified
                )
            }
        }
    }

    /**
     * Place a refill order with a selected pharmacy.
     */
    fun placeRefillOrderWithPharmacy(
        medicine: Medicine,
        quantity: Int,
        pharmacyId: String,
        pharmacyName: String,
        notes: String = ""
    ) {
        viewModelScope.launch {
            _placeOrderResult.postValue(Resource.Loading)
            val result = repository.placeRefillOrderWithPharmacy(
                medicine, quantity, pharmacyId, pharmacyName, notes
            )
            _placeOrderResult.postValue(result)

            // Log the transaction
            if (result is Resource.Success) {
                val verification = _prescriptionVerification.value
                val isVerified = (verification as? Resource.Success)?.data?.isValid ?: false
                val pharmacy = (_pharmacies.value as? Resource.Success)?.data
                    ?.find { it.id == pharmacyId }
                pharmacyRepository.logOrderTransaction(
                    order = result.data,
                    pharmacy = pharmacy,
                    prescriptionVerified = isVerified
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Status Management
    // ═══════════════════════════════════════════════════════════

    fun confirmOrder(orderId: String) {
        viewModelScope.launch {
            _actionResult.postValue(Resource.Loading)
            _actionResult.postValue(
                repository.updateOrderStatus(orderId, OrderStatus.CONFIRMED, "Order confirmed")
            )
        }
    }

    fun markShipped(orderId: String) {
        viewModelScope.launch {
            _actionResult.postValue(Resource.Loading)
            _actionResult.postValue(
                repository.updateOrderStatus(orderId, OrderStatus.SHIPPED, "Order shipped")
            )
        }
    }

    fun markDelivered(orderId: String) {
        viewModelScope.launch {
            _actionResult.postValue(Resource.Loading)
            _actionResult.postValue(
                repository.updateOrderStatus(orderId, OrderStatus.DELIVERED, "Order delivered — stock updated")
            )
        }
    }

    fun cancelOrder(orderId: String, reason: String = "") {
        viewModelScope.launch {
            _actionResult.postValue(Resource.Loading)
            _actionResult.postValue(repository.cancelOrder(orderId, reason))
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Stock Management
    // ═══════════════════════════════════════════════════════════

    fun decrementStock(medicineId: String, amount: Int = 1) {
        viewModelScope.launch {
            repository.decrementStock(medicineId, amount)
        }
    }

    fun enableRefillTracking(
        medicineId: String,
        currentQty: Int,
        totalQty: Int,
        lowThreshold: Int = 5,
        reminderEnabled: Boolean = true
    ) {
        viewModelScope.launch {
            _actionResult.postValue(Resource.Loading)
            _actionResult.postValue(
                repository.enableRefillTracking(medicineId, currentQty, totalQty, lowThreshold, reminderEnabled)
            )
        }
    }

    fun disableRefillTracking(medicineId: String) {
        viewModelScope.launch {
            _actionResult.postValue(Resource.Loading)
            _actionResult.postValue(repository.disableRefillTracking(medicineId))
        }
    }

    fun loadLowStockMedicines() {
        viewModelScope.launch {
            _lowStockMedicines.postValue(Resource.Loading)
            _lowStockMedicines.postValue(repository.getLowStockMedicines())
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Pharmacy
    // ═══════════════════════════════════════════════════════════

    fun loadPharmacies() {
        viewModelScope.launch {
            // Seed sample pharmacies on first-ever load
            pharmacyRepository.seedPharmaciesIfEmpty()
            pharmacyRepository.getPharmaciesFlow().collectLatest {
                _pharmacies.postValue(it)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Prescription Verification
    // ═══════════════════════════════════════════════════════════

    fun verifyPrescription(medicine: Medicine) {
        viewModelScope.launch {
            _prescriptionVerification.postValue(Resource.Loading)
            _prescriptionVerification.postValue(pharmacyRepository.verifyPrescription(medicine))
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Transaction History
    // ═══════════════════════════════════════════════════════════

    fun loadTransactions() {
        viewModelScope.launch {
            pharmacyRepository.getTransactionsFlow().collectLatest {
                _transactions.postValue(it)
            }
        }
    }
}

