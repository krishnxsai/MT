package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Customizable reminder configuration for a prescription.
 * Stored in `users/{userId}/prescriptionReminders` subcollection.
 *
 * Allows patients to customize when they get reminded to take meds.
 */
data class PrescriptionReminder(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val medicineId: String = "",
    val medicineName: String = "",
    val prescriptionId: String = "",

    // ── Reminder times ──────────────────────────────────────────
    /** List of times to remind (e.g., ["08:00", "14:00", "20:00"]) */
    val reminderTimes: List<String> = emptyList(),

    // ── Reminder preferences ────────────────────────────────────
    /** Type of reminders: NOTIFICATION, SMS, EMAIL, PHARMA_CALL */
    val reminderMethods: List<ReminderMethod> = listOf(ReminderMethod.NOTIFICATION),
    /** Alert user X minutes before scheduled time */
    val reminderAdvanceMinutes: Int = 10,

    // ── Quiet hours ─────────────────────────────────────────────
    /** Don't remind during these quiet hours (e.g., "22:00" to "07:00") */
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "07:00",
    /** Reschedule reminder after quiet hours instead of skipping */
    val rescheduleAfterQuietHours: Boolean = true,

    // ── Frequency ───────────────────────────────────────────────
    /** When to repeat: DAILY, WEEKLY, MONTHLY, CUSTOM */
    val repeatType: RepeatType = RepeatType.DAILY,
    /** Days of week to remind (0=Sun, 1=Mon, etc., empty=all days) */
    val repeatDaysOfWeek: List<Int> = emptyList(),
    /** Start reminding from this date */
    val startDate: Date? = null,
    /** Stop reminding after this date */
    val endDate: Date? = null,

    // ── Smart reminders ─────────────────────────────────────────
    /** Remind when stock is running low */
    val remindOnLowStock: Boolean = true,
    /** Remind when medication is about to expire (days before) */
    val remindBeforeExpiry: Int = 30,
    /** Remind for refill when prescription is ending */
    val remindForRefill: Boolean = true,

    // ── Engagement ──────────────────────────────────────────────
    /** Is this reminder enabled */
    val isEnabled: Boolean = true,
    /** Last time this reminder triggered */
    val lastReminderSentAt: Date? = null,
    /** Number of reminders sent this month */
    val remindersThisMonth: Int = 0,
    /** Number of times user confirmed taking medicine */
    val confirmationCount: Int = 0,

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    constructor() : this(id = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "medicineId" to medicineId,
        "medicineName" to medicineName,
        "prescriptionId" to prescriptionId,
        "reminderTimes" to reminderTimes,
        "reminderMethods" to reminderMethods.map { it.name },
        "reminderAdvanceMinutes" to reminderAdvanceMinutes,
        "quietHoursStart" to quietHoursStart,
        "quietHoursEnd" to quietHoursEnd,
        "rescheduleAfterQuietHours" to rescheduleAfterQuietHours,
        "repeatType" to repeatType.name,
        "repeatDaysOfWeek" to repeatDaysOfWeek,
        "startDate" to startDate,
        "endDate" to endDate,
        "remindOnLowStock" to remindOnLowStock,
        "remindBeforeExpiry" to remindBeforeExpiry,
        "remindForRefill" to remindForRefill,
        "isEnabled" to isEnabled,
        "lastReminderSentAt" to lastReminderSentAt,
        "remindersThisMonth" to remindersThisMonth,
        "confirmationCount" to confirmationCount,
        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): PrescriptionReminder = PrescriptionReminder(
            id = id,
            userId = map["userId"] as? String ?: "",
            medicineId = map["medicineId"] as? String ?: "",
            medicineName = map["medicineName"] as? String ?: "",
            prescriptionId = map["prescriptionId"] as? String ?: "",
            reminderTimes = (map["reminderTimes"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            reminderMethods = (map["reminderMethods"] as? List<*>)?.filterIsInstance<String>()?.map {
                try { ReminderMethod.valueOf(it) } catch (_: Exception) { ReminderMethod.NOTIFICATION }
            } ?: listOf(ReminderMethod.NOTIFICATION),
            reminderAdvanceMinutes = (map["reminderAdvanceMinutes"] as? Number)?.toInt() ?: 10,
            quietHoursStart = map["quietHoursStart"] as? String ?: "22:00",
            quietHoursEnd = map["quietHoursEnd"] as? String ?: "07:00",
            rescheduleAfterQuietHours = map["rescheduleAfterQuietHours"] as? Boolean ?: true,
            repeatType = try {
                RepeatType.valueOf(map["repeatType"] as? String ?: "DAILY")
            } catch (_: Exception) { RepeatType.DAILY },
            repeatDaysOfWeek = (map["repeatDaysOfWeek"] as? List<*>)?.filterIsInstance<Number>()?.map { it.toInt() } ?: emptyList(),
            startDate = (map["startDate"] as? Timestamp)?.toDate(),
            endDate = (map["endDate"] as? Timestamp)?.toDate(),
            remindOnLowStock = map["remindOnLowStock"] as? Boolean ?: true,
            remindBeforeExpiry = (map["remindBeforeExpiry"] as? Number)?.toInt() ?: 30,
            remindForRefill = map["remindForRefill"] as? Boolean ?: true,
            isEnabled = map["isEnabled"] as? Boolean ?: true,
            lastReminderSentAt = (map["lastReminderSentAt"] as? Timestamp)?.toDate(),
            remindersThisMonth = (map["remindersThisMonth"] as? Number)?.toInt() ?: 0,
            confirmationCount = (map["confirmationCount"] as? Number)?.toInt() ?: 0,
            createdAt = (map["createdAt"] as? Timestamp)?.toDate(),
            updatedAt = (map["updatedAt"] as? Timestamp)?.toDate()
        )
    }
}

enum class ReminderMethod {
    NOTIFICATION,   // In-app push notification
    SMS,            // SMS text message
    EMAIL,          // Email reminder
    PHARMA_CALL     // Pharmacy calls customer
}
