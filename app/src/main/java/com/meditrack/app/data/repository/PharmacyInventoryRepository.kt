package com.meditrack.app.data.repository

import android.util.Log
import com.meditrack.app.data.model.InventoryItem
import com.meditrack.app.data.model.Resource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository for pharmacy inventory management.
 * CRUD operations on the `pharmacyInventory` Firestore collection.
 */
class PharmacyInventoryRepository {

    companion object {
        private const val TAG = "PharmacyInventoryRepo"
    }

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val inventoryCol by lazy { firestore.collection("pharmacyInventory") }

    // ══════════════════════════════════════════════════════════════
    // Real-time inventory listing
    // ══════════════════════════════════════════════════════════════

    /**
     * Real-time flow of all active inventory items for a pharmacy.
     */
    fun getInventoryFlow(pharmacyId: String): Flow<Resource<List<InventoryItem>>> = callbackFlow {
        trySend(Resource.Loading)

        val listener = inventoryCol
            .whereEqualTo("pharmacyId", pharmacyId)
            .whereEqualTo("isActive", true)
            .orderBy("medicineName", Query.Direction.ASCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "getInventoryFlow error: ${error.message}")
                    trySend(Resource.Success(emptyList()))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { InventoryItem.fromMap(doc.id, it) }
                } ?: emptyList()
                trySend(Resource.Success(list))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get low-stock items for a pharmacy.
     */
    fun getLowStockItemsFlow(pharmacyId: String): Flow<Resource<List<InventoryItem>>> = callbackFlow {
        trySend(Resource.Loading)

        val listener = inventoryCol
            .whereEqualTo("pharmacyId", pharmacyId)
            .whereEqualTo("isActive", true)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Success(emptyList()))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { InventoryItem.fromMap(doc.id, it) }
                }?.filter { it.isLowStock } ?: emptyList()
                trySend(Resource.Success(list))
            }

        awaitClose { listener.remove() }
    }

    // ══════════════════════════════════════════════════════════════
    // CRUD Operations
    // ══════════════════════════════════════════════════════════════

    /**
     * Add a new inventory item.
     */
    suspend fun addItem(item: InventoryItem): Resource<InventoryItem> = withContext(Dispatchers.IO) {
        try {
            val docRef = inventoryCol.document()
            val data = item.toMap().toMutableMap()
            data["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
            docRef.set(data).await()
            Log.d(TAG, "Added inventory item: ${item.medicineName}")
            Resource.Success(item.copy(id = docRef.id))
        } catch (e: Exception) {
            Log.e(TAG, "addItem error: ${e.message}")
            Resource.Error(e.message ?: "Failed to add inventory item")
        }
    }

    /**
     * Update an existing inventory item.
     */
    suspend fun updateItem(item: InventoryItem): Resource<InventoryItem> = withContext(Dispatchers.IO) {
        try {
            inventoryCol.document(item.id).update(item.toMap()).await()
            Log.d(TAG, "Updated inventory item: ${item.id}")
            Resource.Success(item)
        } catch (e: Exception) {
            Log.e(TAG, "updateItem error: ${e.message}")
            Resource.Error(e.message ?: "Failed to update inventory item")
        }
    }

    /**
     * Update stock quantity for an item.
     */
    suspend fun updateStockQuantity(itemId: String, newQuantity: Int): Resource<Unit> =
        withContext(Dispatchers.IO) {
            try {
                inventoryCol.document(itemId).update(
                    mapOf(
                        "stockQuantity" to newQuantity,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                ).await()
                Resource.Success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "updateStockQuantity error: ${e.message}")
                Resource.Error(e.message ?: "Failed to update stock")
            }
        }

    /**
     * Soft-delete an inventory item (set isActive = false).
     */
    suspend fun deleteItem(itemId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            inventoryCol.document(itemId).update(
                mapOf(
                    "isActive" to false,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()
            Log.d(TAG, "Deleted inventory item: $itemId")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteItem error: ${e.message}")
            Resource.Error(e.message ?: "Failed to delete inventory item")
        }
    }

    /**
     * Search inventory items by medicine name.
     */
    suspend fun searchItems(pharmacyId: String, query: String): Resource<List<InventoryItem>> =
        withContext(Dispatchers.IO) {
            try {
                val snapshot = inventoryCol
                    .whereEqualTo("pharmacyId", pharmacyId)
                    .whereEqualTo("isActive", true)
                    .limit(50)
                    .get().await()

                val list = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { InventoryItem.fromMap(doc.id, it) }
                }.filter {
                    it.medicineName.contains(query, ignoreCase = true) ||
                            it.genericName.contains(query, ignoreCase = true)
                }
                Resource.Success(list)
            } catch (e: Exception) {
                Log.e(TAG, "searchItems error: ${e.message}")
                Resource.Error(e.message ?: "Failed to search inventory")
            }
        }

    /**
     * Get inventory count for a pharmacy.
     */
    suspend fun getInventoryCount(pharmacyId: String): Int = withContext(Dispatchers.IO) {
        try {
            val snapshot = inventoryCol
                .whereEqualTo("pharmacyId", pharmacyId)
                .whereEqualTo("isActive", true)
                .limit(500)
                .get().await()
            snapshot.size()
        } catch (e: Exception) {
            Log.e(TAG, "getInventoryCount error: ${e.message}")
            0
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Pharmacy Inventory Matching (Phase 6)
    // ══════════════════════════════════════════════════════════════

    /**
     * Check if a pharmacy has specific medicines in stock.
     * Returns a map of medicineName -> available quantity.
     *
     * @param pharmacyId The pharmacy to check
     * @param medicineNames List of medicine names to check (case-insensitive)
     * @return Map of medicine name -> InventoryItem if available
     */
    suspend fun checkMedicineAvailability(
        pharmacyId: String,
        medicineNames: List<String>
    ): Resource<Map<String, InventoryItem>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = inventoryCol
                .whereEqualTo("pharmacyId", pharmacyId)
                .whereEqualTo("isActive", true)
                .whereGreaterThan("stockQuantity", 0)
                .get().await()

            val items = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { InventoryItem.fromMap(doc.id, it) }
            }

            // Match by medicine name (case-insensitive)
            val matchedItems = mutableMapOf<String, InventoryItem>()
            for (name in medicineNames) {
                val item = items.find {
                    it.medicineName.equals(name, ignoreCase = true) ||
                    it.genericName.equals(name, ignoreCase = true)
                }
                if (item != null && item.stockQuantity > 0) {
                    matchedItems[name] = item
                }
            }

            Resource.Success(matchedItems)
        } catch (e: Exception) {
            Log.e(TAG, "checkMedicineAvailability error: ${e.message}")
            Resource.Error(e.message ?: "Failed to check inventory")
        }
    }

    /**
     * Get pharmacies that have ALL specified medicines in stock.
     *
     * @param pharmacyIds List of pharmacy IDs to check
     * @param medicineNames List of medicine names required
     * @return List of pharmacy IDs that have all medicines available
     */
    suspend fun getPharmaciesWithAllMedicines(
        pharmacyIds: List<String>,
        medicineNames: List<String>
    ): Resource<List<String>> = withContext(Dispatchers.IO) {
        try {
            val matchingPharmacies = mutableListOf<String>()

            for (pharmacyId in pharmacyIds) {
                val result = checkMedicineAvailability(pharmacyId, medicineNames)
                if (result is Resource.Success) {
                    val availableCount = result.data.size
                    if (availableCount == medicineNames.size) {
                        matchingPharmacies.add(pharmacyId)
                    }
                }
            }

            Log.d(TAG, "Found ${matchingPharmacies.size}/${pharmacyIds.size} pharmacies with all ${medicineNames.size} medicines")
            Resource.Success(matchingPharmacies)
        } catch (e: Exception) {
            Log.e(TAG, "getPharmaciesWithAllMedicines error: ${e.message}")
            Resource.Error(e.message ?: "Failed to filter pharmacies")
        }
    }

    /**
     * Get medicines available at a pharmacy with quantity check.
     * Used to calculate pricing and validate orders.
     *
     * @param pharmacyId Pharmacy to check
     * @param medicineNames Medicine names to get
     * @param quantities Required quantities for each medicine
     * @return Map of medicineName -> InventoryItem (only items with sufficient stock)
     */
    suspend fun getMedicinesWithSufficientStock(
        pharmacyId: String,
        medicineNames: List<String>,
        quantities: Map<String, Int>
    ): Resource<Map<String, InventoryItem>> = withContext(Dispatchers.IO) {
        try {
            val availability = checkMedicineAvailability(pharmacyId, medicineNames)
            if (availability is Resource.Error) {
                return@withContext availability
            }

            val items = (availability as Resource.Success).data
            val sufficientItems = items.filter { (name, item) ->
                val requiredQty = quantities[name] ?: 1
                item.stockQuantity >= requiredQty
            }

            Resource.Success(sufficientItems)
        } catch (e: Exception) {
            Log.e(TAG, "getMedicinesWithSufficientStock error: ${e.message}")
            Resource.Error(e.message ?: "Failed to check stock")
        }
    }
}

