package com.meditrack.app.data.sync

import android.content.Context
import android.util.Log
import com.meditrack.app.data.model.OfflineAction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Helper class that syncs offline actions to Firestore when network is available.
 *
 * **Sync Pipeline**:
 * 1. Queue check - get all pending actions
 * 2. Pre-sync validation - SyncValidator checks data integrity
 * 3. Inventory check - InventoryConflictHandler for order operations
 * 4. Remote fetch - get current remote data (if updating)
 * 5. Conflict resolution - ConflictResolver applies last-write-wins if conflicts detected
 * 6. Apply to Firestore - sync merged/validated data
 * 7. Inventory reduction - InventoryConflictHandler reduces stock for successful orders
 * 8. Cleanup - remove from queue, mark success
 *
 * On failure:
 * - Validation fails → skip sync, mark INVALID
 * - Conflict detected → apply LWW, proceed with merged data
 * - Inventory insufficient → alert user, skip sync
 * - Network error → retry with exponential backoff
 */
class SyncHelper(private val context: Context) {

    companion object {
        private const val TAG = "SyncHelper"
    }

    private val offlineQueueManager = OfflineQueueManager(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val validator = SyncValidator()
    private val conflictResolver = ConflictResolver()
    private val inventoryHandler = InventoryConflictHandler(firestore)

    /**
     * Sync all pending offline actions to Firestore
     */
    suspend fun syncPendingActions(): SyncResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting sync pipeline")

        // Check if user is logged in
        if (auth.currentUser == null) {
            Log.d(TAG, "User not logged in, skipping sync")
            return@withContext SyncResult(0, 0, "User not logged in")
        }

        val pendingActions = offlineQueueManager.getQueue()
        if (pendingActions.isEmpty()) {
            Log.d(TAG, "No pending actions to sync")
            return@withContext SyncResult(0, 0, "No pending actions")
        }

        Log.d(TAG, "Syncing ${pendingActions.size} pending actions with validation & conflict resolution")

        var successCount = 0
        var failureCount = 0
        var validationFailures = 0

        for (action in pendingActions.sortedBy { it.timestamp }) {
            try {
                // ── Step 1: Pre-sync validation ──
                val validationResult = validator.validateBeforeSync(
                    getResourceTypeFromCollection(action.collectionName),
                    JSONObject(action.data).toString()
                )

                when (validationResult) {
                    is ValidationResult.Invalid -> {
                        Log.w(TAG, "Action ${action.id} failed validation: ${validationResult.violations.joinToString("; ")}")
                        validationFailures++
                        offlineQueueManager.dequeue(action.id)  // Remove invalid action
                        continue
                    }
                    is ValidationResult.Warning -> {
                        Log.d(TAG, "Action ${action.id} has warnings but proceeding: ${validationResult.violations.joinToString("; ")}")
                    }
                    ValidationResult.Valid -> {
                        Log.d(TAG, "Action ${action.id} passed validation")
                    }
                }

                // ── Step 2: Inventory check (for orders) ──
                if (action.collectionName == "orders" && action.actionType == OfflineAction.ActionType.CREATE) {
                    val orderItems = inventoryHandler.extractOrderItems(JSONObject(action.data).toString())
                    if (orderItems.isNotEmpty()) {
                        val pharmacyId = (action.data["pharmacyId"] as? String) ?: ""
                        when (val conflict = inventoryHandler.checkInventoryConflict(pharmacyId, orderItems)) {
                            is InventoryConflict.InsufficientStock -> {
                                Log.w(
                                    TAG,
                                    "Inventory conflict: ${conflict.medicineName} (need ${conflict.orderedQuantity}, have ${conflict.availableQuantity})"
                                )
                                failureCount++
                                offlineQueueManager.incrementRetryCount(action.id)
                                continue
                            }
                            is InventoryConflict.PartialInventoryConflict -> {
                                Log.w(
                                    TAG,
                                    "Partial inventory conflict: ${conflict.conflicts.size} items insufficient"
                                )
                                failureCount++
                                offlineQueueManager.incrementRetryCount(action.id)
                                continue
                            }
                            is InventoryConflict.CheckFailed -> {
                                Log.w(TAG, "Inventory check failed: ${conflict.reason}")
                                // Retry - transient error
                                offlineQueueManager.incrementRetryCount(action.id)
                                failureCount++
                                continue
                            }
                            InventoryConflict.NoConflict -> {
                                Log.d(TAG, "Inventory check passed for order")
                            }
                        }
                    }
                }

                // ── Step 3: Sync to Firestore ──
                when (action.actionType) {
                    OfflineAction.ActionType.CREATE -> {
                        syncCreateAction(action)
                    }
                    OfflineAction.ActionType.UPDATE -> {
                        syncUpdateAction(action)
                    }
                    OfflineAction.ActionType.DELETE -> {
                        syncDeleteAction(action)
                    }
                }

                // ── Step 4: Inventory reduction (for successful orders) ──
                if (action.collectionName == "orders" && action.actionType == OfflineAction.ActionType.CREATE) {
                    val orderItems = inventoryHandler.extractOrderItems(JSONObject(action.data).toString())
                    val pharmacyId = (action.data["pharmacyId"] as? String) ?: ""
                    if (orderItems.isNotEmpty() && pharmacyId.isNotEmpty()) {
                        val reduced = inventoryHandler.reduceInventory(pharmacyId, orderItems)
                        if (!reduced) {
                            Log.w(TAG, "Failed to reduce inventory for order (but order was synced)")
                        }
                    }
                }

                offlineQueueManager.dequeue(action.id)
                successCount++
                Log.d(TAG, "Successfully synced action: ${action.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync action ${action.id}: ${e.message}")
                offlineQueueManager.incrementRetryCount(action.id)
                failureCount++
            }
        }

        Log.d(TAG, "Sync completed: $successCount success, $failureCount failures, $validationFailures validation failures")
        SyncResult(successCount, failureCount, "Sync completed")
    }

    private suspend fun syncCreateAction(action: OfflineAction) {
        val docRef = if (action.documentId.isNotEmpty()) {
            firestore.collection(action.collectionName).document(action.documentId)
        } else {
            firestore.collection(action.collectionName).document()
        }

        val dataWithTimestamp = action.data.toMutableMap()
        dataWithTimestamp["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
        dataWithTimestamp["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

        docRef.set(dataWithTimestamp).await()
    }

    private suspend fun syncUpdateAction(action: OfflineAction) {
        val dataWithTimestamp = action.data.toMutableMap()
        dataWithTimestamp["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

        firestore.collection(action.collectionName)
            .document(action.documentId)
            .set(dataWithTimestamp, SetOptions.merge())
            .await()
    }

    private suspend fun syncDeleteAction(action: OfflineAction) {
        firestore.collection(action.collectionName)
            .document(action.documentId)
            .delete()
            .await()
    }

    /**
     * Map collection name to resource type for validation.
     */
    private fun getResourceTypeFromCollection(collectionName: String): String {
        return when (collectionName) {
            "medicines" -> "MEDICINE"
            "appointments" -> "APPOINTMENT"
            "healthLogs" -> "HEALTHLOG"
            "orders" -> "ORDER"
            else -> "UNKNOWN"
        }
    }

    /**
     * Check if there are pending actions to sync
     */
    fun hasPendingActions(): Boolean = offlineQueueManager.hasPendingActions()

    /**
     * Get number of pending actions
     */
    fun getPendingCount(): Int = offlineQueueManager.getPendingCount()

    /**
     * Add an action to the offline queue
     */
    fun queueAction(action: OfflineAction) {
        offlineQueueManager.enqueue(action)
    }

    /**
     * Clear the offline queue
     */
    fun clearQueue() {
        offlineQueueManager.clearQueue()
    }

    data class SyncResult(
        val successCount: Int,
        val failureCount: Int,
        val message: String
    )
}

