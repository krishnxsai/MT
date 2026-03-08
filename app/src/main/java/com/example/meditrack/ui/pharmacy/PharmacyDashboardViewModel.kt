package com.example.meditrack.ui.pharmacy

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meditrack.data.model.Pharmacy
import com.example.meditrack.data.model.RefillOrder
import com.example.meditrack.data.model.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class PharmacyDashboardViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _pharmacy = MutableLiveData<Resource<Pharmacy?>>()
    val pharmacy: LiveData<Resource<Pharmacy?>> = _pharmacy

    private val _orders = MutableLiveData<Resource<List<RefillOrder>>>()
    val orders: LiveData<Resource<List<RefillOrder>>> = _orders

    private val _orderCounts = MutableLiveData<Map<String, Int>>()
    val orderCounts: LiveData<Map<String, Int>> = _orderCounts

    /** Load the pharmacy document owned by the current user. */
    fun loadPharmacyProfile() {
        _pharmacy.value = Resource.Loading
        viewModelScope.launch {
            try {
                val uid = auth.currentUser?.uid ?: return@launch
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

                    // Once we have the pharmacy, load its orders
                    pharmacy?.let { loadOrders(it.id) }
                } else {
                    _pharmacy.value = Resource.Success(null) // No pharmacy profile yet
                }
            } catch (e: Exception) {
                _pharmacy.value = Resource.Error(e.message ?: "Failed to load pharmacy", e)
            }
        }
    }

    /** Load orders for this pharmacy. */
    private fun loadOrders(pharmacyId: String) {
        _orders.value = Resource.Loading
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    firestore.collection("orders")
                        .whereEqualTo("pharmacyId", pharmacyId)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .get()
                        .await()
                }
                val orders = result.documents.mapNotNull { doc ->
                    doc.data?.let { RefillOrder.fromMap(doc.id, it) }
                }
                _orders.value = Resource.Success(orders)

                // Calculate counts
                val counts = mapOf(
                    "total" to orders.size,
                    "pending" to orders.count { it.status.name == "PENDING" },
                    "completed" to orders.count { it.status.name == "DELIVERED" || it.status.name == "CONFIRMED" }
                )
                _orderCounts.value = counts
            } catch (e: Exception) {
                _orders.value = Resource.Error(e.message ?: "Failed to load orders", e)
            }
        }
    }

    /** Update order status. */
    fun updateOrderStatus(orderId: String, newStatus: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    firestore.collection("orders")
                        .document(orderId)
                        .update(
                            mapOf(
                                "status" to newStatus,
                                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )
                        )
                        .await()
                }
                // Reload orders
                val pharmacyResult = _pharmacy.value
                if (pharmacyResult is Resource.Success && pharmacyResult.data != null) {
                    loadOrders(pharmacyResult.data.id)
                }
            } catch (e: Exception) {
                // Handle error silently for now
            }
        }
    }
}

