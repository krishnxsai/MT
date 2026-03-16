package com.meditrack.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.meditrack.app.R
import com.meditrack.app.data.model.OrderStatus
import com.meditrack.app.ui.order.OrderTrackingActivity

/**
 * Helper for order status notifications to patients.
 * Shows local notifications when order status changes.
 */
object OrderNotificationHelper {

    private const val CHANNEL_ID = "meditrack_orders"
    private const val CHANNEL_NAME = "Order Updates"
    private const val CHANNEL_DESCRIPTION = "Notifications about your medicine order status"
    private const val NOTIFICATION_ID_BASE = 7000

    /**
     * Creates the notification channel for order updates.
     * Should be called once during app initialization.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Shows a notification for order status change.
     *
     * @param context Application context
     * @param orderId Order ID for navigation
     * @param status New order status
     * @param pharmacyName Name of the pharmacy
     */
    fun notifyOrderStatusChange(
        context: Context,
        orderId: String,
        status: OrderStatus,
        pharmacyName: String
    ) {
        val (title, body, priority) = when (status) {
            OrderStatus.CONFIRMED -> Triple(
                "Order Accepted ✓",
                "Your order from $pharmacyName has been accepted and is being processed.",
                NotificationCompat.PRIORITY_HIGH
            )
            OrderStatus.PREPARING -> Triple(
                "Preparing Your Medicine",
                "$pharmacyName is preparing your order. It will be ready soon.",
                NotificationCompat.PRIORITY_DEFAULT
            )
            OrderStatus.READY -> Triple(
                "Medicine Ready! 📦",
                "Your medicine is ready for pickup at $pharmacyName.",
                NotificationCompat.PRIORITY_HIGH
            )
            OrderStatus.SHIPPED -> Triple(
                "Out for Delivery 🚚",
                "Your order from $pharmacyName is on the way!",
                NotificationCompat.PRIORITY_HIGH
            )
            OrderStatus.DELIVERED -> Triple(
                "Order Delivered ✅",
                "Your order from $pharmacyName has been delivered. Stay healthy!",
                NotificationCompat.PRIORITY_HIGH
            )
            OrderStatus.CANCELLED -> Triple(
                "Order Cancelled",
                "Your order from $pharmacyName has been cancelled.",
                NotificationCompat.PRIORITY_DEFAULT
            )
            else -> return // Don't notify for PENDING status
        }

        showNotification(context, orderId, title, body, priority)
    }

    /**
     * Shows a simple order notification with custom message.
     */
    fun notifyOrder(
        context: Context,
        orderId: String,
        title: String,
        body: String
    ) {
        showNotification(context, orderId, title, body, NotificationCompat.PRIORITY_DEFAULT)
    }

    private fun showNotification(
        context: Context,
        orderId: String,
        title: String,
        body: String,
        priority: Int
    ) {
        val intent = Intent(context, OrderTrackingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(OrderTrackingActivity.EXTRA_ORDER_ID, orderId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            orderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_medicine)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_BASE + orderId.hashCode(), notification)
    }

    /**
     * Cancels an existing notification for an order.
     */
    fun cancelOrderNotification(context: Context, orderId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_BASE + orderId.hashCode())
    }
}
