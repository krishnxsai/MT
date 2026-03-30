package com.meditrack.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.meditrack.app.data.model.OfflineAction
import com.meditrack.app.data.model.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Service to handle medicine intake actions (TAKE/SKIP) with offline support.
 * - TAKE: Decrements stock by 1, creates intake record, queues if offline
 * - SKIP: Creates intake record only, no stock change, queues if offline
 */
class MedicineIntakeService(
    private val context: Context,
    private val offlineRepository: OfflineActionRepository
) {
    companion object {
        private const val TAG = "MedicineIntakeService"
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    /**
     * Mark a medicine as TAKEN:
     * - Creates intake record with status=TAKEN
     * - Decrements currentQuantity by 1 (atomic transaction)
     * - Creates offline action queue if offline
     * - Returns callback for UI updates
     */
    suspend fun markAsTaken(
        medicineId: String,
        medicineName: String,
        reminderTime: String?,
        source: String = "notification_action_taken"
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")
            if (medicineId.isEmpty()) return@withContext Resource.Error("Invalid medicine ID")

            val intakeData = hashMapOf(
                "userId" to userId,
                "medicineId" to medicineId,
                "medicineName" to medicineName,
                "scheduledTime" to (reminderTime ?: ""),
                "taken" to true,
                "status" to "TAKEN",
                "takenAt" to FieldValue.serverTimestamp(),
                "source" to source
            )

            // Try to create intake record + decrement stock atomically
            try {
                firestore.runTransaction { transaction ->
                    // 1. Add intake record
                    val intakeRef = firestore.collection("medicineIntakes").document()
                    transaction.set(intakeRef, intakeData)

                    // 2. Decrement stock (if refill tracking is enabled)
                    val medRef = firestore.collection("medicines").document(medicineId)
                    val snapshot = transaction.get(medRef)
                    val currentQuantity = (snapshot.getLong("currentQuantity") ?: -1L).toInt()

                    if (currentQuantity > -1) {  // -1 means not tracked
                        val newQuantity = (currentQuantity - 1).coerceAtLeast(0)
                        transaction.update(medRef, mapOf(
                            "currentQuantity" to newQuantity,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ))
                        Log.d(TAG, "Stock decremented: $medicineId from $currentQuantity to $newQuantity")
                    }
                }.await()

                Log.d(TAG, "Medicine marked as taken: $medicineId")
                Resource.Success(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "Transaction failed, queueing offline action: ${e.message}")
                // Queue for later sync
                queueOfflineIntakeTaken(userId, medicineId, medicineName, reminderTime, intakeData)
                Resource.Success(Unit)  // User sees success even if offline
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error marking as taken: ${e.message}")
            Resource.Error(e.message ?: "Failed to mark as taken", e)
        }
    }

    /**
     * Mark a medicine as SKIPPED:
     * - Creates intake record with status=SKIPPED
     * - Does NOT change stock
     * - Creates offline action queue if offline
     */
    suspend fun markAsSkipped(
        medicineId: String,
        medicineName: String,
        reminderTime: String?,
        source: String = "notification_action_skipped"
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")
            if (medicineId.isEmpty()) return@withContext Resource.Error("Invalid medicine ID")

            val skipData = hashMapOf(
                "userId" to userId,
                "medicineId" to medicineId,
                "medicineName" to medicineName,
                "scheduledTime" to (reminderTime ?: ""),
                "taken" to false,
                "status" to "SKIPPED",
                "takenAt" to FieldValue.serverTimestamp(),
                "source" to source
            )

            // Try to create skip record (no stock change)
            try {
                firestore.collection("medicineIntakes")
                    .add(skipData)
                    .await()

                Log.d(TAG, "Medicine marked as skipped: $medicineId")
                Resource.Success(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "Skip record creation failed, queueing offline action: ${e.message}")
                // Queue for later sync (no stock change needed)
                queueOfflineIntakeSkipped(userId, medicineId, medicineName, reminderTime, skipData)
                Resource.Success(Unit)  // User sees success even if offline
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error marking as skipped: ${e.message}")
            Resource.Error(e.message ?: "Failed to mark as skipped", e)
        }
    }

    /**
     * Queue offline action for TAKE operation (includes stock decrement).
     */
    private suspend fun queueOfflineIntakeTaken(
        userId: String,
        medicineId: String,
        medicineName: String,
        reminderTime: String?,
        intakeData: Map<String, Any?>
    ) = withContext(Dispatchers.IO) {
        try {
            // Create compound action: intake + stock update
            val action = OfflineAction(
                actionType = OfflineAction.ActionType.CREATE,
                collectionName = "medicineIntakes",
                documentId = "",  // Will be auto-generated
                data = intakeData
            )
            offlineRepository.queueAction(action)

            // Also queue stock decrement
            val stockDecrement = OfflineAction(
                actionType = OfflineAction.ActionType.UPDATE,
                collectionName = "medicines",
                documentId = medicineId,
                data = mapOf(
                    "currentQuantity" to "DECREMENT_BY_1",  // Special marker
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            offlineRepository.queueAction(stockDecrement)

            Log.d(TAG, "Offline actions queued for taken: $medicineId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue offline actions: ${e.message}")
        }
    }

    /**
     * Queue offline action for SKIP operation (no stock change).
     */
    private suspend fun queueOfflineIntakeSkipped(
        userId: String,
        medicineId: String,
        medicineName: String,
        reminderTime: String?,
        skipData: Map<String, Any?>
    ) = withContext(Dispatchers.IO) {
        try {
            val action = OfflineAction(
                actionType = OfflineAction.ActionType.CREATE,
                collectionName = "medicineIntakes",
                documentId = "",
                data = skipData
            )
            offlineRepository.queueAction(action)

            Log.d(TAG, "Offline action queued for skipped: $medicineId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue offline action: ${e.message}")
        }
    }
}
