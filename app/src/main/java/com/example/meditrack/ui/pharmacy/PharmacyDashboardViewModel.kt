package com.example.meditrack.ui.pharmacy

import android.app.Application
import androidx.lifecycle.*
import com.example.meditrack.data.model.OrderStatus
import com.example.meditrack.data.model.Pharmacy
import com.example.meditrack.data.model.RefillOrder
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.repository.PharmacyRepository
import com.example.meditrack.util.PharmacyNotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class PharmacyDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val pharmacyRepository = PharmacyRepository()

    private val _pharmacy = MutableLiveData<Resource<Pharmacy?>>()
    val pharmacy: LiveData<Resource<Pharmacy?>> = _pharmacy

    private val _orders = MutableLiveData<Resource<List<RefillOrder>>>()
    val orders: LiveData<Resource<List<RefillOrder>>> = _orders

    private val _orderCounts = MutableLiveData<Map<String, Int>>(mapOf("total" to 0, "pending" to 0, "completed" to 0))
    val orderCounts: LiveData<Map<String, Int>> = _orderCounts

    // ── Analytics ─────────────────────────────────────────────
    private val _dailyRevenue = MutableLiveData<Double>()
    val dailyRevenue: LiveData<Double> = _dailyRevenue

    private val _topMedicines = MutableLiveData<List<Pair<String, Int>>>()
    val topMedicines: LiveData<List<Pair<String, Int>>> = _topMedicines

    // ── Real-time listener ────────────────────────────────────
    private var ordersListener: ListenerRegistration? = null
    private var previousOrderIds: Set<String> = emptySet()

    /** Load the pharmacy document owned by the current user. */
    fun loadPharmacyProfile() {
        _pharmacy.value = Resource.Loading
        viewModelScope.launch {
            try {
                val uid = auth.currentUser?.uid
                if (uid == null) {
                    _pharmacy.value = Resource.Error("Not logged in")
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    firestore.collection("pharmacies")
                        .whereEqualTo("ownerId", uid)
                        .limit(1)
                        .get()
                        .await()
                }
                if (result.documents.isNotEmpty()) {
                    val doc = result.documents.first()
                    val pharmacy = doc.data?.let { Pharmacy.fromMap(doc.id, it) }
                    _pharmacy.value = Resource.Success(pharmacy)

                    // Once we have the pharmacy, start real-time orders listener
                    pharmacy?.let {
                        startOrdersListener(it.id)
                        loadAnalytics(it.id)
                    }
                } else {
                    _pharmacy.value = Resource.Success(null)
                }
            } catch (e: Exception) {
                _pharmacy.value = Resource.Error(e.message ?: "Failed to load pharmacy", e)
            }
        }
    }

    /** Start real-time listener for orders. */
    private fun startOrdersListener(pharmacyId: String) {
        ordersListener?.remove()
        _orders.value = Resource.Loading

        ordersListener = firestore.collection("orders")
            .whereEqualTo("pharmacyId", pharmacyId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _orders.value = Resource.Error(error.message ?: "Failed to load orders")
                    return@addSnapshotListener
                }

                val orders = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { RefillOrder.fromMap(doc.id, it) }
                } ?: emptyList()

                _orders.value = Resource.Success(orders)

                // Calculate counts
                val counts = mapOf(
                    "total" to orders.size,
                    "pending" to orders.count { it.status == OrderStatus.PENDING },
                    "completed" to orders.count {
                        it.status == OrderStatus.DELIVERED || it.status == OrderStatus.CONFIRMED
                    }
                )
                _orderCounts.value = counts

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

                // Compute top medicines
                computeTopMedicines(orders)
            }
    }

    /** Load analytics data. */
    private fun loadAnalytics(pharmacyId: String) {
        viewModelScope.launch {
            when (val result = pharmacyRepository.getRevenueStats(pharmacyId)) {
                is Resource.Success -> {
                    _dailyRevenue.value = result.data["todayRevenue"] ?: 0.0
                }
                else -> { _dailyRevenue.value = 0.0 }
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

    /** Update order status. */
    fun updateOrderStatus(orderId: String, newStatus: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val statusEntry = mapOf(
                        "status" to newStatus,
                        "changedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "note" to ""
                    )
                    firestore.collection("orders")
                        .document(orderId)
                        .update(
                            mapOf(
                                "status" to newStatus,
                                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                "statusHistory" to com.google.firebase.firestore.FieldValue.arrayUnion(statusEntry)
                            )
                        )
                        .await()
                }
                // Orders will auto-refresh via the real-time listener
            } catch (_: Exception) { }
        }
    }

    /** Get the current pharmacy ID. */
    fun getPharmacyId(): String? {
        return (_pharmacy.value as? Resource.Success)?.data?.id
    }

    override fun onCleared() {
        super.onCleared()
        ordersListener?.remove()
    }
}
