package com.meditrack.app.core.util

import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.User
import com.meditrack.app.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorAccessVerifier @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    suspend fun verifyPatientAccess(patientId: String, doctorId: String): Resource<Unit> {
        return try {
            val patientDoc = firestore.collection("users").document(patientId).get().await()
            if (!patientDoc.exists()) {
                return Resource.Error("Patient not found")
            }

            @Suppress("UNCHECKED_CAST")
            val assignedDoctors = patientDoc.get("assignedDoctors") as? List<String>
            val legacyDoctor = patientDoc.getString("assignedDoctor")

            if (assignedDoctors?.contains(doctorId) == true || legacyDoctor == doctorId) {
                Resource.Success(Unit)
            } else {
                Resource.Error("You are not assigned to this patient")
            }
        } catch (e: Exception) {
            Resource.Error("Failed to verify access: ${e.message}", e)
        }
    }

    suspend fun isCurrentUserDoctor(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            doc.getString("role") == UserRole.DOCTOR.name
        } catch (e: Exception) {
            false
        }
    }
}
