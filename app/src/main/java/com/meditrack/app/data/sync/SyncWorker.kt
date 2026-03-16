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

/**
 * Helper class that syncs offline actions to Firestore when network is available.
 */
class SyncHelper(private val context: Context) {

    companion object {
        private const val TAG = "SyncHelper"
    }

    private val offlineQueueManager = OfflineQueueManager(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Sync all pending offline actions to Firestore
     */
    suspend fun syncPendingActions(): SyncResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting sync")

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

        Log.d(TAG, "Syncing ${pendingActions.size} pending actions")

        var successCount = 0
        var failureCount = 0

        for (action in pendingActions.sortedBy { it.timestamp }) {
            try {
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
                offlineQueueManager.dequeue(action.id)
                successCount++
                Log.d(TAG, "Successfully synced action: ${action.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync action ${action.id}: ${e.message}")
                offlineQueueManager.incrementRetryCount(action.id)
                failureCount++
            }
        }

        Log.d(TAG, "Sync completed: $successCount success, $failureCount failures")
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

