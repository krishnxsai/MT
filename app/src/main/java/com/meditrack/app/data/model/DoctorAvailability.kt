package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a recurring availability slot for a doctor.
 * Stored in the top-level `doctorAvailability` Firestore collection.
 *
 * Example: Dr. Smith is available Monday 09:00–17:00, 30-min slots.
 */
data class DoctorAvailability(
    @DocumentId
    val id: String = "",
    val doctorId: String = "",
    val doctorName: String = "",
    val dayOfWeek: Int = 1, // 1=Monday, 7=Sunday  (ISO 8601)
    val startTime: String = "09:00", // "HH:mm" 24-h format
    val endTime: String = "17:00",
    val slotDurationMinutes: Int = 30,
    val isActive: Boolean = true,

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    constructor() : this(id = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "doctorId" to doctorId,
        "doctorName" to doctorName,
        "dayOfWeek" to dayOfWeek,
        "startTime" to startTime,
        "endTime" to endTime,
        "slotDurationMinutes" to slotDurationMinutes,
        "isActive" to isActive,
        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )

    /** Returns user-friendly day name. */
    fun getDayName(): String = when (dayOfWeek) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        7 -> "Sunday"
        else -> "Unknown"
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): DoctorAvailability = DoctorAvailability(
            id = id,
            doctorId = map["doctorId"] as? String ?: "",
            doctorName = map["doctorName"] as? String ?: "",
            dayOfWeek = (map["dayOfWeek"] as? Number)?.toInt() ?: 1,
            startTime = map["startTime"] as? String ?: "09:00",
            endTime = map["endTime"] as? String ?: "17:00",
            slotDurationMinutes = (map["slotDurationMinutes"] as? Number)?.toInt() ?: 30,
            isActive = map["isActive"] as? Boolean ?: true,
            createdAt = (map["createdAt"] as? Timestamp)?.toDate(),
            updatedAt = (map["updatedAt"] as? Timestamp)?.toDate()
        )

        val DAY_OPTIONS = listOf(
            1 to "Monday",
            2 to "Tuesday",
            3 to "Wednesday",
            4 to "Thursday",
            5 to "Friday",
            6 to "Saturday",
            7 to "Sunday"
        )
    }
}

