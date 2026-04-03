package com.meditrack.app.data.repository

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.meditrack.app.data.model.*
import com.meditrack.app.service.DeliveryLocationService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import javax.inject.Inject

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
class OrderRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "OrderRepository"
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val ordersCol by lazy { firestore.collection("orders") }
    private val medicinesCol by lazy { firestore.collection("medicines") }
    private val inventoryCol by lazy { firestore.collection("pharmacyInventory") }
    private val cancellationRequestsCol by lazy { firestore.collection("cancellationRequests") }
    private val transactionsCol by lazy { firestore.collection("transactions") }

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

    /**
     * Place a multi-item order (unified ordering experience).
     * Supports pricing, delivery address, and multi-item carts.
     *
     * @param order The complete order with items, pharmacy info, and pricing
     * @return Order ID on success
     */
    suspend fun placeOrder(order: RefillOrder): Resource<String> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            if (order.items.isEmpty() && order.medicineId.isEmpty()) {
                return@withContext Resource.Error("Order must have at least one item")
            }

            if (order.pharmacyId.isEmpty()) {
                return@withContext Resource.Error("Pharmacy must be selected")
            }

            // ── PHASE 3: VALIDATE INVENTORY ──────────────────────────────────
            // Check stock availability for all items before confirming order
            val inventoryValidation = validateInventory(order)
            if (inventoryValidation !is Resource.Success) {
                val errorMsg = if (inventoryValidation is Resource.Error) inventoryValidation.message else "Inventory check failed"
                Log.w(TAG, "Inventory validation failed: $errorMsg")
                return@withContext inventoryValidation as Resource.Error
            }

            // Fetch pharmacy details to get location for map display
            var pharmacyLocationGeoPoint: com.google.firebase.firestore.GeoPoint? = null
            try {
                val pharmacyDoc = firestore.collection("pharmacies")
                    .document(order.pharmacyId).get().await()
                val lat = pharmacyDoc.getDouble("latitude")
                val lng = pharmacyDoc.getDouble("longitude")
                if (lat != null && lng != null) {
                    pharmacyLocationGeoPoint = com.google.firebase.firestore.GeoPoint(lat, lng)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch pharmacy location: ${e.message}")
            }

            val initialStatus = OrderStatusEntry(
                status = OrderStatus.PENDING.name,
                changedAt = Date(),
                note = "Order placed at ${order.pharmacyName}"
            )

            val orderWithUser = order.copy(
                userId = userId,
                status = OrderStatus.PENDING,
                statusHistory = listOf(initialStatus),
                pharmacyLocation = pharmacyLocationGeoPoint
            )

            val docRef = ordersCol.document()
            val data = orderWithUser.toMap().toMutableMap()
            data["orderId"] = docRef.id
            data["patientId"] = userId
            data["deliveryAddress"] = orderWithUser.deliveryAddress?.fullAddress ?: ""
            data["deliveryAddressDetails"] = orderWithUser.deliveryAddress?.toMap()
            data["deliveryLocation"] = orderWithUser.deliveryAddress?.toLocationMap()
            data["pharmacyLocation"] = pharmacyLocationGeoPoint
            data["estimatedDeliveryTime"] = orderWithUser.estimatedDeliveryMinutes
            data["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

            docRef.set(data).await()

            // Inventory mutations are handled server-side by Cloud Functions
            // when order status transitions are persisted.

            Log.d(TAG, "Order placed successfully: ${docRef.id}")
            Resource.Success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "placeOrder error: ${e.message}")
            Resource.Error(e.message ?: "Failed to place order")
        }
    }

    // ─────────────── PHASE 3: Inventory Validation ───────────────

    /**
     * Find inventory document by pharmacy and medicine name.
     * Searches normalized name first, then falls back to legacy exact medicineName.
     */
    private fun normalizeMedicineName(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
    }

    private suspend fun findInventoryDocId(
        pharmacyId: String,
        medicineName: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val normalizedName = normalizeMedicineName(medicineName)
            if (normalizedName.isNotEmpty()) {
                try {
                    val normalizedSnapshot = inventoryCol
                        .whereEqualTo("pharmacyId", pharmacyId)
                        .whereEqualTo("medicineNameNormalized", normalizedName)
                        .limit(1)
                        .get().await()

                    normalizedSnapshot.documents.firstOrNull()?.id?.let { return@withContext it }
                } catch (queryError: Exception) {
                    Log.w(TAG, "Normalized inventory lookup failed, falling back to legacy name query: ${queryError.message}")
                }
            }

            val legacyName = medicineName.trim()
            if (legacyName.isEmpty()) {
                return@withContext null
            }

            val snapshot = inventoryCol
                .whereEqualTo("pharmacyId", pharmacyId)
                .whereEqualTo("medicineName", legacyName)
                .limit(1)
                .get().await()

            snapshot.documents.firstOrNull()?.id
        } catch (e: Exception) {
            Log.w(TAG, "findInventoryDocId error for pharmacy=$pharmacyId, medicine=$medicineName: ${e.message}")
            null
        }
    }

    /**
     * Read stock from the canonical field while supporting legacy docs.
     */
    private fun getInventoryQuantity(invDoc: com.google.firebase.firestore.DocumentSnapshot): Int {
        val stockQuantity = invDoc.getLong("stockQuantity")
        if (stockQuantity != null) return stockQuantity.toInt()
        return (invDoc.getLong("quantity") ?: 0L).toInt()
    }

    /**
     * Apply stock delta with transactional safety to prevent race conditions.
     * Supports both stockQuantity and legacy quantity fields.
     */
    private suspend fun applyInventoryDelta(docId: String, delta: Long) {
        withContext(Dispatchers.IO) {
            try {
                val docRef = inventoryCol.document(docId)

                // Use transaction for atomic update
                firestore.runTransaction { transaction ->
                    val invDoc = transaction.get(docRef)

                    val updates = mutableMapOf<String, Any>(
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )

                    val hasStockQuantity = invDoc.get("stockQuantity") != null
                    val hasLegacyQuantity = invDoc.get("quantity") != null

                    // Update primary field
                    if (hasStockQuantity) {
                        val currentStock = (invDoc.getLong("stockQuantity") ?: 0L).toInt()
                        val newStock = (currentStock + delta).coerceAtLeast(0)
                        updates["stockQuantity"] = newStock
                    }

                    // Keep legacy field in sync
                    if (hasLegacyQuantity) {
                        val currentQty = (invDoc.getLong("quantity") ?: 0L).toInt()
                        val newQty = (currentQty + delta).coerceAtLeast(0)
                        updates["quantity"] = newQty
                    }

                    // If neither field exists, initialize canonical stock field
                    if (!hasStockQuantity && !hasLegacyQuantity) {
                        val newStock = (0 + delta).coerceAtLeast(0)
                        updates["stockQuantity"] = newStock
                    }

                    transaction.update(docRef, updates)
                }.await()

                Log.d(TAG, "Inventory delta applied: docId=$docId, delta=$delta")
            } catch (e: Exception) {
                Log.e(TAG, "applyInventoryDelta error: ${e.message}")
                throw e
            }
        }
    }

    /**
     * Validate that pharmacy has sufficient inventory for all order items.
     * Must pass before allowing order placement.
     *
      * Queries inventory by normalized medicine name first, with legacy exact-name fallback.
     *
     * @param order Order with items to validate
     * @return Success if all items in stock, Error otherwise
     */
    private suspend fun validateInventory(order: RefillOrder): Resource<Unit> {
        try {
            // If single-item order (legacy), validate that single item
            if (order.items.isEmpty() && order.medicineId.isNotEmpty()) {
                val docId = findInventoryDocId(order.pharmacyId, order.medicineName)
                    ?: return Resource.Error("${order.medicineName} not found in inventory")

                val invDoc = inventoryCol.document(docId).get().await()
                val inventory = getInventoryQuantity(invDoc)

                if (inventory < order.quantity) {
                    Log.w(TAG, "Insufficient stock for ${order.medicineName}: have $inventory, need ${order.quantity}")
                    return Resource.Error("${order.medicineName} unavailable (in stock: $inventory, requested: ${order.quantity})")
                }
                return Resource.Success(Unit)
            }

            // Multi-item order: validate each item
            for (item in order.items) {
                val docId = findInventoryDocId(order.pharmacyId, item.medicineName)
                    ?: return Resource.Error("${item.medicineName} not found in inventory")

                val invDoc = inventoryCol.document(docId).get().await()
                val inventory = getInventoryQuantity(invDoc)

                if (inventory < item.quantity) {
                    Log.w(TAG, "Insufficient stock for ${item.medicineName}: have $inventory, need ${item.quantity}")
                    return Resource.Error("${item.medicineName} unavailable (in stock: $inventory, requested: ${item.quantity})")
                }
            }

            Log.d(TAG, "Inventory validation passed for order: all items in stock")
            return Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "validateInventory error: ${e.message}")
            return Resource.Error(e.message ?: "Failed to validate inventory")
        }
    }

    /**
     * Reduce pharmacy inventory after order confirmation.
     * Called after order is created in Firestore.
     *
      * Finds inventory documents by normalized name (legacy fallback supported)
      * and applies stock reduction atomically within a transaction.
     *
     * @param order Order with items to reduce
     */
    private suspend fun reduceInventory(order: RefillOrder) {
        try {
            // If single-item order
            if (order.items.isEmpty() && order.medicineId.isNotEmpty()) {
                val docId = findInventoryDocId(order.pharmacyId, order.medicineName)
                if (docId != null) {
                    applyInventoryDelta(docId, -order.quantity.toLong())
                    Log.d(TAG, "Inventory reduced: -${order.quantity} units of ${order.medicineName} (docId=$docId)")
                } else {
                    Log.w(TAG, "Could not find inventory doc to reduce for ${order.medicineName}")
                }
                return
            }

            // Multi-item order
            for (item in order.items) {
                val docId = findInventoryDocId(order.pharmacyId, item.medicineName)
                if (docId != null) {
                    applyInventoryDelta(docId, -item.quantity.toLong())
                    Log.d(TAG, "Inventory reduced: -${item.quantity} units of ${item.medicineName} (docId=$docId)")
                } else {
                    Log.w(TAG, "Could not find inventory doc to reduce for ${item.medicineName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "reduceInventory error: ${e.message}")
            // Don't propagate error - order is already created
        }
    }

    // ─────────────── Order Status Management ───────────────

    /**
     * Update the status of an order.
     * Appends to the status history for audit trail.
     * Starts delivery tracking service when transitioning to SHIPPED.
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

            val deliveryContext = if (newStatus == OrderStatus.SHIPPED) {
                resolveDeliveryPersonContext()
            } else {
                null
            }

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

            if (newStatus == OrderStatus.SHIPPED) {
                updates["deliveryTrackingId"] = "ongoing_$orderId"
                updates["trackingStatus"] = "ACTIVE"
                updates["trackingStartedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
                deliveryContext?.let {
                    updates["deliveryPersonId"] = it.id
                    updates["deliveryPersonName"] = it.name
                    updates["deliveryPersonPhone"] = it.phone
                }
            }

            if (newStatus.isTerminal()) {
                updates["trackingStatus"] = "ENDED"
                updates["trackingEndedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
            }

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

            if (newStatus == OrderStatus.SHIPPED) {
                startDeliveryLocationTracking(orderId, deliveryContext)
            }

            if (newStatus.isTerminal()) {
                stopDeliveryLocationTracking(orderId)
                markTrackingDocumentInactive(orderId, newStatus)
            }

            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateOrderStatus error: ${e.message}")
            Resource.Error(e.message ?: "Failed to update order")
        }
    }

    private data class DeliveryPersonContext(
        val id: String,
        val name: String,
        val phone: String
    )

    private suspend fun resolveDeliveryPersonContext(): DeliveryPersonContext? {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w(TAG, "Cannot resolve delivery context: user not authenticated")
            return null
        }

        val deliveryPersonId = currentUser.uid
        val deliveryPersonName = currentUser.displayName ?: "Delivery Person"

        val deliveryPersonPhone = try {
            val userDoc = FirebaseFirestore.getInstance().collection("users")
                .document(deliveryPersonId)
                .get()
                .await()
            userDoc.getString("phoneNumber") ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch delivery person phone: ${e.message}")
            ""
        }

        return DeliveryPersonContext(
            id = deliveryPersonId,
            name = deliveryPersonName,
            phone = deliveryPersonPhone
        )
    }

    /**
     * Start delivery location tracking service when order transitions to SHIPPED.
     * The delivery person's location will be tracked in real-time and written to Firestore.
     */
    private suspend fun startDeliveryLocationTracking(
        orderId: String,
        deliveryContext: DeliveryPersonContext?
    ) {
        try {
            val contextData = deliveryContext ?: resolveDeliveryPersonContext()
            if (contextData == null) {
                Log.w(TAG, "Cannot start delivery tracking: missing delivery context")
                return
            }

            // Start the delivery location service
            val serviceIntent = Intent(context, DeliveryLocationService::class.java).apply {
                action = DeliveryLocationService.ACTION_ADD_TRACKING
                putExtra(DeliveryLocationService.EXTRA_ORDER_ID, orderId)
                putExtra(DeliveryLocationService.EXTRA_DELIVERY_PERSON_ID, contextData.id)
                putExtra(DeliveryLocationService.EXTRA_DELIVERY_PERSON_NAME, contextData.name)
                putExtra(DeliveryLocationService.EXTRA_DELIVERY_PERSON_PHONE, contextData.phone)
            }

            // Start the service using foreground service for Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            Log.d(TAG, "Delivery location tracking started for order: $orderId")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting delivery location tracking: ${e.message}")
        }
    }

    private fun stopDeliveryLocationTracking(orderId: String) {
        try {
            val serviceIntent = Intent(context, DeliveryLocationService::class.java).apply {
                action = DeliveryLocationService.ACTION_REMOVE_TRACKING
                putExtra(DeliveryLocationService.EXTRA_ORDER_ID, orderId)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            Log.d(TAG, "Requested stop for delivery tracking order: $orderId")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping delivery location tracking: ${e.message}")
        }
    }

    private suspend fun markTrackingDocumentInactive(orderId: String, terminalStatus: OrderStatus) {
        try {
            val trackingDoc = firestore.collection("deliveryTracking")
                .document("ongoing_$orderId")

            val snapshot = trackingDoc.get().await()
            if (!snapshot.exists()) {
                return
            }

            trackingDoc.update(
                mapOf(
                    "isActive" to false,
                    "trackingStatus" to terminalStatus.name,
                    "endedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "Could not mark tracking inactive for $orderId: ${e.message}")
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
    ): Resource<Unit> {
        val parsedStatus = try {
            OrderStatus.valueOf(newStatus)
        } catch (_: IllegalArgumentException) {
            return Resource.Error("Invalid order status: $newStatus")
        }

        val effectiveNote = when (parsedStatus) {
            OrderStatus.CANCELLED -> cancelReason?.takeIf { it.isNotBlank() }
                ?: note.ifBlank { "Cancelled by pharmacy" }
            else -> note
        }

        return updateOrderStatus(orderId, parsedStatus, effectiveNote)
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

    fun getOrderFlow(orderId: String): Flow<RefillOrder?> = callbackFlow {
        val listener = ordersCol.document(orderId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(null)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val order = snapshot.data?.let { RefillOrder.fromMap(snapshot.id, it) }
                trySend(order)
            }
        }
        awaitClose { listener.remove() }
    }

    /**
     * Get the pharmacy phone number for an order.
     * Used for calling pharmacy from order tracking.
     */
    suspend fun getPharmacyPhoneForOrder(orderId: String): Resource<String?> = withContext(Dispatchers.IO) {
        try {
            // Fetch the order to get pharmacyId
            val orderDoc = ordersCol.document(orderId).get().await()
            val order = orderDoc.data?.let { RefillOrder.fromMap(orderDoc.id, it) }
                ?: return@withContext Resource.Success(null)

            if (order.pharmacyId.isBlank()) {
                return@withContext Resource.Success(null)
            }

            // Fetch pharmacy details to get phone
            val pharmacyDoc = firestore.collection("pharmacies").document(order.pharmacyId).get().await()
            val phone = pharmacyDoc.getString("phone")
            Resource.Success(phone)
        } catch (e: Exception) {
            Log.e(TAG, "getPharmacyPhoneForOrder error: ${e.message}")
            Resource.Success(null) // Return null on error for graceful fallback
        }
    }

    // ─────────────── Payment Integration ───────────────

    /**
     * Record payment confirmation for an order.
     * Updates order status from PENDING to CONFIRMED.
     * This is called after successful Razorpay payment.
     *
     * @param orderId Order ID
     * @param razorpayPaymentId Razorpay payment ID
     * @param paymentMethod Payment method used (UPI, CARD, etc.)
     */
    suspend fun confirmPayment(
        orderId: String,
        razorpayPaymentId: String,
        paymentMethod: String = "UPI"
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val orderDoc = ordersCol.document(orderId).get().await()
            val order = orderDoc.data?.let { RefillOrder.fromMap(orderDoc.id, it) }
                ?: return@withContext Resource.Error("Order not found")

            if (order.status != OrderStatus.PENDING) {
                Log.w(TAG, "Order ${order.status} already processed, cannot confirm payment")
                return@withContext Resource.Error("Order is already ${order.status.displayName()}")
            }

            // Update order to CONFIRMED status
            updateOrderStatus(
                orderId,
                OrderStatus.CONFIRMED,
                "Payment confirmed via $paymentMethod (ID: $razorpayPaymentId)"
            )

            Log.d(TAG, "Order $orderId confirmed after payment")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "confirmPayment error: ${e.message}")
            Resource.Error(e.message ?: "Failed to confirm payment")
        }
    }

    /**
     * Cancel order and handle payment refund.
     * Does NOT process actual Razorpay refund (that's done by RazorpayRepository).
     * This just updates order status and records the refund request.
     *
     * @param orderId Order ID to cancel
     * @param reason Cancellation reason
     * @param razorpayPaymentId Razorpay payment ID (if payment was made)
     */
    suspend fun cancelOrderWithRefund(
        orderId: String,
        reason: String = "",
        razorpayPaymentId: String? = null
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val orderDoc = ordersCol.document(orderId).get().await()
            val order = orderDoc.data?.let { RefillOrder.fromMap(orderDoc.id, it) }
                ?: return@withContext Resource.Error("Order not found")

            // Can only cancel PENDING or CONFIRMED orders
            if (order.status !in listOf(OrderStatus.PENDING, OrderStatus.CONFIRMED)) {
                return@withContext Resource.Error("Cannot cancel ${order.status.displayName()} order")
            }

            // Update order status to CANCELLED
            updateOrderStatus(orderId, OrderStatus.CANCELLED, reason.ifEmpty { "Cancelled by user" })

            Log.d(TAG, "Order $orderId cancelled for refund: $reason")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "cancelOrderWithRefund error: ${e.message}")
            Resource.Error(e.message ?: "Failed to cancel order")
        }
    }

    /**
     * Rollback pharmacy inventory when order is cancelled.
     * Inverse operation of inventory reduction that happens on order confirmation.
     * Finds and updates the inventory document by pharmacyId + medicineName.
     *
     * @param pharmacyId Pharmacy ID
     * @param medicineName Medicine name
     * @param quantity Quantity to add back
     */
    private suspend fun rollbackInventory(
        pharmacyId: String,
        medicineName: String,
        quantity: Int
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val docId = findInventoryDocId(pharmacyId, medicineName)
                ?: return@withContext Resource.Error("Inventory item not found for rollback")

            applyInventoryDelta(docId, quantity.toLong())
            Log.d(TAG, "Inventory rollback: +$quantity of medicine $medicineName at pharmacy $pharmacyId")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "rollbackInventory error: ${e.message}")
            Resource.Error(e.message ?: "Failed to rollback inventory")
        }
    }

    // ─────────────── PHASE 4: Order Cancellation & Refunds ───────────────

    /**
     * Request order cancellation.
     * For PENDING/CONFIRMED orders: auto-approved and immediately cancelled
     * For SHIPPED+ orders: creates approval request for pharmacy
     *
     * @param orderId Order ID to cancel
     * @param reason Cancellation reason
     * @return Cancellation request ID
     */
    suspend fun requestCancellation(orderId: String, reason: String): Resource<String> =
        withContext(Dispatchers.IO) {
            try {
                val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")
                val orderDoc = ordersCol.document(orderId).get().await()
                val order = orderDoc.data?.let { RefillOrder.fromMap(orderDoc.id, it) }
                    ?: return@withContext Resource.Error("Order not found")

                // Can only cancel PENDING, CONFIRMED, or PREPARING orders
                if (order.status !in listOf(
                    OrderStatus.PENDING,
                    OrderStatus.CONFIRMED,
                    OrderStatus.PREPARING
                )) {
                    return@withContext Resource.Error("Cannot cancel ${order.status.displayName()} order")
                }

                // Create cancellation request
                val cancellationRequest = CancellationRequest(
                    userId = userId,
                    orderId = orderId,
                    orderStatus = order.status.name,
                    reason = reason,
                    requestStatus = if (order.status in listOf(
                        OrderStatus.PENDING,
                        OrderStatus.CONFIRMED
                    )) CancellationStatus.APPROVED else CancellationStatus.PENDING,
                    refundAmount = order.totalAmount,
                    razorpayPaymentId = ""  // Will fetch from transaction if exists
                )

                val docRef = cancellationRequestsCol.document()
                cancellationRequestsCol.document(docRef.id).set(cancellationRequest.toMap()).await()

                Log.d(TAG, "Cancellation request created: ${docRef.id}")

                // Auto-approve for PENDING/CONFIRMED orders
                if (cancellationRequest.requestStatus == CancellationStatus.APPROVED) {
                    processCancellation(orderId, docRef.id, userId)
                }

                Resource.Success(docRef.id)
            } catch (e: Exception) {
                Log.e(TAG, "requestCancellation error: ${e.message}")
                Resource.Error(e.message ?: "Failed to request cancellation")
            }
        }

    /**
     * Process order cancellation.
     * Handles inventory rollback and refund initiation.
     *
     * @param orderId Order ID
     * @param cancellationRequestId Cancellation request ID
     * @param approvedBy User ID who approved (default: system)
     */
    suspend fun processCancellation(
        orderId: String,
        cancellationRequestId: String,
        approvedBy: String = "system"
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val orderDoc = ordersCol.document(orderId).get().await()
            val order = orderDoc.data?.let { RefillOrder.fromMap(orderDoc.id, it) }
                ?: return@withContext Resource.Error("Order not found")

            // Inventory rollback is handled server-side on CANCELLED status transitions.

            // ── 1. Update cancellation request ────────────────────────
            cancellationRequestsCol.document(cancellationRequestId).update(mapOf(
                "requestStatus" to CancellationStatus.COMPLETED.name,
                "approvedBy" to approvedBy,
                "approvedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "inventoryRestored" to true,
                "restoredAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )).await()

            // ── 2. Update order status ────────────────────────────────
            updateOrderStatus(
                orderId,
                OrderStatus.CANCELLED,
                "Order cancelled (request: $cancellationRequestId)"
            )

            // ── 3. Initiate refund if payment was made ────────────────
            initiateRefund(orderId, order, cancellationRequestId)

            Log.d(TAG, "Order $orderId cancelled; inventory rollback handled server-side")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "processCancellation error: ${e.message}")
            Resource.Error(e.message ?: "Failed to process cancellation")
        }
    }

    /**
     * Initiate refund for cancelled order with payment.
     * Checks idempotency to prevent duplicate refunds.
     *
     * @param orderId Order ID
     * @param order Order object
     * @param cancellationRequestId Cancellation request ID
     */
    private suspend fun initiateRefund(
        orderId: String,
        order: RefillOrder,
        cancellationRequestId: String
    ) {
        try {
            // Only refund if amount > 0 and order was CONFIRMED+
            if (order.totalAmount <= 0.0 || order.status == OrderStatus.PENDING) {
                Log.d(TAG, "No refund needed for order $orderId (amount: ${order.totalAmount}, status: ${order.status})")
                return
            }

            // Get payment ID from transactions
            val transactionSnapshot = transactionsCol
                .whereEqualTo("orderId", orderId)
                .whereEqualTo("type", "PURCHASE")
                .limit(1)
                .get()
                .await()

            val razorpayPaymentId = transactionSnapshot.documents.firstOrNull()
                ?.getString("razorpayPaymentId") ?: ""

            if (razorpayPaymentId.isEmpty()) {
                Log.w(TAG, "No payment ID found for order $orderId")
                return
            }

            // Generate idempotency key to prevent duplicate refunds
            val idempotencyKey = generateIdempotencyKey(orderId)

            // Check if refund already exists for this order
            val existingRefund = transactionsCol
                .whereEqualTo("orderId", orderId)
                .whereEqualTo("type", "REFUND")
                .limit(1)
                .get()
                .await()

            if (existingRefund.documents.isNotEmpty()) {
                Log.d(TAG, "Refund already exists for order $orderId")
                return
            }

            // Create refund transaction record (marks refund as pending)
            val refundTransaction = OrderTransaction(
                userId = order.userId,
                orderId = orderId,
                type = TransactionType.REFUND,
                amount = order.totalAmount,
                currency = "INR",
                status = TransactionStatus.PENDING,
                pharmacyId = order.pharmacyId,
                pharmacyName = order.pharmacyName,
                razorpayPaymentId = razorpayPaymentId,
                notes = "Refund for cancelled order (request: $cancellationRequestId)"
            )

            transactionsCol.document().set(refundTransaction.toMap()).await()

            Log.d(TAG, "Refund transaction created for order $orderId (idempotency key: $idempotencyKey)")
        } catch (e: Exception) {
            Log.e(TAG, "initiateRefund error: ${e.message}")
            // Don't throw - refund initiation failure shouldn't block cancellation
        }
    }

    /**
     * Generate idempotency key for refund deduplication.
     * SHA256 hash of orderId to create unique key.
     *
     * @param orderId Order ID
     * @return Idempotency key
     */
    private fun generateIdempotencyKey(orderId: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(orderId.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to generate SHA256 key, using orderId: ${e.message}")
            orderId
        }
    }

    /**
     * Get cancellation request details.
     */
    suspend fun getCancellationRequest(requestId: String): Resource<CancellationRequest> =
        withContext(Dispatchers.IO) {
            try {
                val doc = cancellationRequestsCol.document(requestId).get().await()
                val request = doc.data?.let { CancellationRequest.fromMap(doc.id, it) }
                    ?: return@withContext Resource.Error("Cancellation request not found")
                Resource.Success(request)
            } catch (e: Exception) {
                Log.e(TAG, "getCancellationRequest error: ${e.message}")
                Resource.Error(e.message ?: "Failed to get cancellation request")
            }
        }

    /**
     * Get all cancellation requests for current user.
     */
    fun getCancellationRequestsFlow(): Flow<Resource<List<CancellationRequest>>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = cancellationRequestsCol
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "getCancellationRequestsFlow error: ${error.message}")
                    trySend(Resource.Success(emptyList()))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { CancellationRequest.fromMap(doc.id, it) }
                } ?: emptyList()
                trySend(Resource.Success(list))
            }

        awaitClose { listener.remove() }
    }
}

