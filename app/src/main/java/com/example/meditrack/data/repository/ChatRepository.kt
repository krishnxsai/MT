package com.example.meditrack.data.repository

import android.util.Log
import com.example.meditrack.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Repository for real-time doctor–patient chat.
 *
 * Key design decisions:
 *  • `conversations` holds metadata + unread counts — queried via `array-contains`
 *    on the `participantIds` field for efficient lookup.
 *  • `messages` is a separate top-level collection indexed on `conversationId` +
 *    `timestamp DESC` for pagination with `startAfter`.
 *  • Sending a message is a Firestore batch write (new message + conversation update)
 *    so the two documents are always consistent.
 *  • Read-receipt is a batch that marks all unread messages as read and resets the
 *    conversation's per-participant unread counter.
 */
class ChatRepository {

    companion object {
        private const val TAG = "ChatRepository"
        private const val PAGE_SIZE = 30L
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val conversationsCol by lazy { firestore.collection("conversations") }
    private val messagesCol by lazy { firestore.collection("messages") }
    private val usersCol by lazy { firestore.collection("users") }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // ─────────────────── Conversations ───────────────────

    /**
     * Real-time flow of all conversations the current user participates in,
     * ordered by last message time (most recent first).
     */
    fun getConversationsFlow(): Flow<Resource<List<Conversation>>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = conversationsCol
            .whereArrayContains("participantIds", userId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to load conversations"))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Conversation.fromMap(doc.id, it) }
                } ?: emptyList()
                trySend(Resource.Success(list))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get or create a conversation between the current user and [otherUserId].
     * Returns the existing conversation if one already exists.
     */
    suspend fun getOrCreateConversation(otherUserId: String): Resource<Conversation> =
        withContext(Dispatchers.IO) {
            try {
                val myId = currentUserId ?: return@withContext Resource.Error("Not logged in")

                // Check if conversation already exists
                val existing = conversationsCol
                    .whereArrayContains("participantIds", myId)
                    .get().await()
                    .documents
                    .mapNotNull { it.data?.let { d -> Conversation.fromMap(it.id, d) } }
                    .firstOrNull { it.participantIds.contains(otherUserId) }

                if (existing != null) {
                    return@withContext Resource.Success(existing)
                }

                // Fetch user data to populate names
                val myDoc = usersCol.document(myId).get().await()
                val otherDoc = usersCol.document(otherUserId).get().await()
                val myUser = myDoc.data?.let { User.fromMap(myId, it) }
                    ?: return@withContext Resource.Error("Current user not found")
                val otherUser = otherDoc.data?.let { User.fromMap(otherUserId, it) }
                    ?: return@withContext Resource.Error("Other user not found")

                // Determine who is doctor and who is patient
                val (doctorId, doctorName, doctorUrl) = if (myUser.role == UserRole.DOCTOR)
                    Triple(myId, myUser.displayName, myUser.profileImageUrl)
                else Triple(otherUserId, otherUser.displayName, otherUser.profileImageUrl)

                val (patientId, patientName, patientUrl) = if (myUser.role == UserRole.PATIENT)
                    Triple(myId, myUser.displayName, myUser.profileImageUrl)
                else Triple(otherUserId, otherUser.displayName, otherUser.profileImageUrl)

                val conv = Conversation(
                    doctorId = doctorId,
                    patientId = patientId,
                    doctorName = doctorName,
                    patientName = patientName,
                    doctorProfileUrl = doctorUrl,
                    patientProfileUrl = patientUrl,
                    participantIds = listOf(doctorId, patientId)
                )

                val docRef = conversationsCol.add(conv.toMap().plus("createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())).await()
                Resource.Success(conv.copy(id = docRef.id))

            } catch (e: Exception) {
                Log.e(TAG, "getOrCreateConversation error: ${e.message}")
                Resource.Error(e.message ?: "Failed to get or create conversation")
            }
        }

    // ─────────────────── Messages ───────────────────

    /**
     * Real-time flow of the latest messages in a conversation.
     * Use [loadMoreMessages] with the oldest DocumentSnapshot for pagination.
     */
    fun getMessagesFlow(conversationId: String): Flow<Resource<List<Message>>> = callbackFlow {
        trySend(Resource.Loading)

        val listener = messagesCol
            .whereEqualTo("conversationId", conversationId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to load messages"))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Message.fromMap(doc.id, it) }
                } ?: emptyList()
                trySend(Resource.Success(list))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Load the next page of older messages, starting after [lastDocument].
     */
    suspend fun loadMoreMessages(
        conversationId: String,
        lastDocument: DocumentSnapshot
    ): Resource<List<Message>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = messagesCol
                .whereEqualTo("conversationId", conversationId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .startAfter(lastDocument)
                .limit(PAGE_SIZE)
                .get().await()

            val list = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Message.fromMap(doc.id, it) }
            }
            Resource.Success(list)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load more messages")
        }
    }

    /**
     * Send a text message. Uses a batch write to atomically:
     *  1. Create the message document
     *  2. Update the conversation metadata (lastMessage, unread count)
     */
    suspend fun sendMessage(conversationId: String, text: String): Resource<Message> =
        withContext(Dispatchers.IO) {
            try {
                val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")
                val trimmed = text.trim()
                if (trimmed.isEmpty()) return@withContext Resource.Error("Message is empty")

                // Fetch sender info
                val userDoc = usersCol.document(userId).get().await()
                val userName = userDoc.getString("displayName") ?: "User"
                val userRole = userDoc.getString("role") ?: "PATIENT"

                // Fetch conversation to determine unread field
                val convDoc = conversationsCol.document(conversationId).get().await()
                val conv = convDoc.data?.let { Conversation.fromMap(convDoc.id, it) }
                    ?: return@withContext Resource.Error("Conversation not found")

                val msgRef = messagesCol.document()
                val msg = Message(
                    id = msgRef.id,
                    conversationId = conversationId,
                    senderId = userId,
                    senderName = userName,
                    senderRole = userRole,
                    text = trimmed
                )

                // Figure out which unread counter to increment
                val unreadField = if (userId == conv.doctorId) "unreadCountPatient" else "unreadCountDoctor"

                val batch = firestore.batch()
                batch.set(msgRef, msg.toMap())
                batch.update(
                    conversationsCol.document(conversationId),
                    mapOf(
                        "lastMessage" to trimmed,
                        "lastMessageSenderId" to userId,
                        "lastMessageTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        unreadField to com.google.firebase.firestore.FieldValue.increment(1),
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()

                Resource.Success(msg)
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage error: ${e.message}")
                Resource.Error(e.message ?: "Failed to send message")
            }
        }

    /**
     * Mark all unread messages in a conversation as read for the current user.
     * Resets the user's unread counter on the conversation document.
     */
    suspend fun markMessagesAsRead(conversationId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")

                // Query unread messages not sent by current user
                val unread = messagesCol
                    .whereEqualTo("conversationId", conversationId)
                    .whereEqualTo("isRead", false)
                    .get().await()
                    .documents
                    .filter { it.getString("senderId") != userId }

                if (unread.isEmpty()) {
                    // Still reset the counter in case of inconsistency
                    val convDoc = conversationsCol.document(conversationId).get().await()
                    val conv = convDoc.data?.let { Conversation.fromMap(convDoc.id, it) }
                    if (conv != null) {
                        val field = if (userId == conv.doctorId) "unreadCountDoctor" else "unreadCountPatient"
                        conversationsCol.document(conversationId).update(field, 0).await()
                    }
                    return@withContext Resource.Success(Unit)
                }

                val batch = firestore.batch()
                val now = Date()
                unread.forEach { doc ->
                    batch.update(doc.reference, mapOf("isRead" to true, "readAt" to now))
                }

                // Reset unread counter
                val convDoc = conversationsCol.document(conversationId).get().await()
                val conv = convDoc.data?.let { Conversation.fromMap(convDoc.id, it) }
                if (conv != null) {
                    val field = if (userId == conv.doctorId) "unreadCountDoctor" else "unreadCountPatient"
                    batch.update(conversationsCol.document(conversationId), field, 0)
                }

                batch.commit().await()
                Resource.Success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "markMessagesAsRead error: ${e.message}")
                Resource.Error(e.message ?: "Failed to mark messages as read")
            }
        }

    /**
     * Real-time total unread count across all conversations.
     */
    fun getTotalUnreadCountFlow(): Flow<Int> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            trySend(0)
            close()
            return@callbackFlow
        }

        val listener = conversationsCol
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snapshot, _ ->
                val total = snapshot?.documents?.sumOf { doc ->
                    val data = doc.data ?: return@sumOf 0
                    val conv = Conversation.fromMap(doc.id, data)
                    conv.getUnreadCount(userId)
                } ?: 0
                trySend(total)
            }

        awaitClose { listener.remove() }
    }
}

