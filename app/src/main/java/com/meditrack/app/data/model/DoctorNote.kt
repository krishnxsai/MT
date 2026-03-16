package com.meditrack.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class DoctorNote(
    @DocumentId
    val id: String = "",
    val doctorId: String = "",
    val doctorName: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val note: String = "",
    val category: NoteCategory = NoteCategory.GENERAL,
    val isPrivate: Boolean = false, // If true, only doctor can see; if false, patient can also see
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    constructor() : this(id = "")

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "doctorId" to doctorId,
            "doctorName" to doctorName,
            "patientId" to patientId,
            "patientName" to patientName,
            "note" to note,
            "category" to category.name,
            "isPrivate" to isPrivate,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): DoctorNote {
            return DoctorNote(
                id = id,
                doctorId = map["doctorId"] as? String ?: "",
                doctorName = map["doctorName"] as? String ?: "",
                patientId = map["patientId"] as? String ?: "",
                patientName = map["patientName"] as? String ?: "",
                note = map["note"] as? String ?: "",
                category = try {
                    NoteCategory.valueOf(map["category"] as? String ?: "GENERAL")
                } catch (e: Exception) {
                    NoteCategory.GENERAL
                },
                isPrivate = map["isPrivate"] as? Boolean ?: false,
                createdAt = (map["createdAt"] as? com.google.firebase.Timestamp)?.toDate(),
                updatedAt = (map["updatedAt"] as? com.google.firebase.Timestamp)?.toDate()
            )
        }
    }
}

enum class NoteCategory {
    GENERAL,
    DIAGNOSIS,
    PRESCRIPTION,
    FOLLOW_UP,
    LAB_RESULTS,
    OBSERVATION,
    WARNING
}

