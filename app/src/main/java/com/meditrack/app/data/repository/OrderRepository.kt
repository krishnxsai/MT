package com.meditrack.app.data.repository

import android.util.Log
import com.meditrack.app.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Repository for medicine refill orders.
 *
 * Handles:
 *  • Creating refill orders
 *  • Tracking order status changes
 *  • Updating medicine quantity on delivery
 *  • Decrementing stock when medicine is taken
 *  • Low-stock detection
 */
class OrderRepository {

    companion object {
        private const val TAG = "OrderRepository"
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val ordersCol by lazy { firestore.collection("orders") }
    private val medicinesCol by lazy { firestore.collection("medicines") }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // ─────────────── Order Listing ───────────────

    /**
     * Real-time flow of all orders for the current user, newest first.
     */
    fun getOrdersFlow(): Flow<Resource<List<RefillOrder>>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = ordersCol
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "getOrdersFlow error: ${error.message}")
                    // Gracefully return empty for permission/index errors
                    trySend(Resource.Success(emptyList()))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { RefillOrder.fromMap(doc.id, it) }
                } ?: emptyList()
                trySend(Resource.Success(list))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get orders for a specific medicine.
     */
    fun getOrdersForMedicineFlow(medicineId: String): Flow<Resource<List<RefillOrder>>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = ordersCol
            .whereEqualTo("userId", userId)
            .whereEqualTo("medicineId", medicineId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to load orders"))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { RefillOrder.fromMap(doc.id, it) }
                } ?: emptyList()
                trySend(Resource.Success(list))
            }

        awaitClose { listener.remove() }
    }

    // ─────────────── Order Creation ───────────────

    /**
     * Place a refill order for a medicine.
     *
     * @param medicine   The medicine to refill
     * @param quantity   Number of units to order
     * @param notes      Optional notes (e.g., "Need by Friday")
     */
    suspend fun placeRefillOrder(
        medicine: Medicine,
        quantity: Int,
        notes: String = ""
    ): Resource<RefillOrder> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            if (quantity <= 0) {
                return@withContext Resource.Error("Quantity must be positive")
            }

            val initialStatus = OrderStatusEntry(
                status = OrderStatus.PENDING.name,
                changedAt = Date(),
                note = "Order placed"
            )

            val order = RefillOrder(
                userId = userId,
                medicineId = medicine.id,
                medicineName = medicine.name,
                medicineDosage = medicine.dosage,
                medicineUnit = medicine.unit,
                quantity = quantity,
                status = OrderStatus.PENDING,
                notes = notes.trim(),
                prescriptionId = medicine.prescriptionId,
                statusHistory = listOf(initialStatus)
            )

            val docRef = ordersCol.document()
            val data = order.toMap().toMutableMap()
            data["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

            docRef.set(data).await()

            Resource.Success(order.copy(id = docRef.id))
        } catch (e: Exception) {
            Log.e(TAG, "placeRefillOrder error: ${e.message}")
            Resource.Error(e.message ?: "Failed to place order")
        }
    }

    /**
     * Place a refill order with a selected pharmacy.
     */
    suspend fun placeRefillOrderWithPharmacy(
        medicine: Medicine,
        quantity: Int,
        pharmacyId: String,
        pharmacyName: String,
        notes: String = ""
    ): Resource<RefillOrder> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")
            if (quantity <= 0) return@withContext Resource.Error("Quantity must be positive")

            val initialStatus = OrderStatusEntry(
                status = OrderStatus.PENDING.name,
                changedAt = Date(),
                note = "Order placed at $pharmacyName"
            )

            val order = RefillOrder(
                userId = userId,
                medicineId = medicine.id,
                medicineName = medicine.name,
                medicineDosage = medicine.dosage,
                medicineUnit = medicine.unit,
                quantity = quantity,
                status = OrderStatus.PENDING,
                notes = notes.trim(),
                prescriptionId = medicine.prescriptionId,
                pharmacyId = pharmacyId,
                pharmacyName = pharmacyName,
                statusHistory = listOf(initialStatus)
            )

            val docRef = ordersCol.document()
            val data = order.toMap().toMutableMap()
            data["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
            docRef.set(data).await()

            Resource.Success(order.copy(id = docRef.id))
        } catch (e: Exception) {
            Log.e(TAG, "placeRefillOrderWithPharmacy error: ${e.message}")
            Resource.Error(e.message ?: "Failed to place order")
        }
    }

    // ─────────────── Order Status Management ───────────────

    /**
     * Update the status of an order.
     * Appends to the status history for audit trail.
     */
    suspend fun updateOrderStatus(
        orderId: String,
        newStatus: OrderStatus,
        note: String = ""
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val orderDoc = ordersCol.document(orderId).get().await()
            val order = orderDoc.data?.let { RefillOrder.fromMap(orderDoc.id, it) }
                ?: return@withContext Resource.Error("Order not found")

            val statusEntry = OrderStatusEntry(
                status = newStatus.name,
                changedAt = Date(),
                note = note
            )

            val updatedHistory = order.statusHistory + statusEntry

            val updates = mutableMapOf<String, Any?>(
                "status" to newStatus.name,
                "statusHistory" to updatedHistory.map { it.toMap() },
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            // If delivered, record delivery time and update medicine stock
            if (newStatus == OrderStatus.DELIVERED) {
                updates["deliveredAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
                // Refill the medicine stock
                restockMedicine(order.medicineId, order.quantity)
            }

            if (newStatus == OrderStatus.CANCELLED) {
                updates["cancelReason"] = note
            }

            ordersCol.document(orderId).update(updates).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateOrderStatus error: ${e.message}")
            Resource.Error(e.message ?: "Failed to update order")
        }
    }

    /**
     * Cancel an order. Only PENDING or CONFIRMED orders can be cancelled.
     */
    suspend fun cancelOrder(orderId: String, reason: String = ""): Resource<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val orderDoc = ordersCol.document(orderId).get().await()
                val order = orderDoc.data?.let { RefillOrder.fromMap(orderDoc.id, it) }
                    ?: return@withContext Resource.Error("Order not found")

                if (order.status != OrderStatus.PENDING && order.status != OrderStatus.CONFIRMED) {
                    return@withContext Resource.Error("Cannot cancel ${order.status.displayName()} order")
                }

                updateOrderStatus(orderId, OrderStatus.CANCELLED, reason.ifEmpty { "Cancelled by user" })
            } catch (e: Exception) {
                Log.e(TAG, "cancelOrder error: ${e.message}")
                Resource.Error(e.message ?: "Failed to cancel order")
            }
        }

    // ─────────────── Medicine Stock Management ───────────────

    /**
     * Decrement medicine stock by the number of doses taken.
     * Called when the user logs a medicine intake.
     */
    suspend fun decrementStock(medicineId: String, amount: Int = 1): Resource<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val docRef = medicinesCol.document(medicineId)
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(docRef)
                    val current = (snapshot.getLong("currentQuantity") ?: -1).toInt()

                    // Only decrement if tracking is enabled (current >= 0)
                    if (current > 0) {
                        val newQty = (current - amount).coerceAtLeast(0)
                        transaction.update(docRef, mapOf(
                            "currentQuantity" to newQty,
                            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        ))
                    }
                }.await()
                Resource.Success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "decrementStock error: ${e.message}")
                Resource.Error(e.message ?: "Failed to update stock")
            }
        }

    /**
     * Restock medicine after a refill delivery.
     */
    private suspend fun restockMedicine(medicineId: String, addQuantity: Int) {
        try {
            val docRef = medicinesCol.document(medicineId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val current = (snapshot.getLong("currentQuantity") ?: 0).toInt()
                val total = (snapshot.getLong("totalQuantity") ?: -1).toInt()

                val newQty = if (current < 0) addQuantity else current + addQuantity
                val updates = mutableMapOf<String, Any?>(
                    "currentQuantity" to newQty,
                    "lastRefillDate" to Date(),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                // Also update totalQuantity if it wasn't set
                if (total <= 0) {
                    updates["totalQuantity"] = addQuantity
                }
                transaction.update(docRef, updates)
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "restockMedicine error: ${e.message}")
        }
    }

    /**
     * Enable refill tracking for a medicine with initial quantity.
     */
    suspend fun enableRefillTracking(
        medicineId: String,
        currentQty: Int,
        totalQty: Int,
        lowThreshold: Int = 5,
        reminderEnabled: Boolean = true
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            medicinesCol.document(medicineId).update(mapOf(
                "currentQuantity" to currentQty,
                "totalQuantity" to totalQty,
                "lowStockThreshold" to lowThreshold,
                "refillReminderEnabled" to reminderEnabled,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "enableRefillTracking error: ${e.message}")
            Resource.Error(e.message ?: "Failed to enable tracking")
        }
    }

    /**
     * Disable refill tracking for a medicine.
     */
    suspend fun disableRefillTracking(medicineId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            try {
                medicinesCol.document(medicineId).update(mapOf(
                    "currentQuantity" to -1,
                    "totalQuantity" to -1,
                    "refillReminderEnabled" to false,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )).await()
                Resource.Success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "disableRefillTracking error: ${e.message}")
                Resource.Error(e.message ?: "Failed to disable tracking")
            }
        }

    /**
     * Get all medicines with low stock for the current user.
     */
    suspend fun getLowStockMedicines(): Resource<List<Medicine>> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            val snapshot = medicinesCol
                .whereEqualTo("userId", userId)
                .whereEqualTo("isActive", true)
                .limit(50)
                .get().await()

            val medicines = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Medicine::class.java)
            }.filter { it.isLowStock }

            Resource.Success(medicines)
        } catch (e: Exception) {
            Log.e(TAG, "getLowStockMedicines error: ${e.message}")
            Resource.Error(e.message ?: "Failed to check stock")
        }
    }

    /**
     * Update the status of an order and append to status history.
     * Handles cancel-specific logic (cancel reason).
     */
    suspend fun updateOrderStatus(
        orderId: String,
        newStatus: String,
        note: String = "",
        cancelReason: String? = null
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val orderRef = ordersCol.document(orderId)
            val orderSnap = orderRef.get().await()
            val existingOrder = orderSnap.data?.let { RefillOrder.fromMap(orderSnap.id, it) }
                ?: return@withContext Resource.Error("Order not found")

            val statusEntry = mapOf(
                "status" to newStatus,
                "changedAt" to Date(),
                "note" to note
            )
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "statusHistory" to com.google.firebase.firestore.FieldValue.arrayUnion(statusEntry)
            )
            if (newStatus == OrderStatus.CANCELLED.name) {
                updates["cancelReason"] = cancelReason ?: "Cancelled by pharmacy"
            }

            orderRef.update(updates).await()

            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateOrderStatus error: ${e.message}")
            Resource.Error(e.message ?: "Failed to update order status")
        }
    }

    /**
     * Get the existing order for cancellation refund purposes.
     */
    suspend fun getOrder(orderId: String): Resource<RefillOrder> = withContext(Dispatchers.IO) {
        try {
            val doc = ordersCol.document(orderId).get().await()
            val order = doc.data?.let { RefillOrder.fromMap(doc.id, it) }
                ?: return@withContext Resource.Error("Order not found")
            Resource.Success(order)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get order")
        }
    }
}

