package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a single chat message in a conversation.
 * Stored in the top-level `messages` Firestore collection.
 */
data class Message(
    @DocumentId
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = "", // "DOCTOR" or "PATIENT"
    val text: String = "",
    val type: MessageType = MessageType.TEXT,

    /** Read receipt tracking. */
    val isRead: Boolean = false,
    val readAt: Date? = null,

    @ServerTimestamp
    val timestamp: Date? = null
) {
    constructor() : this(id = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "conversationId" to conversationId,
        "senderId" to senderId,
        "senderName" to senderName,
        "senderRole" to senderRole,
        "text" to text,
        "type" to type.name,
        "isRead" to isRead,
        "readAt" to readAt,
        "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Message = Message(
            id = id,
            conversationId = map["conversationId"] as? String ?: "",
            senderId = map["senderId"] as? String ?: "",
            senderName = map["senderName"] as? String ?: "",
            senderRole = map["senderRole"] as? String ?: "",
            text = map["text"] as? String ?: "",
            type = try {
                MessageType.valueOf(map["type"] as? String ?: "TEXT")
            } catch (_: Exception) { MessageType.TEXT },
            isRead = map["isRead"] as? Boolean ?: false,
            readAt = (map["readAt"] as? Timestamp)?.toDate(),
            timestamp = (map["timestamp"] as? Timestamp)?.toDate()
        )
    }
}

enum class MessageType {
    TEXT,
    SYSTEM    // For auto-generated messages (e.g., appointment confirmations)
}

