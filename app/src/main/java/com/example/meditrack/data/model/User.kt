package com.example.meditrack.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class User(
    @DocumentId
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val profileImageUrl: String = "",
    val role: UserRole = UserRole.PATIENT,
    val assignedDoctors: List<String> = emptyList(), // List of doctor UIDs for patients
    val assignedDoctorNames: Map<String, String> = emptyMap(), // doctorUid -> displayName
    val phoneNumber: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    // No-argument constructor required for Firestore
    constructor() : this(uid = "")

    /** Backward-compat helper: returns first assigned doctor ID or empty string. */
    val assignedDoctor: String get() = assignedDoctors.firstOrNull() ?: ""

    /** Backward-compat helper: returns first assigned doctor name or empty string. */
    val assignedDoctorName: String get() = assignedDoctorNames.values.firstOrNull() ?: ""

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "email" to email,
            "displayName" to displayName,
            "profileImageUrl" to profileImageUrl,
            "role" to role.name,
            "assignedDoctors" to assignedDoctors,
            "assignedDoctorNames" to assignedDoctorNames,
            "phoneNumber" to phoneNumber,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(uid: String, map: Map<String, Any?>): User {
            // Backward-compat: migrate old single-value fields to lists
            val doctors = (map["assignedDoctors"] as? List<*>)?.filterIsInstance<String>()
                ?: run {
                    val legacy = map["assignedDoctor"] as? String
                    if (!legacy.isNullOrEmpty()) listOf(legacy) else emptyList()
                }
            val doctorNames = (map["assignedDoctorNames"] as? Map<*, *>)
                ?.entries
                ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? String)?.let { value -> key to value } } }
                ?.toMap()
                ?: run {
                    val legacyId = map["assignedDoctor"] as? String ?: ""
                    val legacyName = map["assignedDoctorName"] as? String ?: ""
                    if (legacyId.isNotEmpty() && legacyName.isNotEmpty()) mapOf(legacyId to legacyName) else emptyMap()
                }

            return User(
                uid = uid,
                email = map["email"] as? String ?: "",
                displayName = map["displayName"] as? String ?: "",
                profileImageUrl = map["profileImageUrl"] as? String ?: "",
                role = try {
                    UserRole.valueOf(map["role"] as? String ?: "PATIENT")
                } catch (e: Exception) {
                    UserRole.PATIENT
                },
                assignedDoctors = doctors,
                assignedDoctorNames = doctorNames,
                phoneNumber = map["phoneNumber"] as? String ?: "",
                createdAt = map["createdAt"] as? Date,
                updatedAt = map["updatedAt"] as? Date
            )
        }
    }
}

enum class UserRole {
    PATIENT,
    DOCTOR
}

