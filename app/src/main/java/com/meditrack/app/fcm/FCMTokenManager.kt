package com.meditrack.app.fcm

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Manages FCM token registration and updates.
 * Call [registerToken] on user login/signup to ensure the token is saved.
 */
object FCMTokenManager {

    private const val TAG = "FCMTokenManager"

    /**
     * Registers the current FCM token for the authenticated user.
     * Should be called after successful login/signup.
     */
    suspend fun registerToken() {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val token = FirebaseMessaging.getInstance().token.await()

            Log.d(TAG, "Registering FCM token for user: $userId")

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .update("fcmToken", token)
                .await()

            Log.d(TAG, "FCM token registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register FCM token", e)
        }
    }

    /**
     * Gets the current FCM token.
     */
    suspend fun getToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get FCM token", e)
            null
        }
    }

    /**
     * Clears the FCM token for the current user (call on logout).
     */
    suspend fun clearToken() {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .update("fcmToken", "")
                .await()

            Log.d(TAG, "FCM token cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear FCM token", e)
        }
    }

    /**
     * Subscribes to a topic for broadcast notifications.
     * Example: subscribe to "order_updates" for all order-related notifications.
     */
    fun subscribeToTopic(topic: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
            .addOnSuccessListener {
                Log.d(TAG, "Subscribed to topic: $topic")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to subscribe to topic: $topic", e)
            }
    }

    /**
     * Unsubscribes from a topic.
     */
    fun unsubscribeFromTopic(topic: String) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            .addOnSuccessListener {
                Log.d(TAG, "Unsubscribed from topic: $topic")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to unsubscribe from topic: $topic", e)
            }
    }
}
