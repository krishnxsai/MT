package com.meditrack.app.fcm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.meditrack.app.R
import com.meditrack.app.data.model.NotificationType
import com.meditrack.app.data.repository.NotificationPreferenceRepository
import com.meditrack.app.ui.order.OrderTrackingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging service for handling push notifications.
 * Receives notifications when order status changes occur.
 */
class MediTrackFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MediTrackFCM"
        private const val CHANNEL_ID = "meditrack_orders"
        private const val NOTIFICATION_ID_BASE = 6000
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")

        // Save token to Firestore for authenticated user
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            saveFcmToken(userId, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received from: ${message.from}")

        // Handle data payload (for order status updates)
        message.data.isNotEmpty().let {
            val type = message.data["type"]
            when (type) {
                "order_status" -> handleOrderStatusNotification(message.data)
                else -> handleGenericNotification(message)
            }
        }

        // Handle notification payload (if sent from Firebase Console)
        message.notification?.let { notification ->
            showNotification(
                title = notification.title ?: "MediTrack",
                body = notification.body ?: "",
                data = message.data
            )
        }
    }

    private fun handleOrderStatusNotification(data: Map<String, String>) {
        val orderId = data["orderId"] ?: return
        val status = data["status"] ?: return
        val pharmacyName = data["pharmacyName"] ?: "Pharmacy"

        val (title, body) = when (status) {
            "CONFIRMED" -> "Order Accepted" to "Your order from $pharmacyName has been accepted and is being processed."
            "PREPARING" -> "Order Preparing" to "$pharmacyName is preparing your medicine."
            "READY" -> "Order Ready" to "Your medicine is ready for pickup at $pharmacyName!"
            "SHIPPED" -> "Out for Delivery" to "Your order from $pharmacyName is on the way!"
            "DELIVERED" -> "Order Delivered" to "Your order from $pharmacyName has been delivered. Stay healthy!"
            "CANCELLED" -> "Order Cancelled" to "Your order from $pharmacyName has been cancelled."
            else -> return
        }

        // Check notification preferences before showing notification
        val preferenceRepo = NotificationPreferenceRepository()
        CoroutineScope(Dispatchers.IO).launch {
            val isAllowed = preferenceRepo.isNotificationAllowed(NotificationType.ORDER_STATUS)
            if (isAllowed) {
                showOrderNotification(orderId, title, body)
                Log.d(TAG, "Order notification shown: $status (preferences allowed)")
            } else {
                Log.d(TAG, "Order notification suppressed: $status (quiet hours or disabled)")
            }
        }
    }

    private fun handleGenericNotification(message: RemoteMessage) {
        message.notification?.let { notification ->
            showNotification(
                title = notification.title ?: "MediTrack",
                body = notification.body ?: "",
                data = message.data
            )
        }
    }

    private fun showOrderNotification(orderId: String, title: String, body: String) {
        val intent = Intent(this, OrderTrackingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(OrderTrackingActivity.EXTRA_ORDER_ID, orderId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            orderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_medicine)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_BASE + orderId.hashCode(), notification)
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
        }

        val pendingIntent = intent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_medicine)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply { pendingIntent?.let { setContentIntent(it) } }
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun saveFcmToken(userId: String, token: String) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .update("fcmToken", token)
            .addOnSuccessListener {
                Log.d(TAG, "FCM token saved successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save FCM token", e)
            }
    }
}
