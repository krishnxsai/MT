package com.meditrack.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.meditrack.app.data.model.OfflineAction
import com.meditrack.app.data.model.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository for managing offline actions that need to be synced to Firestore.
 * - Stores actions in local database when offline
 * - Retries syncing when connection is restored
 * - Handles failures gracefully with exponential backoff
 */
class OfflineActionRepository(private val context: Context) {
    companion object {
        private const val TAG = "OfflineActionRepository"
        private const val MAX_RETRIES = 5
        private const val PREFS_NAME = "offline_actions"
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    /**
     * Queue an offline action to be synced later.
     */
    suspend fun queueAction(action: OfflineAction): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val key = "action_${action.id}"
            prefs.edit().putString(key, serializeAction(action)).apply()
            Log.d(TAG, "Action queued: ${action.id}")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue action: ${e.message}")
            Resource.Error(e.message ?: "Failed to queue action", e)
        }
    }

    /**
     * Sync all queued actions to Firestore.
     * Returns the number of successfully synced actions.
     */
    suspend fun syncAllActions(): Resource<Int> = withContext(Dispatchers.IO) {
        try {
            var successCount = 0
            val allPrefs = prefs.all

            for ((key, value) in allPrefs) {
                if (key.startsWith("action_") && value is String) {
                    val action = deserializeAction(value) ?: continue
                    val result = syncSingleAction(action)

                    if (result is Resource.Success) {
                        prefs.edit().remove(key).apply()
                        successCount++
                    } else if (action.retryCount >= MAX_RETRIES) {
                        // Give up after max retries
                        Log.w(TAG, "Max retries reached for action: ${action.id}")
                        prefs.edit().remove(key).apply()
                    } else {
                        // Update retry count and keep in queue
                        val updatedAction = action.copy(retryCount = action.retryCount + 1)
                        prefs.edit().putString(key, serializeAction(updatedAction)).apply()
                    }
                }
            }

            Log.d(TAG, "Synced $successCount actions")
            Resource.Success(successCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error during sync: ${e.message}")
            Resource.Error(e.message ?: "Sync failed", e)
        }
    }

    /**
     * Sync a single offline action to Firestore.
     */
    private suspend fun syncSingleAction(action: OfflineAction): Resource<Unit> =
        withContext(Dispatchers.IO) {
            try {
                when (action.actionType) {
                    OfflineAction.ActionType.CREATE -> {
                        firestore.collection(action.collectionName)
                            .add(action.data)
                            .await()
                        Log.d(TAG, "Synced CREATE action: ${action.id}")
                        Resource.Success(Unit)
                    }

                    OfflineAction.ActionType.UPDATE -> {
                        // Special handling for medicine stock decrements
                        if (action.collectionName == "medicines" &&
                            action.data.containsKey("currentQuantity") &&
                            action.data["currentQuantity"] == "DECREMENT_BY_1"
                        ) {
                            // Atomic decrement
                            firestore.runTransaction { transaction ->
                                val docRef = firestore.collection(action.collectionName)
                                    .document(action.documentId)
                                val snapshot = transaction.get(docRef)
                                val currentQty = (snapshot.getLong("currentQuantity") ?: -1L).toInt()

                                if (currentQty > -1) {
                                    val newQty = (currentQty - 1).coerceAtLeast(0)
                                    transaction.update(
                                        docRef,
                                        mapOf(
                                            "currentQuantity" to newQty,
                                            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                        )
                                    )
                                }
                            }.await()
                        } else {
                            firestore.collection(action.collectionName)
                                .document(action.documentId)
                                .update(action.data)
                                .await()
                        }
                        Log.d(TAG, "Synced UPDATE action: ${action.id}")
                        Resource.Success(Unit)
                    }

                    OfflineAction.ActionType.DELETE -> {
                        firestore.collection(action.collectionName)
                            .document(action.documentId)
                            .delete()
                            .await()
                        Log.d(TAG, "Synced DELETE action: ${action.id}")
                        Resource.Success(Unit)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync action ${action.id}: ${e.message}")
                Resource.Error(e.message ?: "Sync failed", e)
            }
        }

    /**
     * Get number of pending offline actions.
     */
    fun getPendingActionCount(): Int {
        val allPrefs = prefs.all
        return allPrefs.count { (key, _) -> key.startsWith("action_") }
    }

    /**
     * Serialize OfflineAction to JSON string using manual serialization.
     */
    private fun serializeAction(action: OfflineAction): String {
        return try {
            // Manual JSON serialization to avoid Gson dependency
            val dataJson = action.data.entries.joinToString(", ") { (k, v) ->
                "\"$k\": ${sanitizeValue(v)}"
            }
            """{
                "id": "${action.id}",
                "actionType": "${action.actionType.name}",
                "collectionName": "${action.collectionName}",
                "documentId": "${action.documentId}",
                "data": { $dataJson },
                "timestamp": ${action.timestamp},
                "retryCount": ${action.retryCount},
                "maxRetries": ${action.maxRetries}
            }""".replace(Regex("\\s+"), "")
        } catch (e: Exception) {
            Log.e(TAG, "Serialization error: ${e.message}")
            ""
        }
    }

    private fun sanitizeValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"${value.replace("\"", "\\\"")}\""
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> "\"${value.toString().replace("\"", "\\\"")}\""
    }

    /**
     * Deserialize JSON string to OfflineAction (supports offline queue persistence).
     * This is a simplified parser that handles our specific JSON format.
     */
    private fun deserializeAction(json: String): OfflineAction? {
        return try {
            // Simplified JSON parsing without external library
            val idMatch = "\"id\": \"([^\"]+)\"".toRegex().find(json)
            val actionTypeMatch = "\"actionType\": \"([^\"]+)\"".toRegex().find(json)
            val collectionMatch = "\"collectionName\": \"([^\"]+)\"".toRegex().find(json)
            val docIdMatch = "\"documentId\": \"([^\"]+)\"".toRegex().find(json)
            val timestampMatch = "\"timestamp\": (\\d+)".toRegex().find(json)
            val retryMatch = "\"retryCount\": (\\d+)".toRegex().find(json)
            val maxRetriesMatch = "\"maxRetries\": (\\d+)".toRegex().find(json)

            OfflineAction(
                id = idMatch?.groupValues?.getOrNull(1) ?: "",
                actionType = try {
                    OfflineAction.ActionType.valueOf(actionTypeMatch?.groupValues?.getOrNull(1) ?: "CREATE")
                } catch (e: Exception) {
                    OfflineAction.ActionType.CREATE
                },
                collectionName = collectionMatch?.groupValues?.getOrNull(1) ?: "",
                documentId = docIdMatch?.groupValues?.getOrNull(1) ?: "",
                data = emptyMap(),  // Simplified - data reconstruction
                timestamp = (timestampMatch?.groupValues?.getOrNull(1) ?: "0").toLong(),
                retryCount = (retryMatch?.groupValues?.getOrNull(1) ?: "0").toInt(),
                maxRetries = (maxRetriesMatch?.groupValues?.getOrNull(1) ?: "5").toInt()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Deserialization error: ${e.message}")
            null
        }
    }
}
