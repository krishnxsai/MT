package com.example.meditrack.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a structured clinical decision made by a doctor for a patient.
 * This includes prescriptions, follow-ups, alerts, and recommendations.
 */
data class ClinicalDecision(
    @DocumentId
    val id: String = "",
    val doctorId: String = "",
    val doctorName: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val type: ClinicalDecisionType = ClinicalDecisionType.RECOMMENDATION,
    val priority: Priority = Priority.NORMAL,
    val status: DecisionStatus = DecisionStatus.ACTIVE,
    val title: String = "",
    val description: String = "",
    val isPublic: Boolean = true, // If true, patient can see; if false, only doctor

    // Prescription-specific fields
    val prescriptions: List<Prescription> = emptyList(),

    // Follow-up specific fields
    val followUpDate: Date? = null,
    val followUpInstructions: String = "",

    // Vital thresholds for alerts
    val vitalAlerts: List<VitalAlert> = emptyList(),

    // Analysis summary
    val analysisSummary: String = "",
    val analyzedSymptoms: List<String> = emptyList(),
    val analyzedVitals: VitalAnalysis? = null,

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null,

    // For tracking acknowledgment
    val patientAcknowledged: Boolean = false,
    val acknowledgedAt: Date? = null
) {
    constructor() : this(id = "")

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "doctorId" to doctorId,
            "doctorName" to doctorName,
            "patientId" to patientId,
            "patientName" to patientName,
            "type" to type.name,
            "priority" to priority.name,
            "status" to status.name,
            "title" to title,
            "description" to description,
            "isPublic" to isPublic,
            "prescriptions" to prescriptions.map { it.toMap() },
            "followUpDate" to followUpDate,
            "followUpInstructions" to followUpInstructions,
            "vitalAlerts" to vitalAlerts.map { it.toMap() },
            "analysisSummary" to analysisSummary,
            "analyzedSymptoms" to analyzedSymptoms,
            "analyzedVitals" to analyzedVitals?.toMap(),
            "patientAcknowledged" to patientAcknowledged,
            "acknowledgedAt" to acknowledgedAt,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, map: Map<String, Any?>): ClinicalDecision {
            val prescriptionsList = (map["prescriptions"] as? List<Map<String, Any?>>)
                ?.map { Prescription.fromMap(it) } ?: emptyList()

            val alertsList = (map["vitalAlerts"] as? List<Map<String, Any?>>)
                ?.map { VitalAlert.fromMap(it) } ?: emptyList()

            val vitalsMap = map["analyzedVitals"] as? Map<String, Any?>

            return ClinicalDecision(
                id = id,
                doctorId = map["doctorId"] as? String ?: "",
                doctorName = map["doctorName"] as? String ?: "",
                patientId = map["patientId"] as? String ?: "",
                patientName = map["patientName"] as? String ?: "",
                type = try {
                    ClinicalDecisionType.valueOf(map["type"] as? String ?: "RECOMMENDATION")
                } catch (e: Exception) {
                    ClinicalDecisionType.RECOMMENDATION
                },
                priority = try {
                    Priority.valueOf(map["priority"] as? String ?: "NORMAL")
                } catch (e: Exception) {
                    Priority.NORMAL
                },
                status = try {
                    DecisionStatus.valueOf(map["status"] as? String ?: "ACTIVE")
                } catch (e: Exception) {
                    DecisionStatus.ACTIVE
                },
                title = map["title"] as? String ?: "",
                description = map["description"] as? String ?: "",
                isPublic = map["isPublic"] as? Boolean ?: true,
                prescriptions = prescriptionsList,
                followUpDate = (map["followUpDate"] as? Timestamp)?.toDate(),
                followUpInstructions = map["followUpInstructions"] as? String ?: "",
                vitalAlerts = alertsList,
                analysisSummary = map["analysisSummary"] as? String ?: "",
                analyzedSymptoms = (map["analyzedSymptoms"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                analyzedVitals = vitalsMap?.let { VitalAnalysis.fromMap(it) },
                createdAt = (map["createdAt"] as? Timestamp)?.toDate(),
                updatedAt = (map["updatedAt"] as? Timestamp)?.toDate(),
                patientAcknowledged = map["patientAcknowledged"] as? Boolean ?: false,
                acknowledgedAt = (map["acknowledgedAt"] as? Timestamp)?.toDate()
            )
        }
    }
}

enum class ClinicalDecisionType {
    PRESCRIPTION,      // New prescription order
    FOLLOW_UP,         // Follow-up appointment/check
    VITAL_ALERT,       // Alert based on vital readings
    RECOMMENDATION,    // General health recommendation
    DIAGNOSIS,         // Diagnosis assessment
    LAB_ORDER,         // Lab test order
    LIFESTYLE          // Lifestyle modification advice
}

enum class Priority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

enum class DecisionStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED,
    PENDING_REVIEW
}

/**
 * Represents a structured prescription entry
 */
data class Prescription(
    val medicationName: String = "",
    val dosage: String = "",
    val unit: String = "",
    val frequency: String = "",
    val duration: String = "",
    val route: String = "Oral", // Oral, IV, Topical, etc.
    val instructions: String = "",
    val startDate: Date? = null,
    val endDate: Date? = null,
    val refills: Int = 0,
    val isActive: Boolean = true,
    val substitutionAllowed: Boolean = true,
    val warnings: List<String> = emptyList()
) {
    constructor() : this(medicationName = "")

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "medicationName" to medicationName,
            "dosage" to dosage,
            "unit" to unit,
            "frequency" to frequency,
            "duration" to duration,
            "route" to route,
            "instructions" to instructions,
            "startDate" to startDate,
            "endDate" to endDate,
            "refills" to refills,
            "isActive" to isActive,
            "substitutionAllowed" to substitutionAllowed,
            "warnings" to warnings
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): Prescription {
            return Prescription(
                medicationName = map["medicationName"] as? String ?: "",
                dosage = map["dosage"] as? String ?: "",
                unit = map["unit"] as? String ?: "",
                frequency = map["frequency"] as? String ?: "",
                duration = map["duration"] as? String ?: "",
                route = map["route"] as? String ?: "Oral",
                instructions = map["instructions"] as? String ?: "",
                startDate = (map["startDate"] as? Timestamp)?.toDate(),
                endDate = (map["endDate"] as? Timestamp)?.toDate(),
                refills = (map["refills"] as? Number)?.toInt() ?: 0,
                isActive = map["isActive"] as? Boolean ?: true,
                substitutionAllowed = map["substitutionAllowed"] as? Boolean ?: true,
                warnings = (map["warnings"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        }
    }
}

/**
 * Represents a vital sign alert threshold
 */
data class VitalAlert(
    val vitalType: VitalType = VitalType.HEART_RATE,
    val condition: AlertCondition = AlertCondition.ABOVE,
    val threshold: Double = 0.0,
    val message: String = "",
    val severity: Priority = Priority.NORMAL
) {
    constructor() : this(vitalType = VitalType.HEART_RATE)

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "vitalType" to vitalType.name,
            "condition" to condition.name,
            "threshold" to threshold,
            "message" to message,
            "severity" to severity.name
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): VitalAlert {
            return VitalAlert(
                vitalType = try {
                    VitalType.valueOf(map["vitalType"] as? String ?: "HEART_RATE")
                } catch (e: Exception) {
                    VitalType.HEART_RATE
                },
                condition = try {
                    AlertCondition.valueOf(map["condition"] as? String ?: "ABOVE")
                } catch (e: Exception) {
                    AlertCondition.ABOVE
                },
                threshold = (map["threshold"] as? Number)?.toDouble() ?: 0.0,
                message = map["message"] as? String ?: "",
                severity = try {
                    Priority.valueOf(map["severity"] as? String ?: "NORMAL")
                } catch (e: Exception) {
                    Priority.NORMAL
                }
            )
        }
    }
}

enum class VitalType {
    HEART_RATE,
    BLOOD_PRESSURE_SYSTOLIC,
    BLOOD_PRESSURE_DIASTOLIC,
    GLUCOSE_LEVEL,
    TEMPERATURE,
    WEIGHT,
    OXYGEN_SATURATION
}

enum class AlertCondition {
    ABOVE,
    BELOW,
    EQUALS,
    BETWEEN
}

/**
 * Represents analyzed vital signs summary
 */
data class VitalAnalysis(
    val heartRateAvg: Double? = null,
    val heartRateMin: Double? = null,
    val heartRateMax: Double? = null,
    val heartRateTrend: String = "", // "stable", "increasing", "decreasing"

    val bpSystolicAvg: Double? = null,
    val bpDiastolicAvg: Double? = null,
    val bpTrend: String = "",

    val glucoseAvg: Double? = null,
    val glucoseTrend: String = "",

    val temperatureAvg: Double? = null,
    val weightTrend: String = "",

    val overallAssessment: String = "",
    val riskLevel: String = "low", // "low", "moderate", "high", "critical"
    val concerns: List<String> = emptyList(),
    val recommendations: List<String> = emptyList()
) {
    constructor() : this(heartRateAvg = null)

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "heartRateAvg" to heartRateAvg,
            "heartRateMin" to heartRateMin,
            "heartRateMax" to heartRateMax,
            "heartRateTrend" to heartRateTrend,
            "bpSystolicAvg" to bpSystolicAvg,
            "bpDiastolicAvg" to bpDiastolicAvg,
            "bpTrend" to bpTrend,
            "glucoseAvg" to glucoseAvg,
            "glucoseTrend" to glucoseTrend,
            "temperatureAvg" to temperatureAvg,
            "weightTrend" to weightTrend,
            "overallAssessment" to overallAssessment,
            "riskLevel" to riskLevel,
            "concerns" to concerns,
            "recommendations" to recommendations
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): VitalAnalysis {
            return VitalAnalysis(
                heartRateAvg = (map["heartRateAvg"] as? Number)?.toDouble(),
                heartRateMin = (map["heartRateMin"] as? Number)?.toDouble(),
                heartRateMax = (map["heartRateMax"] as? Number)?.toDouble(),
                heartRateTrend = map["heartRateTrend"] as? String ?: "",
                bpSystolicAvg = (map["bpSystolicAvg"] as? Number)?.toDouble(),
                bpDiastolicAvg = (map["bpDiastolicAvg"] as? Number)?.toDouble(),
                bpTrend = map["bpTrend"] as? String ?: "",
                glucoseAvg = (map["glucoseAvg"] as? Number)?.toDouble(),
                glucoseTrend = map["glucoseTrend"] as? String ?: "",
                temperatureAvg = (map["temperatureAvg"] as? Number)?.toDouble(),
                weightTrend = map["weightTrend"] as? String ?: "",
                overallAssessment = map["overallAssessment"] as? String ?: "",
                riskLevel = map["riskLevel"] as? String ?: "low",
                concerns = (map["concerns"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                recommendations = (map["recommendations"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        }
    }
}

// Common medication frequencies
object MedicationFrequencies {
    val FREQUENCIES = listOf(
        "Once daily",
        "Twice daily (BID)",
        "Three times daily (TID)",
        "Four times daily (QID)",
        "Every 4 hours",
        "Every 6 hours",
        "Every 8 hours",
        "Every 12 hours",
        "Once weekly",
        "As needed (PRN)",
        "Before meals",
        "After meals",
        "At bedtime"
    )
}

// Common medication routes
object MedicationRoutes {
    val ROUTES = listOf(
        "Oral",
        "Sublingual",
        "Topical",
        "Intravenous (IV)",
        "Intramuscular (IM)",
        "Subcutaneous",
        "Inhalation",
        "Rectal",
        "Ophthalmic",
        "Otic",
        "Nasal",
        "Transdermal"
    )
}

// Common medication units
object MedicationUnits {
    val UNITS = listOf(
        "mg",
        "mcg",
        "g",
        "mL",
        "tablets",
        "capsules",
        "drops",
        "puffs",
        "units",
        "patches"
    )
}

