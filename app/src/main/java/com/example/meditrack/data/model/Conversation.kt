package com.example.meditrack.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a one-to-one chat conversation between a doctor and a patient.
 * Stored in the top-level `conversations` Firestore collection.
 */
data class Conversation(
    @DocumentId
    val id: String = "",
    val doctorId: String = "",
    val patientId: String = "",
    val doctorName: String = "",
    val patientName: String = "",
    val doctorProfileUrl: String = "",
    val patientProfileUrl: String = "",

    /** Array of participant UIDs for efficient array-contains queries. */
    val participantIds: List<String> = emptyList(),

    /** Preview of the last message sent. */
    val lastMessage: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageTimestamp: Date? = null,

    /** Per-participant unread counters. */
    val unreadCountDoctor: Int = 0,
    val unreadCountPatient: Int = 0,

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    constructor() : this(id = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "doctorId" to doctorId,
        "patientId" to patientId,
        "doctorName" to doctorName,
        "patientName" to patientName,
        "doctorProfileUrl" to doctorProfileUrl,
        "patientProfileUrl" to patientProfileUrl,
        "participantIds" to participantIds,
        "lastMessage" to lastMessage,
        "lastMessageSenderId" to lastMessageSenderId,
        "lastMessageTimestamp" to lastMessageTimestamp,
        "unreadCountDoctor" to unreadCountDoctor,
        "unreadCountPatient" to unreadCountPatient,
        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )

    /**
     * Returns the display name of the other participant.
     */
    fun getOtherName(currentUserId: String): String =
        if (currentUserId == doctorId) patientName else doctorName

    /**
     * Returns the profile URL of the other participant.
     */
    fun getOtherProfileUrl(currentUserId: String): String =
        if (currentUserId == doctorId) patientProfileUrl else doctorProfileUrl

    /**
     * Returns the UID of the other participant.
     */
    fun getOtherId(currentUserId: String): String =
        if (currentUserId == doctorId) patientId else doctorId

    /**
     * Returns the unread count for the given user.
     */
    fun getUnreadCount(currentUserId: String): Int =
        if (currentUserId == doctorId) unreadCountDoctor else unreadCountPatient

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, map: Map<String, Any?>): Conversation = Conversation(
            id = id,
            doctorId = map["doctorId"] as? String ?: "",
            patientId = map["patientId"] as? String ?: "",
            doctorName = map["doctorName"] as? String ?: "",
            patientName = map["patientName"] as? String ?: "",
            doctorProfileUrl = map["doctorProfileUrl"] as? String ?: "",
            patientProfileUrl = map["patientProfileUrl"] as? String ?: "",
            participantIds = (map["participantIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            lastMessage = map["lastMessage"] as? String ?: "",
            lastMessageSenderId = map["lastMessageSenderId"] as? String ?: "",
            lastMessageTimestamp = (map["lastMessageTimestamp"] as? Timestamp)?.toDate(),
            unreadCountDoctor = (map["unreadCountDoctor"] as? Number)?.toInt() ?: 0,
            unreadCountPatient = (map["unreadCountPatient"] as? Number)?.toInt() ?: 0,
            createdAt = (map["createdAt"] as? Timestamp)?.toDate(),
            updatedAt = (map["updatedAt"] as? Timestamp)?.toDate()
        )
    }
}

