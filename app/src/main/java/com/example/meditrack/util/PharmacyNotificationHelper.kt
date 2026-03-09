package com.example.meditrack.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.meditrack.R
import com.example.meditrack.ui.pharmacy.PharmacyDashboardActivity

/**
 * Helper for pharmacy-specific notifications.
 * Uses the existing notification channel from MediTrackApplication.
 */
object PharmacyNotificationHelper {

    private const val CHANNEL_ID = "meditrack_general"

    private const val NOTIFICATION_ID_NEW_ORDER = 5001
    private const val NOTIFICATION_ID_ORDER_CANCELLED = 5002
    private const val NOTIFICATION_ID_PRESCRIPTION = 5003
    private const val NOTIFICATION_ID_LOW_STOCK = 5004

    /**
     * Notify pharmacy owner about a new incoming order.
     */
    fun notifyNewOrder(context: Context, medicineName: String, quantity: Int) {
        val intent = Intent(context, PharmacyDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_medicine)
            .setContentTitle("New Order Received")
            .setContentText("$quantity x $medicineName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_NEW_ORDER, notification)
    }

    /**
     * Notify pharmacy owner about an order cancellation.
     */
    fun notifyOrderCancelled(context: Context, medicineName: String, orderId: String) {
        val intent = Intent(context, PharmacyDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_medicine)
            .setContentTitle("Order Cancelled")
            .setContentText("Order for $medicineName has been cancelled")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_ORDER_CANCELLED, notification)
    }

    /**
     * Notify pharmacy owner about a prescription upload.
     */
    fun notifyPrescriptionUploaded(context: Context, patientName: String) {
        val intent = Intent(context, PharmacyDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_medicine)
            .setContentTitle("Prescription Uploaded")
            .setContentText("$patientName uploaded a new prescription")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_PRESCRIPTION, notification)
    }

    /**
     * Notify pharmacy owner about low stock items.
     */
    fun notifyLowStock(context: Context, itemCount: Int) {
        val intent = Intent(context, PharmacyDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_medicine)
            .setContentTitle("Low Stock Alert")
            .setContentText("$itemCount items are running low on stock")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_LOW_STOCK, notification)
    }
}

