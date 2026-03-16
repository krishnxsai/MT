package com.meditrack.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class HealthLog(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val date: Date = Date(),
    val heartRate: Int? = null, // bpm
    val bloodPressureSystolic: Int? = null, // mmHg
    val bloodPressureDiastolic: Int? = null, // mmHg
    val glucoseLevel: Double? = null, // mg/dL
    val weight: Double? = null, // kg
    val temperature: Double? = null, // °C
    val symptoms: List<String> = emptyList(),
    val notes: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    constructor() : this(id = "")

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "userId" to userId,
            "date" to date,
            "heartRate" to heartRate,
            "bloodPressureSystolic" to bloodPressureSystolic,
            "bloodPressureDiastolic" to bloodPressureDiastolic,
            "glucoseLevel" to glucoseLevel,
            "weight" to weight,
            "temperature" to temperature,
            "symptoms" to symptoms,
            "notes" to notes,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
    }

    val bloodPressureFormatted: String
        get() = if (bloodPressureSystolic != null && bloodPressureDiastolic != null) {
            "$bloodPressureSystolic/$bloodPressureDiastolic mmHg"
        } else {
            "—"
        }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): HealthLog {
            @Suppress("UNCHECKED_CAST")
            return HealthLog(
                id = id,
                userId = map["userId"] as? String ?: "",
                date = (map["date"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                heartRate = (map["heartRate"] as? Number)?.toInt(),
                bloodPressureSystolic = (map["bloodPressureSystolic"] as? Number)?.toInt(),
                bloodPressureDiastolic = (map["bloodPressureDiastolic"] as? Number)?.toInt(),
                glucoseLevel = (map["glucoseLevel"] as? Number)?.toDouble(),
                weight = (map["weight"] as? Number)?.toDouble(),
                temperature = (map["temperature"] as? Number)?.toDouble(),
                symptoms = (map["symptoms"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                notes = map["notes"] as? String ?: "",
                createdAt = (map["createdAt"] as? com.google.firebase.Timestamp)?.toDate(),
                updatedAt = (map["updatedAt"] as? com.google.firebase.Timestamp)?.toDate()
            )
        }

        val COMMON_SYMPTOMS = listOf(
            "Headache",
            "Fatigue",
            "Nausea",
            "Dizziness",
            "Fever",
            "Cough",
            "Shortness of breath",
            "Chest pain",
            "Body aches",
            "Loss of appetite",
            "Insomnia",
            "Anxiety"
        )
    }
}

