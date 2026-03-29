package com.meditrack.app.data.sync

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Inventory-specific conflict handler for order/pharmacy operations.
 *
 * **Purpose**: Prevent inventory inconsistencies by validating order quantities
 * against available pharmacy stock before syncing to Firestore.
 *
 * **Scenario**:
 * - Patient creates order for 100 pills while offline
 * - Meanwhile, inventory drops from 150 → 50 (sold to other patients)
 * - When sync happens, check if 100 pills available before confirming order
 * - If not, alert user and rollback order
 *
 * **Strategy**:
 * - Check pharmacy inventory before syncing order
 * - Validate total order amount against stock levels
 * - Prevent overselling (orders > stock)
 * - Log conflicts for audit trail
 */
sealed class InventoryConflict {
    /**
     * Inventory check passed - safe to sync.
     */
    object NoConflict : InventoryConflict()

    /**
     * Insufficient inventory - order exceeds available stock.
     */
    data class InsufficientStock(
        val orderedQuantity: Int,
        val availableQuantity: Int,
        val medicineId: String,
        val medicineName: String
    ) : InventoryConflict()

    /**
     * Multiple items with conflicts - partial failure scenario.
     */
    data class PartialInventoryConflict(
        val conflicts: List<InsufficientStock>,
        val successfulItems: List<String>
    ) : InventoryConflict()

    /**
     * Inventory check failed - couldn't verify stock (network error, etc).
     */
    data class CheckFailed(
        val reason: String
    ) : InventoryConflict()
}

/**
 * Handler for detect and manage inventory-related conflicts during sync.
 * Integrates with order processing to prevent overselling.
 */
class InventoryConflictHandler(private val firestore: FirebaseFirestore) {

    companion object {
        private const val TAG = "InventoryConflictHandler"
        private const val PHARMACY_COLLECTION = "pharmacies"
        private const val INVENTORY_FIELD = "inventory"
    }

    /**
     * Check if order can be fulfilled based on current pharmacy inventory.
     *
     * @param pharmacyId Pharmacy ID handling the order
     * @param orderItems List of order items with medicineId, medicineName, quantity
     * @return InventoryConflict result (NoConflict if OK, InsufficientStock if issues)
     */
    suspend fun checkInventoryConflict(
        pharmacyId: String,
        orderItems: List<OrderItem>
    ): InventoryConflict {
        return try {
            Log.d(TAG, "Checking inventory for pharmacy $pharmacyId with ${orderItems.size} items")

            // Fetch current pharmacy inventory from Firestore
            val pharmacyDoc = firestore
                .collection(PHARMACY_COLLECTION)
                .document(pharmacyId)
                .get()
                .await()

            if (!pharmacyDoc.exists()) {
                Log.w(TAG, "Pharmacy $pharmacyId not found")
                return InventoryConflict.CheckFailed("Pharmacy not found")
            }

            @Suppress("UNCHECKED_CAST")
            val inventory = (pharmacyDoc.get(INVENTORY_FIELD) as? Map<String, Any>) ?: emptyMap()

            val conflicts = mutableListOf<InventoryConflict.InsufficientStock>()
            val successfulItems = mutableListOf<String>()

            for (item in orderItems) {
                val availableQuantity = (inventory[item.medicineId] as? Number)?.toInt() ?: 0

                if (item.quantity > availableQuantity) {
                    Log.w(
                        TAG,
                        "Inventory conflict: ${item.medicineName} ordered ${item.quantity}, available $availableQuantity"
                    )
                    conflicts.add(
                        InventoryConflict.InsufficientStock(
                            orderedQuantity = item.quantity,
                            availableQuantity = availableQuantity,
                            medicineId = item.medicineId,
                            medicineName = item.medicineName
                        )
                    )
                } else {
                    Log.d(TAG, "Inventory OK: ${item.medicineName} (have $availableQuantity, need ${item.quantity})")
                    successfulItems.add(item.medicineId)
                }
            }

            return when {
                conflicts.isEmpty() -> {
                    Log.d(TAG, "Inventory check passed for all items")
                    InventoryConflict.NoConflict
                }
                successfulItems.isNotEmpty() && conflicts.size < orderItems.size -> {
                    // Partial success - some items can be fulfilled
                    Log.w(TAG, "Partial inventory conflict: ${conflicts.size} items insufficient, ${successfulItems.size} OK")
                    InventoryConflict.PartialInventoryConflict(conflicts, successfulItems)
                }
                else -> {
                    // Complete failure - first conflict only (user sees most critical)
                    conflicts.first()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking inventory conflict", e)
            InventoryConflict.CheckFailed(e.message ?: "Unknown error")
        }
    }

    /**
     * Extract order items from order data JSON.
     * Handles both nested order structure and flat item list.
     *
     * @param orderDataJson JSON string containing order data
     * @return List of OrderItem with medicineId, quantity, name
     */
    fun extractOrderItems(orderDataJson: String): List<OrderItem> {
        return try {
            val order = JSONObject(orderDataJson)

            // Try to parse items array
            val items = mutableListOf<OrderItem>()

            if (order.has("items")) {
                val itemsArray = order.getJSONArray("items")
                for (i in 0 until itemsArray.length()) {
                    val item = itemsArray.getJSONObject(i)
                    items.add(
                        OrderItem(
                            medicineId = item.optString("medicineId", ""),
                            medicineName = item.optString("medicineName", "Unknown"),
                            quantity = item.optInt("quantity", 0)
                        )
                    )
                }
            }

            Log.d(TAG, "Extracted ${items.size} order items")
            items
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting order items", e)
            emptyList()
        }
    }

    /**
     * Reduce pharmacy inventory after successful order sync.
     * Called AFTER order is confirmed in Firestore to update stock levels.
     *
     * @param pharmacyId Pharmacy ID
     * @param orderItems Items to reduce inventory for
     */
    suspend fun reduceInventory(
        pharmacyId: String,
        orderItems: List<OrderItem>
    ): Boolean {
        return try {
            Log.d(TAG, "Reducing inventory for pharmacy $pharmacyId")

            val updates = mutableMapOf<String, Any>()
            for (item in orderItems) {
                updates[INVENTORY_FIELD + ".${item.medicineId}"] = com.google.firebase.firestore.FieldValue.increment(-item.quantity.toLong())
            }

            firestore
                .collection(PHARMACY_COLLECTION)
                .document(pharmacyId)
                .update(updates)
                .await()

            Log.d(TAG, "Inventory reduced successfully for ${orderItems.size} items")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error reducing inventory", e)
            false
        }
    }

    /**
     * Restore inventory if order sync fails (rollback for failed orders).
     * Called when order creation/payment fails to revert inventory changes.
     *
     * @param pharmacyId Pharmacy ID
     * @param orderItems Items to restore inventory for
     */
    suspend fun restoreInventory(
        pharmacyId: String,
        orderItems: List<OrderItem>
    ): Boolean {
        return try {
            Log.d(TAG, "Restoring inventory for failed order (pharmacy $pharmacyId)")

            val updates = mutableMapOf<String, Any>()
            for (item in orderItems) {
                updates[INVENTORY_FIELD + ".${item.medicineId}"] = com.google.firebase.firestore.FieldValue.increment(item.quantity.toLong())
            }

            firestore
                .collection(PHARMACY_COLLECTION)
                .document(pharmacyId)
                .update(updates)
                .await()

            Log.d(TAG, "Inventory restored successfully for ${orderItems.size} items")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring inventory", e)
            false
        }
    }
}

/**
 * Data class representing a single order item with inventory details.
 */
data class OrderItem(
    val medicineId: String,
    val medicineName: String,
    val quantity: Int
)
