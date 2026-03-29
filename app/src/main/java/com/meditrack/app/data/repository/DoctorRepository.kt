package com.meditrack.app.data.repository

import android.util.Log
import com.meditrack.app.data.model.DoctorNote
import com.meditrack.app.data.model.HealthLog
import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.User
import com.meditrack.app.data.model.UserRole
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

class DoctorRepository {
    companion object {
        private const val TAG = "DoctorRepository"
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val usersCollection by lazy { firestore.collection("users") }
    private val medicinesCollection by lazy { firestore.collection("medicines") }
    private val healthLogsCollection by lazy { firestore.collection("healthLogs") }
    private val doctorNotesCollection by lazy { firestore.collection("doctorNotes") }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // ==================== Patient Management ====================

    /**
     * Get all patients assigned to the current doctor
     */
    suspend fun getAssignedPatients(): Resource<List<User>> = withContext(Dispatchers.IO) {
        try {
            val doctorId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            // Verify current user is a doctor
            val doctorDoc = usersCollection.document(doctorId).get().await()
            val doctorRole = doctorDoc.getString("role")
            if (doctorRole != UserRole.DOCTOR.name) {
                return@withContext Resource.Error("Access denied: Not a doctor account")
            }

            // Query patients who have this doctor assigned
            val snapshot = usersCollection
                .whereArrayContains("assignedDoctors", doctorId)
                .whereEqualTo("role", UserRole.PATIENT.name)
                .limit(50)
                .get()
                .await()

            val patients = snapshot.documents.mapNotNull { doc ->
                doc.toObject(User::class.java)
            }

            Resource.Success(patients)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get patients", e)
        }
    }

    /**
     * Get patients as a Flow for real-time updates
     */
    fun getAssignedPatientsFlow(): Flow<Resource<List<User>>> = callbackFlow {
        val doctorId = currentUserId
        if (doctorId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = usersCollection
            .whereArrayContains("assignedDoctors", doctorId)
            .whereEqualTo("role", UserRole.PATIENT.name)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to get patients"))
                    return@addSnapshotListener
                }

                val patients = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(User::class.java)
                } ?: emptyList()

                trySend(Resource.Success(patients))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get a specific patient's details (with security check)
     */
    suspend fun getPatientDetails(patientId: String): Resource<User> = withContext(Dispatchers.IO) {
        try {
            val doctorId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            val patientDoc = usersCollection.document(patientId).get().await()
            val patient = patientDoc.toObject(User::class.java)
                ?: return@withContext Resource.Error("Patient not found")

            // Security check: verify this patient has the current doctor assigned
            if (!patient.assignedDoctors.contains(doctorId)) {
                AuditLogger.logSecurityWarning(
                    "Unauthorized patient access attempt",
                    mapOf("patientId" to patientId, "doctorId" to doctorId)
                )
                return@withContext Resource.Error("Access denied: Patient not assigned to you")
            }

            // Log successful patient access
            AuditLogger.logPatientAccess(patientId, "profile")

            Resource.Success(patient)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get patient details", e)
        }
    }

    // ==================== Patient Medicines ====================

    /**
     * Get medicines for a specific patient (with security check)
     */
    suspend fun getPatientMedicines(patientId: String): Resource<List<Medicine>> = withContext(Dispatchers.IO) {
        try {
            val doctorId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            // Verify access
            val accessCheck = verifyPatientAccess(patientId, doctorId)
            if (accessCheck is Resource.Error) {
                return@withContext Resource.Error(accessCheck.message)
            }

            val snapshot = medicinesCollection
                .whereEqualTo("userId", patientId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()

            val medicines = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Medicine::class.java)
            }

            Resource.Success(medicines)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get patient medicines", e)
        }
    }

    fun getPatientMedicinesFlow(patientId: String): Flow<Resource<List<Medicine>>> = callbackFlow {
        val doctorId = currentUserId
        if (doctorId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = medicinesCollection
            .whereEqualTo("userId", patientId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to get medicines"))
                    return@addSnapshotListener
                }

                val medicines = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Medicine::class.java)
                } ?: emptyList()

                trySend(Resource.Success(medicines))
            }

        awaitClose { listener.remove() }
    }

    // ==================== Patient Health Logs ====================

    /**
     * Get health logs for a specific patient (with security check)
     */
    suspend fun getPatientHealthLogs(patientId: String): Resource<List<HealthLog>> = withContext(Dispatchers.IO) {
        try {
            val doctorId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            // Verify access
            val accessCheck = verifyPatientAccess(patientId, doctorId)
            if (accessCheck is Resource.Error) {
                return@withContext Resource.Error(accessCheck.message)
            }

            val snapshot = healthLogsCollection
                .whereEqualTo("userId", patientId)
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(60)
                .get()
                .await()

            val logs = snapshot.documents.mapNotNull { doc ->
                doc.toObject(HealthLog::class.java)
            }

            Resource.Success(logs)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get patient health logs", e)
        }
    }

    fun getPatientHealthLogsFlow(patientId: String): Flow<Resource<List<HealthLog>>> = callbackFlow {
        val doctorId = currentUserId
        if (doctorId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = healthLogsCollection
            .whereEqualTo("userId", patientId)
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(60)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to get health logs"))
                    return@addSnapshotListener
                }

                val logs = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(HealthLog::class.java)
                } ?: emptyList()

                trySend(Resource.Success(logs))
            }

        awaitClose { listener.remove() }
    }

    // ==================== Doctor Notes ====================

    /**
     * Add a doctor note for a patient
     */
    suspend fun addDoctorNote(note: DoctorNote): Resource<DoctorNote> = withContext(Dispatchers.IO) {
        try {
            val doctorId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            // Verify access
            val accessCheck = verifyPatientAccess(note.patientId, doctorId)
            if (accessCheck is Resource.Error) {
                return@withContext Resource.Error(accessCheck.message)
            }

            // Get doctor's name
            val doctorDoc = usersCollection.document(doctorId).get().await()
            val doctorName = doctorDoc.getString("displayName") ?: "Doctor"

            val noteWithDoctor = note.copy(
                doctorId = doctorId,
                doctorName = doctorName
            )

            val docRef = doctorNotesCollection.document()
            val noteToSave = noteWithDoctor.copy(id = docRef.id)

            val data = noteToSave.toMap().toMutableMap()
            data["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

            docRef.set(data).await()

            // Audit log
            AuditLogger.logSuccess(
                AuditLogger.AuditAction.DOCTOR_NOTE_CREATE,
                targetId = docRef.id,
                targetType = "doctorNote",
                details = mapOf("patientId" to note.patientId, "category" to note.category.name)
            )

            Resource.Success(noteToSave)
        } catch (e: Exception) {
            AuditLogger.logFailure(
                AuditLogger.AuditAction.DOCTOR_NOTE_CREATE,
                e.message ?: "Unknown error",
                details = mapOf("patientId" to note.patientId)
            )
            Resource.Error(e.message ?: "Failed to add note", e)
        }
    }

    /**
     * Update a doctor note
     */
    suspend fun updateDoctorNote(note: DoctorNote): Resource<DoctorNote> = withContext(Dispatchers.IO) {
        try {
            val doctorId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            // Verify the note belongs to this doctor
            if (note.doctorId != doctorId) {
                return@withContext Resource.Error("Access denied: Cannot edit another doctor's note")
            }

            doctorNotesCollection.document(note.id).update(note.toMap()).await()

            Resource.Success(note)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update note", e)
        }
    }

    /**
     * Delete a doctor note
     */
    suspend fun deleteDoctorNote(noteId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val doctorId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            // Verify ownership
            val noteDoc = doctorNotesCollection.document(noteId).get().await()
            val noteDoctorId = noteDoc.getString("doctorId")

            if (noteDoctorId != doctorId) {
                return@withContext Resource.Error("Access denied: Cannot delete another doctor's note")
            }

            doctorNotesCollection.document(noteId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to delete note", e)
        }
    }

    /**
     * Get all notes for a patient (by this doctor)
     */
    suspend fun getPatientNotes(patientId: String): Resource<List<DoctorNote>> = withContext(Dispatchers.IO) {
        try {
            val doctorId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            val snapshot = doctorNotesCollection
                .whereEqualTo("patientId", patientId)
                .whereEqualTo("doctorId", doctorId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()

            val notes = snapshot.documents.mapNotNull { doc ->
                doc.toObject(DoctorNote::class.java)
            }

            Resource.Success(notes)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get notes", e)
        }
    }

    fun getPatientNotesFlow(patientId: String): Flow<Resource<List<DoctorNote>>> = callbackFlow {
        val doctorId = currentUserId
        if (doctorId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = doctorNotesCollection
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("doctorId", doctorId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to get notes"))
                    return@addSnapshotListener
                }

                val notes = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(DoctorNote::class.java)
                } ?: emptyList()

                trySend(Resource.Success(notes))
            }

        awaitClose { listener.remove() }
    }

    // ==================== Helper Methods ====================

    private suspend fun verifyPatientAccess(patientId: String, doctorId: String): Resource<Unit> {
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

    /**
     * Get the phone number for a doctor by ID.
     * Used for calling doctor from appointment booking or doctor list.
     */
    suspend fun getDoctorPhone(doctorId: String): Resource<String?> = withContext(Dispatchers.IO) {
        try {
            val doctoDoc = usersCollection.document(doctorId).get().await()
            val doctor = doctoDoc.toObject(User::class.java)
                ?: return@withContext Resource.Success(null)

            // Verify doctor has DOCTOR role
            if (doctor.role != UserRole.DOCTOR) {
                return@withContext Resource.Success(null)
            }

            Resource.Success(doctor.phoneNumber)
        } catch (e: Exception) {
            Log.e(TAG, "getDoctorPhone error: ${e.message}")
            Resource.Success(null) // Return null on error for graceful fallback
        }
    }
}

