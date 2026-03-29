package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents user notification preferences.
 * Allows granular control over different notification types and quiet hours.
 * Stored in `notificationPreferences/{userId}` collection.
 */
data class NotificationPreference(
    @DocumentId
    val userId: String = "",

    // ── Notification Type Toggles ──────────────────────────────
    val appointmentReminders: Boolean = true,
    val prescriptionNotifications: Boolean = true,
    val orderStatusUpdates: Boolean = true,
    val promotionalMessages: Boolean = false,  // Opt-in only
    val healthAlerts: Boolean = true,

    // ── Quiet Hours (Do Not Disturb) ──────────────────────────
    val quietHoursEnabled: Boolean = false,
    val quietHourStart: String = "22:00",  // HH:mm format (10 PM)
    val quietHourEnd: String = "08:00",    // HH:mm format (8 AM)

    // ── Notification Channel Preferences ──────────────────────
    // Maps NotificationType to Boolean (true = enabled)
    val notificationChannels: Map<String, Boolean> = mapOf(
        NotificationType.APPOINTMENT.name to true,
        NotificationType.PRESCRIPTION.name to true,
        NotificationType.ORDER_STATUS.name to true,
        NotificationType.PROMOTIONAL.name to false,
        NotificationType.HEALTH_ALERT.name to true
    ),

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    constructor() : this(userId = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "appointmentReminders" to appointmentReminders,
        "prescriptionNotifications" to prescriptionNotifications,
        "orderStatusUpdates" to orderStatusUpdates,
        "promotionalMessages" to promotionalMessages,
        "healthAlerts" to healthAlerts,
        "quietHoursEnabled" to quietHoursEnabled,
        "quietHourStart" to quietHourStart,
        "quietHourEnd" to quietHourEnd,
        "notificationChannels" to notificationChannels,
        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): NotificationPreference = NotificationPreference(
            userId = map["userId"] as? String ?: "",
            appointmentReminders = map["appointmentReminders"] as? Boolean ?: true,
            prescriptionNotifications = map["prescriptionNotifications"] as? Boolean ?: true,
            orderStatusUpdates = map["orderStatusUpdates"] as? Boolean ?: true,
            promotionalMessages = map["promotionalMessages"] as? Boolean ?: false,
            healthAlerts = map["healthAlerts"] as? Boolean ?: true,
            quietHoursEnabled = map["quietHoursEnabled"] as? Boolean ?: false,
            quietHourStart = map["quietHourStart"] as? String ?: "22:00",
            quietHourEnd = map["quietHourEnd"] as? String ?: "08:00",
            notificationChannels = @Suppress("UNCHECKED_CAST") (map["notificationChannels"] as? Map<String, Boolean> ?: emptyMap()),
            createdAt = (map["createdAt"] as? Timestamp)?.toDate(),
            updatedAt = (map["updatedAt"] as? Timestamp)?.toDate()
        )

        fun default(userId: String): NotificationPreference = NotificationPreference(
            userId = userId,
            notificationChannels = mapOf(
                NotificationType.APPOINTMENT.name to true,
                NotificationType.PRESCRIPTION.name to true,
                NotificationType.ORDER_STATUS.name to true,
                NotificationType.PROMOTIONAL.name to false,
                NotificationType.HEALTH_ALERT.name to true
            )
        )
    }
}

/**
 * Types of notifications that can be controlled.
 */
enum class NotificationType {
    APPOINTMENT,        // Appointment reminders & updates
    PRESCRIPTION,       // Prescription & medicine related
    ORDER_STATUS,       // Refill order status updates
    PROMOTIONAL,        // Promotions & special offers
    HEALTH_ALERT        // Health risk alerts from doctor
}
