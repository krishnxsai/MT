package com.example.meditrack.data.model

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
    EMERGENCY
}

