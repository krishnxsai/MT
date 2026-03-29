package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Standalone prescription record stored in the `prescriptions` Firestore collection.
 *
 * Unlike the embedded [Prescription] in [ClinicalDecision], this is an
 * independently queryable, versioned document that maintains a full
 * modification history and links bidirectionally to the patient's
 * `medicines` collection entry.
 *
 * Lifecycle:  ACTIVE → MODIFIED → STOPPED / COMPLETED
 * Versioning: Every modification bumps [version] and appends to [versionHistory].
 */
data class PrescriptionRecord(
    @DocumentId
    val id: String = "",

    // ── Identity ────────────────────────────────────────────────
    val patientId: String = "",
    val doctorId: String = "",
    val doctorName: String = "",

    // ── Cross-links ─────────────────────────────────────────────
    /** ID of the ClinicalDecision that originated this prescription (nullable). */
    val clinicalDecisionId: String = "",
    /** ID of the corresponding doc in the `medicines` collection (nullable). */
    val medicineId: String = "",

    // ── Medication details ──────────────────────────────────────
    val medicationName: String = "",
    /** Lowercase-trimmed copy of [medicationName] used for dedup queries. */
    val medicationNameNormalized: String = "",
    val dosage: String = "",
    val unit: String = "",
    val frequency: String = "",
    val route: String = "Oral",
    val duration: String = "",
    val instructions: String = "",

    // ── Status ──────────────────────────────────────────────────
    val status: PrescriptionStatus = PrescriptionStatus.ACTIVE,
    val isActive: Boolean = true,

    // ── Versioning ──────────────────────────────────────────────
    val version: Int = 1,
    val versionHistory: List<VersionEntry> = emptyList(),

    // ── Dates ───────────────────────────────────────────────────
    val startDate: Date? = null,
    val endDate: Date? = null,
    val stoppedAt: Date? = null,
    val stopReason: String = "",

    // ── Digital Signature (GAP 5 FIX) ─────────────────────────
    /** Base64-encoded digital signature from doctor */
    val digitalSignature: String = "",
    /** Timestamp when prescription was signed */
    val signedAt: Date? = null,
    /** Doctor's signing certificate (optional) */
    val signingCertificate: String = "",

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    /** Firestore no-arg constructor */
    constructor() : this(id = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "patientId" to patientId,
        "doctorId" to doctorId,
        "doctorName" to doctorName,
        "clinicalDecisionId" to clinicalDecisionId,
        "medicineId" to medicineId,
        "medicationName" to medicationName,
        "medicationNameNormalized" to medicationName.trim().lowercase(),
        "dosage" to dosage,
        "unit" to unit,
        "frequency" to frequency,
        "route" to route,
        "duration" to duration,
        "instructions" to instructions,
        "status" to status.name,
        "isActive" to isActive,
        "version" to version,
        "versionHistory" to versionHistory.map { it.toMap() },
        "startDate" to startDate,
        "endDate" to endDate,
        "stoppedAt" to stoppedAt,
        "stopReason" to stopReason,
        "digitalSignature" to digitalSignature,
        "signedAt" to signedAt,
        "signingCertificate" to signingCertificate,
        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, map: Map<String, Any?>): PrescriptionRecord {
            val historyList = (map["versionHistory"] as? List<Map<String, Any?>>)
                ?.map { VersionEntry.fromMap(it) } ?: emptyList()

            return PrescriptionRecord(
                id = id,
                patientId = map["patientId"] as? String ?: "",
                doctorId = map["doctorId"] as? String ?: "",
                doctorName = map["doctorName"] as? String ?: "",
                clinicalDecisionId = map["clinicalDecisionId"] as? String ?: "",
                medicineId = map["medicineId"] as? String ?: "",
                medicationName = map["medicationName"] as? String ?: "",
                medicationNameNormalized = map["medicationNameNormalized"] as? String ?: "",
                dosage = map["dosage"] as? String ?: "",
                unit = map["unit"] as? String ?: "",
                frequency = map["frequency"] as? String ?: "",
                route = map["route"] as? String ?: "Oral",
                duration = map["duration"] as? String ?: "",
                instructions = map["instructions"] as? String ?: "",
                status = try {
                    PrescriptionStatus.valueOf(map["status"] as? String ?: "ACTIVE")
                } catch (_: Exception) { PrescriptionStatus.ACTIVE },
                isActive = map["isActive"] as? Boolean ?: true,
                version = (map["version"] as? Number)?.toInt() ?: 1,
                versionHistory = historyList,
                startDate = (map["startDate"] as? Timestamp)?.toDate(),
                endDate = (map["endDate"] as? Timestamp)?.toDate(),
                stoppedAt = (map["stoppedAt"] as? Timestamp)?.toDate(),
                stopReason = map["stopReason"] as? String ?: "",
                createdAt = (map["createdAt"] as? Timestamp)?.toDate(),
                updatedAt = (map["updatedAt"] as? Timestamp)?.toDate()
            )
        }
    }
}

enum class PrescriptionStatus {
    ACTIVE,
    MODIFIED,
    STOPPED,
    COMPLETED
}

/**
 * Single entry in the version history trail.
 */
data class VersionEntry(
    val version: Int = 0,
    val changedFields: List<String> = emptyList(),
    val changedBy: String = "",       // doctorId
    val changedAt: Date? = null,
    val previousValues: Map<String, Any?> = emptyMap(),
    val changeNote: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "version" to version,
        "changedFields" to changedFields,
        "changedBy" to changedBy,
        "changedAt" to changedAt,
        "previousValues" to previousValues,
        "changeNote" to changeNote
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): VersionEntry = VersionEntry(
            version = (map["version"] as? Number)?.toInt() ?: 0,
            changedFields = (map["changedFields"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            changedBy = map["changedBy"] as? String ?: "",
            changedAt = (map["changedAt"] as? Timestamp)?.toDate(),
            previousValues = map["previousValues"] as? Map<String, Any?> ?: emptyMap(),
            changeNote = map["changeNote"] as? String ?: ""
        )
    }
}

