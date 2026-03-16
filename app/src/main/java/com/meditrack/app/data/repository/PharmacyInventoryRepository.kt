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
}

