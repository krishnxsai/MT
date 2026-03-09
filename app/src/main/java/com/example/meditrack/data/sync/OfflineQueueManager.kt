package com.example.meditrack.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.meditrack.data.model.OfflineAction
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages a queue of actions taken while offline.
 * Uses shared preferences to store pending actions.
 */
class OfflineQueueManager(context: Context) {

    companion object {
        private const val TAG = "OfflineQueueManager"
        private const val PREFS_NAME = "meditrack_offline_queue"
        private const val KEY_QUEUE = "offline_actions_queue"
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Add an action to the offline queue
     */
    fun enqueue(action: OfflineAction) {
        synchronized(this) {
            val queue = getQueue().toMutableList()
            queue.add(action)
            saveQueue(queue)
            Log.d(TAG, "Enqueued action: ${action.actionType} for ${action.collectionName}/${action.documentId}")
        }
    }

    /**
     * Get all pending actions
     */
    fun getQueue(): List<OfflineAction> {
        val json = sharedPreferences.getString(KEY_QUEUE, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).mapNotNull { i ->
                try {
                    val obj = jsonArray.getJSONObject(i)
                    parseAction(obj)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse action at index $i: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get queue: ${e.message}")
            emptyList()
        }
    }

    /**
     * Remove an action from the queue after successful sync
     */
    fun dequeue(actionId: String) {
        synchronized(this) {
            val queue = getQueue().filter { it.id != actionId }
            saveQueue(queue)
            Log.d(TAG, "Dequeued action: $actionId")
        }
    }

    /**
     * Update retry count for a failed action
     */
    fun incrementRetryCount(actionId: String): OfflineAction? {
        synchronized(this) {
            val queue = getQueue().toMutableList()
            val index = queue.indexOfFirst { it.id == actionId }
            if (index >= 0) {
                val action = queue[index]
                val updatedAction = action.copy(retryCount = action.retryCount + 1)

                if (updatedAction.retryCount >= updatedAction.maxRetries) {
                    // Remove action that has exceeded max retries
                    queue.removeAt(index)
                    Log.w(TAG, "Action $actionId exceeded max retries, removing from queue")
                } else {
                    queue[index] = updatedAction
                }

                saveQueue(queue)
                return updatedAction
            }
            return null
        }
    }

    /**
     * Clear all pending actions
     */
    fun clearQueue() {
        synchronized(this) {
            sharedPreferences.edit().remove(KEY_QUEUE).apply()
            Log.d(TAG, "Queue cleared")
        }
    }

    /**
     * Get number of pending actions
     */
    fun getPendingCount(): Int = getQueue().size

    /**
     * Check if there are any pending actions
     */
    fun hasPendingActions(): Boolean = getPendingCount() > 0

    private fun saveQueue(queue: List<OfflineAction>) {
        val jsonArray = JSONArray()
        queue.forEach { action ->
            jsonArray.put(actionToJson(action))
        }
        sharedPreferences.edit().putString(KEY_QUEUE, jsonArray.toString()).apply()
    }

    private fun actionToJson(action: OfflineAction): JSONObject {
        return JSONObject().apply {
            put("id", action.id)
            put("actionType", action.actionType.name)
            put("collectionName", action.collectionName)
            put("documentId", action.documentId)
            @Suppress("UNCHECKED_CAST")
            put("data", JSONObject(action.data.mapValues { (_, v) ->
                when (v) {
                    is Map<*, *> -> JSONObject(v as Map<String, Any?>)
                    is List<*> -> JSONArray(v)
                    else -> v
                }
            }))
            put("timestamp", action.timestamp)
            put("retryCount", action.retryCount)
            put("maxRetries", action.maxRetries)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseAction(json: JSONObject): OfflineAction {
        val dataJson = json.optJSONObject("data") ?: JSONObject()
        val data = mutableMapOf<String, Any?>()

        dataJson.keys().forEach { key ->
            data[key] = when (val value = dataJson.get(key)) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }

        return OfflineAction(
            id = json.getString("id"),
            actionType = OfflineAction.ActionType.valueOf(json.getString("actionType")),
            collectionName = json.getString("collectionName"),
            documentId = json.getString("documentId"),
            data = data,
            timestamp = json.getLong("timestamp"),
            retryCount = json.optInt("retryCount", 0),
            maxRetries = json.optInt("maxRetries", 5)
        )
    }

    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            map[key] = when (val value = json.get(key)) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }

    private fun jsonArrayToList(json: JSONArray): List<Any?> {
        return (0 until json.length()).map { i ->
            when (val value = json.get(i)) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
    }
}

