package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a booked appointment between a doctor and a patient.
 * Stored in the top-level `appointments` Firestore collection.
 */
data class Appointment(
    @DocumentId
    val id: String = "",
    val doctorId: String = "",
    val patientId: String = "",
    val doctorName: String = "",
    val patientName: String = "",
    val date: Date = Date(),           // The date of the appointment
    val startTime: String = "",        // "HH:mm" 24-h
    val endTime: String = "",          // "HH:mm" 24-h
    val status: AppointmentStatus = AppointmentStatus.PENDING,
    val type: AppointmentType = AppointmentType.CONSULTATION,
    val notes: String = "",            // Patient notes when booking
    val doctorNotes: String = "",      // Doctor notes on confirm/reject
    val cancellationReason: String = "",

    // ── Telemedicine support (GAP 4 FIX) ───────────────────────
    /** True if this is a video consultation */
    val isTelemedicine: Boolean = false,
    /** Meeting URL for video consultations */
    val callUrl: String = "",
    /** Meeting ID (e.g., Zoom/Google Meet ID) */
    val meetingId: String = "",
    /** Cancellation fee for no-show or last-minute cancel */
    val cancellationFee: Double = 0.0,
    /** Hours before appointment within which cancellation fee applies */
    val cancellationDeadlineHours: Int = 2,
    /** Actual cancellation fee applied (0 if cancelled outside deadline) */
    val cancellationFeeApplied: Double = 0.0,

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
        "date" to date,
        "startTime" to startTime,
        "endTime" to endTime,
        "status" to status.name,
        "type" to type.name,
        "notes" to notes,
        "doctorNotes" to doctorNotes,
        "cancellationReason" to cancellationReason,
        "isTelemedicine" to isTelemedicine,
        "callUrl" to callUrl,
        "meetingId" to meetingId,
        "cancellationFee" to cancellationFee,
        "cancellationDeadlineHours" to cancellationDeadlineHours,
        "cancellationFeeApplied" to cancellationFeeApplied,
        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Appointment = Appointment(
            id = id,
            doctorId = map["doctorId"] as? String ?: "",
            patientId = map["patientId"] as? String ?: "",
            doctorName = map["doctorName"] as? String ?: "",
            patientName = map["patientName"] as? String ?: "",
            date = (map["date"] as? Timestamp)?.toDate() ?: Date(),
            startTime = map["startTime"] as? String ?: "",
            endTime = map["endTime"] as? String ?: "",
            status = try {
                AppointmentStatus.valueOf(map["status"] as? String ?: "PENDING")
            } catch (_: Exception) { AppointmentStatus.PENDING },
            type = try {
                AppointmentType.valueOf(map["type"] as? String ?: "CONSULTATION")
            } catch (_: Exception) { AppointmentType.CONSULTATION },
            notes = map["notes"] as? String ?: "",
            doctorNotes = map["doctorNotes"] as? String ?: "",
            cancellationReason = map["cancellationReason"] as? String ?: "",
            isTelemedicine = map["isTelemedicine"] as? Boolean ?: false,
            callUrl = map["callUrl"] as? String ?: "",
            meetingId = map["meetingId"] as? String ?: "",
            cancellationFee = (map["cancellationFee"] as? Number)?.toDouble() ?: 0.0,
            cancellationDeadlineHours = (map["cancellationDeadlineHours"] as? Number)?.toInt() ?: 2,
            cancellationFeeApplied = (map["cancellationFeeApplied"] as? Number)?.toDouble() ?: 0.0,
            createdAt = (map["createdAt"] as? Timestamp)?.toDate(),
            updatedAt = (map["updatedAt"] as? Timestamp)?.toDate()
        )
    }
}

enum class AppointmentStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED,
    COMPLETED
}

enum class AppointmentType {
    CONSULTATION,
    FOLLOW_UP,
    CHECKUP,
    EMERGENCY,
    TELEMEDICINE
}

