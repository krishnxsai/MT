package com.example.meditrack.data.repository

import com.example.meditrack.data.model.*
import com.example.meditrack.data.security.AuditLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Repository for managing clinical decisions, prescriptions, and doctor recommendations.
 * Provides real-time synchronization via Firestore.
 */
class ClinicalDecisionRepository {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val usersCollection by lazy { firestore.collection("users") }
    private val clinicalDecisionsCollection by lazy { firestore.collection("clinicalDecisions") }
    private val medicinesCollection by lazy { firestore.collection("medicines") }
    private val healthLogsCollection by lazy { firestore.collection("healthLogs") }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // ==================== Clinical Decision Creation ====================

    /**
     * Create a new clinical decision (prescription, follow-up, alert, etc.)
     */
    suspend fun createClinicalDecision(decision: ClinicalDecision): Resource<ClinicalDecision> = withContext(Dispatchers.IO) {
        try {
            val doctorId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            // Verify access to patient
            val accessCheck = verifyDoctorAccessToPatient(decision.patientId, doctorId)
            if (accessCheck is Resource.Error) {
                return@withContext Resource.Error(accessCheck.message)
            }

            // Get doctor's name
            val doctorDoc = usersCollection.document(doctorId).get().await()
            val doctorName = doctorDoc.getString("displayName") ?: "Doctor"

            val decisionWithDoctor = decision.copy(
                doctorId = doctorId,
                doctorName = doctorName
            )

            val docRef = clinicalDecisionsCollection.document()
            val decisionToSave = decisionWithDoctor.copy(id = docRef.id)

            val data = decisionToSave.toMap().toMutableMap()
            data["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

            docRef.set(data).await()

            // Audit log
            AuditLogger.logSuccess(
                AuditLogger.AuditAction.DOCTOR_NOTE_CREATE,
                targetId = docRef.id,
                targetType = "clinicalDecision",
                details = mapOf(
                    "patientId" to decision.patientId,
                    "type" to decision.type.name,
                    "priority" to decision.priority.name
                )
            )

            // If this is a prescription, automatically create medicines for the patient
            if (decision.type == ClinicalDecisionType.PRESCRIPTION) {
                createMedicinesFromPrescriptions(decision.patientId, decisionToSave.prescriptions, docRef.id)
            }

            Resource.Success(decisionToSave)
        } catch (e: Exception) {
            AuditLogger.logFailure(
                AuditLogger.AuditAction.DOCTOR_NOTE_CREATE,
                e.message ?: "Unknown error",
                details = mapOf("patientId" to decision.patientId)
            )
            Resource.Error(e.message ?: "Failed to create clinical decision", e)
        }
    }

    /**
     * Update an existing clinical decision
     */
    suspend fun updateClinicalDecision(decision: ClinicalDecision): Resource<ClinicalDecision> = withContext(Dispatchers.IO) {
        try {
            val doctorId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            // Verify ownership
            if (decision.doctorId != doctorId) {
                return@withContext Resource.Error("Access denied: Cannot edit another doctor's decision")
            }

            clinicalDecisionsCollection.document(decision.id).update(decision.toMap()).await()

            Resource.Success(decision)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update clinical decision", e)
        }
    }

    /**
     * Delete a clinical decision
     */
    suspend fun deleteClinicalDecision(decisionId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val doctorId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            // Verify ownership
            val decisionDoc = clinicalDecisionsCollection.document(decisionId).get().await()
            val decisionDoctorId = decisionDoc.getString("doctorId")

            if (decisionDoctorId != doctorId) {
                return@withContext Resource.Error("Access denied: Cannot delete another doctor's decision")
            }

            clinicalDecisionsCollection.document(decisionId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to delete clinical decision", e)
        }
    }

    // ==================== Clinical Decision Queries (Doctor Side) ====================

    /**
     * Get all clinical decisions for a patient (by this doctor)
     */
    fun getPatientClinicalDecisionsFlow(patientId: String): Flow<Resource<List<ClinicalDecision>>> = callbackFlow {
        val doctorId = currentUserId
        if (doctorId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = clinicalDecisionsCollection
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("doctorId", doctorId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to get clinical decisions"))
                    return@addSnapshotListener
                }

                val decisions = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        ClinicalDecision.fromMap(doc.id, data)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                trySend(Resource.Success(decisions))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get clinical decisions by type
     */
    fun getPatientDecisionsByType(patientId: String, type: ClinicalDecisionType): Flow<Resource<List<ClinicalDecision>>> = callbackFlow {
        val doctorId = currentUserId
        if (doctorId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = clinicalDecisionsCollection
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("doctorId", doctorId)
            .whereEqualTo("type", type.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to get decisions"))
                    return@addSnapshotListener
                }

                val decisions = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        ClinicalDecision.fromMap(doc.id, data)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                trySend(Resource.Success(decisions))
            }

        awaitClose { listener.remove() }
    }

    // ==================== Patient Side Queries ====================

    /**
     * Get all public clinical decisions for the current patient
     * Used by patient to see their doctor's recommendations
     */
    fun getMyDoctorRecommendationsFlow(): Flow<Resource<List<ClinicalDecision>>> = callbackFlow {
        val patientId = currentUserId
        if (patientId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = clinicalDecisionsCollection
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("isPublic", true)
            .whereEqualTo("status", DecisionStatus.ACTIVE.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to get recommendations"))
                    return@addSnapshotListener
                }

                val decisions = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        ClinicalDecision.fromMap(doc.id, data)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                trySend(Resource.Success(decisions))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get critical alerts for patient
     */
    fun getMyCriticalAlertsFlow(): Flow<Resource<List<ClinicalDecision>>> = callbackFlow {
        val patientId = currentUserId
        if (patientId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = clinicalDecisionsCollection
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("isPublic", true)
            .whereIn("priority", listOf(Priority.HIGH.name, Priority.CRITICAL.name))
            .whereEqualTo("status", DecisionStatus.ACTIVE.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to get alerts"))
                    return@addSnapshotListener
                }

                val decisions = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        ClinicalDecision.fromMap(doc.id, data)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                trySend(Resource.Success(decisions))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get upcoming follow-ups for patient
     */
    fun getMyUpcomingFollowUpsFlow(): Flow<Resource<List<ClinicalDecision>>> = callbackFlow {
        val patientId = currentUserId
        if (patientId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = clinicalDecisionsCollection
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("isPublic", true)
            .whereEqualTo("type", ClinicalDecisionType.FOLLOW_UP.name)
            .whereEqualTo("status", DecisionStatus.ACTIVE.name)
            .whereGreaterThanOrEqualTo("followUpDate", Date())
            .orderBy("followUpDate", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to get follow-ups"))
                    return@addSnapshotListener
                }

                val decisions = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        ClinicalDecision.fromMap(doc.id, data)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                trySend(Resource.Success(decisions))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get my active prescriptions (patient view)
     */
    fun getMyActivePrescriptionsFlow(): Flow<Resource<List<ClinicalDecision>>> = callbackFlow {
        val patientId = currentUserId
        if (patientId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = clinicalDecisionsCollection
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("isPublic", true)
            .whereEqualTo("type", ClinicalDecisionType.PRESCRIPTION.name)
            .whereEqualTo("status", DecisionStatus.ACTIVE.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to get prescriptions"))
                    return@addSnapshotListener
                }

                val decisions = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        ClinicalDecision.fromMap(doc.id, data)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                trySend(Resource.Success(decisions))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Acknowledge a recommendation (patient marks as seen)
     */
    suspend fun acknowledgeRecommendation(decisionId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val patientId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            // Verify this decision belongs to this patient
            val decisionDoc = clinicalDecisionsCollection.document(decisionId).get().await()
            val decisionPatientId = decisionDoc.getString("patientId")

            if (decisionPatientId != patientId) {
                return@withContext Resource.Error("Access denied")
            }

            clinicalDecisionsCollection.document(decisionId).update(
                mapOf(
                    "patientAcknowledged" to true,
                    "acknowledgedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to acknowledge", e)
        }
    }

    // ==================== Vital Analysis ====================

    /**
     * Analyze patient's health logs and generate insights
     */
    suspend fun analyzePatientVitals(patientId: String, logs: List<HealthLog>): VitalAnalysis {
        if (logs.isEmpty()) {
            return VitalAnalysis(
                overallAssessment = "Insufficient data for analysis",
                riskLevel = "unknown"
            )
        }

        val recentLogs = logs.sortedByDescending { it.date }.take(30) // Last 30 entries

        // Calculate heart rate stats
        val heartRates = recentLogs.mapNotNull { it.heartRate?.toDouble() }
        val hrAvg = heartRates.averageOrNull()
        val hrMin = heartRates.minOrNull()
        val hrMax = heartRates.maxOrNull()
        val hrTrend = calculateTrend(heartRates)

        // Calculate BP stats
        val systolicBPs = recentLogs.mapNotNull { it.bloodPressureSystolic?.toDouble() }
        val diastolicBPs = recentLogs.mapNotNull { it.bloodPressureDiastolic?.toDouble() }
        val bpSysAvg = systolicBPs.averageOrNull()
        val bpDiaAvg = diastolicBPs.averageOrNull()
        val bpTrend = calculateTrend(systolicBPs)

        // Calculate glucose stats
        val glucoseLevels = recentLogs.mapNotNull { it.glucoseLevel }
        val glucoseAvg = glucoseLevels.averageOrNull()
        val glucoseTrend = calculateTrend(glucoseLevels)

        // Calculate temperature
        val temps = recentLogs.mapNotNull { it.temperature }
        val tempAvg = temps.averageOrNull()

        // Calculate weight trend
        val weights = recentLogs.mapNotNull { it.weight }
        val weightTrend = calculateTrend(weights)

        // Identify concerns
        val concerns = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        // Heart rate analysis
        hrAvg?.let { avg ->
            when {
                avg > 100 -> {
                    concerns.add("Elevated resting heart rate (avg: ${avg.toInt()} bpm)")
                    recommendations.add("Consider evaluation for tachycardia")
                }
                avg < 60 -> {
                    concerns.add("Low resting heart rate (avg: ${avg.toInt()} bpm)")
                    recommendations.add("Monitor for bradycardia symptoms")
                }
            }
        }

        // Blood pressure analysis
        if (bpSysAvg != null && bpDiaAvg != null) {
            when {
                bpSysAvg >= 140 || bpDiaAvg >= 90 -> {
                    concerns.add("High blood pressure readings detected")
                    recommendations.add("Consider antihypertensive medication review")
                }
                bpSysAvg < 90 || bpDiaAvg < 60 -> {
                    concerns.add("Low blood pressure readings")
                    recommendations.add("Monitor for hypotension symptoms")
                }
            }
        }

        // Glucose analysis
        glucoseAvg?.let { avg ->
            when {
                avg > 126 -> {
                    concerns.add("Elevated fasting glucose levels")
                    recommendations.add("Recommend HbA1c testing")
                }
                avg < 70 -> {
                    concerns.add("Low glucose levels detected")
                    recommendations.add("Review for hypoglycemia risk factors")
                }
            }
        }

        // Temperature analysis
        tempAvg?.let { avg ->
            if (avg > 37.5) {
                concerns.add("Elevated temperature readings")
                recommendations.add("Monitor for infection signs")
            }
        }

        // Collect symptoms
        val allSymptoms = recentLogs.flatMap { it.symptoms }.groupBy { it }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }

        if (allSymptoms.isNotEmpty()) {
            concerns.add("Recurring symptoms: ${allSymptoms.joinToString(", ")}")
        }

        // Calculate risk level
        val riskLevel = when {
            concerns.size >= 4 -> "critical"
            concerns.size >= 2 -> "high"
            concerns.size == 1 -> "moderate"
            else -> "low"
        }

        val overallAssessment = when (riskLevel) {
            "critical" -> "Multiple concerning indicators detected. Immediate medical attention recommended."
            "high" -> "Several health metrics require attention. Close monitoring advised."
            "moderate" -> "Some metrics slightly outside normal range. Continue monitoring."
            else -> "Overall vitals appear within normal ranges. Maintain current health practices."
        }

        return VitalAnalysis(
            heartRateAvg = hrAvg,
            heartRateMin = hrMin,
            heartRateMax = hrMax,
            heartRateTrend = hrTrend,
            bpSystolicAvg = bpSysAvg,
            bpDiastolicAvg = bpDiaAvg,
            bpTrend = bpTrend,
            glucoseAvg = glucoseAvg,
            glucoseTrend = glucoseTrend,
            temperatureAvg = tempAvg,
            weightTrend = weightTrend,
            overallAssessment = overallAssessment,
            riskLevel = riskLevel,
            concerns = concerns,
            recommendations = recommendations
        )
    }

    private fun calculateTrend(values: List<Double>): String {
        if (values.size < 3) return "insufficient_data"

        val firstHalf = values.takeLast(values.size / 2)
        val secondHalf = values.take(values.size / 2)

        val firstAvg = firstHalf.average()
        val secondAvg = secondHalf.average()

        val diff = firstAvg - secondAvg
        val percentChange = (diff / secondAvg) * 100

        return when {
            percentChange > 5 -> "increasing"
            percentChange < -5 -> "decreasing"
            else -> "stable"
        }
    }

    private fun List<Double>.averageOrNull(): Double? {
        return if (isEmpty()) null else average()
    }

    // ==================== Prescription to Medicine Conversion ====================

    private val prescriptionRepository by lazy { PrescriptionRepository() }

    /**
     * Create structured prescription records (with dedup & versioning)
     * and corresponding medicine entries for the patient's medicine list.
     *
     * Delegates to [PrescriptionRepository.createPrescription] for each
     * active item so that duplicates are caught and version tracking is
     * initialised from the very first write.
     */
    private suspend fun createMedicinesFromPrescriptions(
        patientId: String,
        prescriptions: List<Prescription>,
        clinicalDecisionId: String
    ) {
        for (prescription in prescriptions) {
            if (!prescription.isActive) continue

            val record = PrescriptionRecord(
                patientId = patientId,
                clinicalDecisionId = clinicalDecisionId,
                medicationName = prescription.medicationName,
                dosage = prescription.dosage,
                unit = prescription.unit,
                frequency = prescription.frequency,
                route = prescription.route,
                duration = prescription.duration,
                instructions = prescription.instructions,
                startDate = prescription.startDate ?: java.util.Date()
            )

            try {
                val result = prescriptionRepository.createPrescription(record)
                if (result is Resource.Error) {
                    android.util.Log.w("ClinicalDecisionRepo",
                        "Prescription creation skipped: ${result.message}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun generateReminderTimesFromFrequency(frequency: String): List<String> {
        return when {
            frequency.contains("Once daily", ignoreCase = true) -> listOf("08:00")
            frequency.contains("Twice daily", ignoreCase = true) || frequency.contains("BID", ignoreCase = true) ->
                listOf("08:00", "20:00")
            frequency.contains("Three times", ignoreCase = true) || frequency.contains("TID", ignoreCase = true) ->
                listOf("08:00", "14:00", "20:00")
            frequency.contains("Four times", ignoreCase = true) || frequency.contains("QID", ignoreCase = true) ->
                listOf("08:00", "12:00", "16:00", "20:00")
            frequency.contains("Every 4 hours", ignoreCase = true) ->
                listOf("06:00", "10:00", "14:00", "18:00", "22:00")
            frequency.contains("Every 6 hours", ignoreCase = true) ->
                listOf("06:00", "12:00", "18:00", "00:00")
            frequency.contains("Every 8 hours", ignoreCase = true) ->
                listOf("08:00", "16:00", "00:00")
            frequency.contains("Every 12 hours", ignoreCase = true) ->
                listOf("08:00", "20:00")
            frequency.contains("bedtime", ignoreCase = true) -> listOf("22:00")
            frequency.contains("Before meals", ignoreCase = true) -> listOf("07:30", "12:30", "18:30")
            frequency.contains("After meals", ignoreCase = true) -> listOf("09:00", "14:00", "20:00")
            else -> listOf("08:00") // Default to once daily
        }
    }

    // ==================== Helper Methods ====================

    private suspend fun verifyDoctorAccessToPatient(patientId: String, doctorId: String): Resource<Unit> {
        val patientDoc = usersCollection.document(patientId).get().await()
        val assignedDoctors = (patientDoc.get("assignedDoctors") as? List<*>)?.filterIsInstance<String>()
            ?: run {
                // Backward compat: check legacy single-value field
                val legacy = patientDoc.getString("assignedDoctor")
                if (!legacy.isNullOrEmpty()) listOf(legacy) else emptyList()
            }

        return if (assignedDoctors.contains(doctorId)) {
            Resource.Success(Unit)
        } else {
            Resource.Error("Access denied: Patient not assigned to you")
        }
    }

    /**
     * Check if current user is a doctor
     */
    suspend fun isCurrentUserDoctor(): Boolean = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext false
            val userDoc = usersCollection.document(userId).get().await()
            val role = userDoc.getString("role")
            role == UserRole.DOCTOR.name
        } catch (e: Exception) {
            false
        }
    }
}

