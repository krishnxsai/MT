package com.meditrack.app.data.model

import java.util.Date
import java.util.UUID

/**
 * Represents an action taken while offline that needs to be synced.
 */
data class OfflineAction(
    val id: String = UUID.randomUUID().toString(),
    val actionType: ActionType,
    val collectionName: String,
    val documentId: String,
    val data: Map<String, Any?>,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val maxRetries: Int = 5
) {
    enum class ActionType {
        CREATE,
        UPDATE,
        DELETE
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "actionType" to actionType.name,
            "collectionName" to collectionName,
            "documentId" to documentId,
            "data" to data,
            "timestamp" to timestamp,
            "retryCount" to retryCount,
            "maxRetries" to maxRetries
        )
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): OfflineAction {
            return OfflineAction(
                id = map["id"] as? String ?: UUID.randomUUID().toString(),
                actionType = try {
                    ActionType.valueOf(map["actionType"] as? String ?: "CREATE")
                } catch (e: Exception) {
                    ActionType.CREATE
                },
                collectionName = map["collectionName"] as? String ?: "",
                documentId = map["documentId"] as? String ?: "",
                data = map["data"] as? Map<String, Any?> ?: emptyMap(),
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                retryCount = (map["retryCount"] as? Number)?.toInt() ?: 0,
                maxRetries = (map["maxRetries"] as? Number)?.toInt() ?: 5
            )
        }
    }
}

