package com.example.meditrack.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Medicine(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val dosage: String = "",
    val unit: String = "",
    val instructions: String = "",
    val reminderTimes: List<String> = emptyList(), // List of times like "08:00", "14:00"
    val repeatType: RepeatType = RepeatType.DAILY,
    val color: String = "teal", // Color identifier
    @get:PropertyName("isActive") @set:PropertyName("isActive")
    var isActive: Boolean = true,
    val alarmIds: List<Int> = emptyList(), // Alarm IDs for cancellation

    /** Non-null when this medicine was created by a doctor prescription. */
    val prescriptionId: String = "",
    /** True if this medicine was prescribed by a doctor — patient should not edit core fields. */
    @get:PropertyName("prescribedByDoctor") @set:PropertyName("prescribedByDoctor")
    var prescribedByDoctor: Boolean = false,

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    constructor() : this(id = "")

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "userId" to userId,
            "name" to name,
            "dosage" to dosage,
            "unit" to unit,
            "instructions" to instructions,
            "reminderTimes" to reminderTimes,
            "repeatType" to repeatType.name,
            "color" to color,
            "isActive" to isActive,
            "alarmIds" to alarmIds,
            "prescriptionId" to prescriptionId,
            "prescribedByDoctor" to prescribedByDoctor,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Medicine {
            @Suppress("UNCHECKED_CAST")
            return Medicine(
                id = id,
                userId = map["userId"] as? String ?: "",
                name = map["name"] as? String ?: "",
                dosage = map["dosage"] as? String ?: "",
                unit = map["unit"] as? String ?: "",
                instructions = map["instructions"] as? String ?: "",
                reminderTimes = (map["reminderTimes"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                repeatType = try {
                    RepeatType.valueOf(map["repeatType"] as? String ?: "DAILY")
                } catch (e: Exception) {
                    RepeatType.DAILY
                },
                color = map["color"] as? String ?: "teal",
                isActive = map["isActive"] as? Boolean ?: true,
                alarmIds = (map["alarmIds"] as? List<*>)?.filterIsInstance<Number>()?.map { it.toInt() } ?: emptyList(),
                prescriptionId = map["prescriptionId"] as? String ?: "",
                prescribedByDoctor = map["prescribedByDoctor"] as? Boolean ?: false,
                createdAt = map["createdAt"] as? Date,
                updatedAt = map["updatedAt"] as? Date
            )
        }
    }
}

enum class RepeatType {
    DAILY,
    WEEKLY,
    AS_NEEDED
}

