package com.meditrack.app.data.repository

import android.util.Log
import com.meditrack.app.data.model.*
import com.meditrack.app.data.security.AuditLogger
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
 * Repository for structured prescription management.
 *
 * Key responsibilities:
 *  • CRUD on the standalone `prescriptions` collection
 *  • Duplicate prevention (same active med for same patient)
 *  • Version tracking — every modification is recorded
 *  • Atomic writes — prescription + medicine docs written in a WriteBatch
 *  • Real-time flows for both doctor and patient UI
 *
 * ┌──────────┐    WriteBatch     ┌───────────────┐
 * │ Doctor   │─────────────────►│ prescriptions  │
 * │ Action   │                  │ medicines      │
 * └──────────┘                  └───────────────┘
 *                                    │  snapshot
 *                                    ▼  listener
 *                              ┌──────────┐
 *                              │ Patient  │
 *                              │ Dashboard│
 *                              └──────────┘
 */
class PrescriptionRepository {

    companion object {
        private const val TAG = "PrescriptionRepository"
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val prescriptionsCol by lazy { firestore.collection("prescriptions") }
    private val medicinesCol by lazy { firestore.collection("medicines") }
    private val usersCol by lazy { firestore.collection("users") }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // ═══════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════

    /**
     * Create a new prescription + corresponding medicine entry in one atomic batch.
     *
     * @return [Resource.Error] if a duplicate active prescription exists.
     */
    suspend fun createPrescription(
        record: PrescriptionRecord
    ): Resource<PrescriptionRecord> = withContext(Dispatchers.IO) {
        try {
            val doctorId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            // ── Access check ────────────────────────────────────────
            val access = verifyDoctorAccess(record.patientId, doctorId)
            if (access is Resource.Error) return@withContext Resource.Error(access.message)

            // ── Dedup check ─────────────────────────────────────────
            val duplicate = findActiveDuplicate(record.patientId, record.medicationName)
            if (duplicate != null) {
                return@withContext Resource.Error(
                    "Duplicate: \"${record.medicationName}\" is already an active prescription for this patient (ID: ${duplicate.id})"
                )
            }

            // ── Doctor name ─────────────────────────────────────────
            val doctorDoc = usersCol.document(doctorId).get().await()
            val doctorName = doctorDoc.getString("displayName") ?: "Doctor"

            // ── Prepare documents ───────────────────────────────────
            val rxRef = prescriptionsCol.document()
            val medRef = medicinesCol.document()

            val rxToSave = record.copy(
                id = rxRef.id,
                doctorId = doctorId,
                doctorName = doctorName,
                medicineId = medRef.id,
                medicationNameNormalized = record.medicationName.trim().lowercase(),
                version = 1,
                isActive = true,
                status = PrescriptionStatus.ACTIVE,
                startDate = record.startDate ?: Date()
            )

            val medicine = Medicine(
                id = medRef.id,
                userId = record.patientId,
                name = record.medicationName,
                dosage = record.dosage,
                unit = record.unit,
                instructions = buildMedicineInstructions(record),
                reminderTimes = generateReminderTimes(record.frequency),
                repeatType = RepeatType.DAILY,
                color = "blue",
                isActive = true
            )

            // ── Atomic batch write ──────────────────────────────────
            val batch = firestore.batch()

            val rxData = rxToSave.toMap().toMutableMap()
            rxData["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
            batch.set(rxRef, rxData)

            val medData = medicine.toMap().toMutableMap()
            medData["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
            medData["prescriptionId"] = rxRef.id
            medData["prescribedByDoctor"] = true
            batch.set(medRef, medData)

            batch.commit().await()

            AuditLogger.logSuccess(
                AuditLogger.AuditAction.DOCTOR_NOTE_CREATE,
                targetId = rxRef.id,
                targetType = "prescription",
                details = mapOf(
                    "patientId" to record.patientId,
                    "medicationName" to record.medicationName,
                    "dosage" to "${record.dosage} ${record.unit}"
                )
            )

            Log.d(TAG, "Created prescription ${rxRef.id} + medicine ${medRef.id}")
            Resource.Success(rxToSave)
        } catch (e: Exception) {
            Log.e(TAG, "createPrescription failed: ${e.message}")
            Resource.Error(e.message ?: "Failed to create prescription", e)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // MODIFY  (dosage, frequency, instructions, etc.)
    // ═══════════════════════════════════════════════════════════

    /**
     * Modify a prescription's clinical fields. Automatically:
     *  1. Bumps version
     *  2. Appends previous values to versionHistory
     *  3. Updates linked medicine doc
     */
    suspend fun modifyPrescription(
        prescriptionId: String,
        newDosage: String? = null,
        newUnit: String? = null,
        newFrequency: String? = null,
        newInstructions: String? = null,
        changeNote: String = ""
    ): Resource<PrescriptionRecord> = withContext(Dispatchers.IO) {
        try {
            val doctorId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            // ── Fetch current ───────────────────────────────────────
            val rxDoc = prescriptionsCol.document(prescriptionId).get().await()
            if (!rxDoc.exists()) return@withContext Resource.Error("Prescription not found")

            val current = PrescriptionRecord.fromMap(rxDoc.id, rxDoc.data!!)
            if (current.doctorId != doctorId) {
                return@withContext Resource.Error("Access denied: you are not the prescribing doctor")
            }
            if (!current.isActive) {
                return@withContext Resource.Error("Cannot modify an inactive prescription")
            }

            // ── Build change set ────────────────────────────────────
            val changedFields = mutableListOf<String>()
            val previousValues = mutableMapOf<String, Any?>()
            val updates = mutableMapOf<String, Any?>()

            newDosage?.let {
                if (it != current.dosage) {
                    changedFields.add("dosage")
                    previousValues["dosage"] = current.dosage
                    updates["dosage"] = it
                }
            }
            newUnit?.let {
                if (it != current.unit) {
                    changedFields.add("unit")
                    previousValues["unit"] = current.unit
                    updates["unit"] = it
                }
            }
            newFrequency?.let {
                if (it != current.frequency) {
                    changedFields.add("frequency")
                    previousValues["frequency"] = current.frequency
                    updates["frequency"] = it
                }
            }
            newInstructions?.let {
                if (it != current.instructions) {
                    changedFields.add("instructions")
                    previousValues["instructions"] = current.instructions
                    updates["instructions"] = it
                }
            }

            if (changedFields.isEmpty()) {
                return@withContext Resource.Error("No changes detected")
            }

            // ── Version entry ───────────────────────────────────────
            val entry = VersionEntry(
                version = current.version,
                changedFields = changedFields,
                changedBy = doctorId,
                changedAt = Date(),
                previousValues = previousValues,
                changeNote = changeNote
            )

            val newVersion = current.version + 1
            val newHistory = current.versionHistory + entry

            updates["version"] = newVersion
            updates["versionHistory"] = newHistory.map { it.toMap() }
            updates["status"] = PrescriptionStatus.MODIFIED.name
            updates["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

            // ── Batch: prescription + linked medicine ───────────────
            val batch = firestore.batch()
            batch.update(prescriptionsCol.document(prescriptionId), updates)

            if (current.medicineId.isNotEmpty()) {
                val medUpdates = mutableMapOf<String, Any?>()
                newDosage?.let { medUpdates["dosage"] = it }
                newUnit?.let { medUpdates["unit"] = it }

                val finalRecord = current.copy(
                    dosage = newDosage ?: current.dosage,
                    unit = newUnit ?: current.unit,
                    frequency = newFrequency ?: current.frequency,
                    instructions = newInstructions ?: current.instructions
                )
                medUpdates["instructions"] = buildMedicineInstructions(finalRecord)

                newFrequency?.let {
                    medUpdates["reminderTimes"] = generateReminderTimes(it)
                }
                medUpdates["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

                batch.update(medicinesCol.document(current.medicineId), medUpdates)
            }

            batch.commit().await()

            Log.d(TAG, "Modified prescription $prescriptionId → v$newVersion (${changedFields.joinToString()})")
            Resource.Success(current.copy(
                version = newVersion,
                dosage = newDosage ?: current.dosage,
                unit = newUnit ?: current.unit,
                frequency = newFrequency ?: current.frequency,
                instructions = newInstructions ?: current.instructions,
                status = PrescriptionStatus.MODIFIED
            ))
        } catch (e: Exception) {
            Log.e(TAG, "modifyPrescription failed: ${e.message}")
            Resource.Error(e.message ?: "Failed to modify prescription", e)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // STOP
    // ═══════════════════════════════════════════════════════════

    /**
     * Stop a prescription. The linked medicine is deactivated.
     * Prescription records are never deleted — they form the audit trail.
     */
    suspend fun stopPrescription(
        prescriptionId: String,
        reason: String = ""
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val doctorId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            val rxDoc = prescriptionsCol.document(prescriptionId).get().await()
            if (!rxDoc.exists()) return@withContext Resource.Error("Prescription not found")

            val current = PrescriptionRecord.fromMap(rxDoc.id, rxDoc.data!!)
            if (current.doctorId != doctorId) {
                return@withContext Resource.Error("Access denied")
            }
            if (!current.isActive) {
                return@withContext Resource.Error("Prescription is already inactive")
            }

            // ── Version entry for the stop action ───────────────────
            val entry = VersionEntry(
                version = current.version,
                changedFields = listOf("status", "isActive"),
                changedBy = doctorId,
                changedAt = Date(),
                previousValues = mapOf("status" to current.status.name, "isActive" to true),
                changeNote = "Stopped: $reason"
            )

            val batch = firestore.batch()

            val rxUpdates = mapOf<String, Any?>(
                "status" to PrescriptionStatus.STOPPED.name,
                "isActive" to false,
                "stoppedAt" to Date(),
                "stopReason" to reason,
                "version" to current.version + 1,
                "versionHistory" to (current.versionHistory + entry).map { it.toMap() },
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            batch.update(prescriptionsCol.document(prescriptionId), rxUpdates)

            // Deactivate linked medicine
            if (current.medicineId.isNotEmpty()) {
                batch.update(medicinesCol.document(current.medicineId), mapOf(
                    "isActive" to false,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ))
            }

            batch.commit().await()

            AuditLogger.logSuccess(
                AuditLogger.AuditAction.DOCTOR_NOTE_CREATE,
                targetId = prescriptionId,
                targetType = "prescription_stop",
                details = mapOf(
                    "patientId" to current.patientId,
                    "medicationName" to current.medicationName,
                    "reason" to reason
                )
            )

            Log.d(TAG, "Stopped prescription $prescriptionId: $reason")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "stopPrescription failed: ${e.message}")
            Resource.Error(e.message ?: "Failed to stop prescription", e)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════════════════════════

    /**
     * Real-time flow of ALL prescriptions for a patient (doctor view).
     */
    fun getPatientPrescriptionsFlow(patientId: String): Flow<Resource<List<PrescriptionRecord>>> = callbackFlow {
        val doctorId = currentUserId
        if (doctorId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }
        trySend(Resource.Loading)

        val listener = prescriptionsCol
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("doctorId", doctorId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to load prescriptions"))
                    return@addSnapshotListener
                }
                val records = snapshot?.documents?.mapNotNull { doc ->
                    try { PrescriptionRecord.fromMap(doc.id, doc.data!!) } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(Resource.Success(records))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Real-time flow of ACTIVE prescriptions for a patient (patient view).
     */
    fun getMyActivePrescriptionsFlow(): Flow<Resource<List<PrescriptionRecord>>> = callbackFlow {
        val patientId = currentUserId
        if (patientId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }
        trySend(Resource.Loading)

        val listener = prescriptionsCol
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("isActive", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to load prescriptions"))
                    return@addSnapshotListener
                }
                val records = snapshot?.documents?.mapNotNull { doc ->
                    try { PrescriptionRecord.fromMap(doc.id, doc.data!!) } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(Resource.Success(records))
            }

        awaitClose { listener.remove() }
    }

    /**
     * One-shot fetch of all prescriptions for a patient.
     */
    suspend fun getPatientPrescriptions(patientId: String): Resource<List<PrescriptionRecord>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = prescriptionsCol
                .whereEqualTo("patientId", patientId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(30)
                .get()
                .await()

            val records = snapshot.documents.mapNotNull { doc ->
                try { PrescriptionRecord.fromMap(doc.id, doc.data!!) } catch (_: Exception) { null }
            }
            Resource.Success(records)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load prescriptions", e)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Check for an existing active prescription with the same medication name
     * for the same patient. Returns the duplicate if found, null otherwise.
     */
    private suspend fun findActiveDuplicate(
        patientId: String,
        medicationName: String
    ): PrescriptionRecord? {
        val normalized = medicationName.trim().lowercase()
        val snapshot = prescriptionsCol
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("medicationNameNormalized", normalized)
            .whereEqualTo("isActive", true)
            .limit(1)
            .get()
            .await()

        return snapshot.documents.firstOrNull()?.let { doc ->
            try { PrescriptionRecord.fromMap(doc.id, doc.data!!) } catch (_: Exception) { null }
        }
    }

    private suspend fun verifyDoctorAccess(patientId: String, doctorId: String): Resource<Unit> {
        val patientDoc = usersCol.document(patientId).get().await()
        val assignedDoctors = (patientDoc.get("assignedDoctors") as? List<*>)?.filterIsInstance<String>()
            ?: run {
                // Backward compat: check legacy single-value field
                val legacy = patientDoc.getString("assignedDoctor")
                if (!legacy.isNullOrEmpty()) listOf(legacy) else emptyList()
            }
        return if (assignedDoctors.contains(doctorId)) Resource.Success(Unit)
        else Resource.Error("Access denied: patient not assigned to you")
    }

    private fun buildMedicineInstructions(record: PrescriptionRecord): String = buildString {
        append(record.frequency)
        if (record.route.isNotEmpty() && record.route != "Oral") {
            append(" (${record.route})")
        }
        if (record.instructions.isNotEmpty()) {
            append(". ${record.instructions}")
        }
    }

    /**
     * Map frequency string → list of HH:mm reminder times.
     */
    private fun generateReminderTimes(frequency: String): List<String> = when {
        frequency.contains("Once daily", true) -> listOf("08:00")
        frequency.contains("Twice daily", true) || frequency.contains("BID", true) ->
            listOf("08:00", "20:00")
        frequency.contains("Three times", true) || frequency.contains("TID", true) ->
            listOf("08:00", "14:00", "20:00")
        frequency.contains("Four times", true) || frequency.contains("QID", true) ->
            listOf("08:00", "12:00", "16:00", "20:00")
        frequency.contains("Every 4 hours", true) ->
            listOf("06:00", "10:00", "14:00", "18:00", "22:00")
        frequency.contains("Every 6 hours", true) ->
            listOf("06:00", "12:00", "18:00", "00:00")
        frequency.contains("Every 8 hours", true) ->
            listOf("08:00", "16:00", "00:00")
        frequency.contains("Every 12 hours", true) ->
            listOf("08:00", "20:00")
        frequency.contains("bedtime", true) -> listOf("22:00")
        frequency.contains("Before meals", true) -> listOf("07:30", "12:30", "18:30")
        frequency.contains("After meals", true) -> listOf("09:00", "14:00", "20:00")
        else -> listOf("08:00")
    }
}

