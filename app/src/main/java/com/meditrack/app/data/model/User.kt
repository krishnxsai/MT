package com.meditrack.app.data.model

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
    val status: AccountStatus = AccountStatus.APPROVED,
    val assignedDoctors: List<String> = emptyList(), // List of doctor UIDs for patients
    val assignedDoctorNames: Map<String, String> = emptyMap(), // doctorUid -> displayName
    val phoneNumber: String = "",

    // ── FCM Push Notifications ──────────────────────────────
    /** Firebase Cloud Messaging token for push notifications. */
    val fcmToken: String = "",

    // ── Verification fields (Doctor / Pharmacy) ─────────────
    /** URL of uploaded license document in Firebase Storage. */
    val licenseUrl: String = "",
    /** UID of the admin who verified this account. */
    val verifiedBy: String = "",
    /** Timestamp when the account was verified/rejected. */
    val verifiedAt: Date? = null,
    /** Reason provided by admin on rejection. */
    val rejectionReason: String = "",

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

    /** Whether this account requires admin approval before accessing the platform. */
    val requiresApproval: Boolean
        get() = role == UserRole.DOCTOR || role == UserRole.PHARMACY

    /** Whether the user can access their role-specific dashboard. */
    val isAccountActive: Boolean
        get() = status == AccountStatus.APPROVED

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "email" to email,
            "displayName" to displayName,
            "profileImageUrl" to profileImageUrl,
            "role" to role.name,
            "status" to status.name,
            "assignedDoctors" to assignedDoctors,
            "assignedDoctorNames" to assignedDoctorNames,
            "phoneNumber" to phoneNumber,
            "fcmToken" to fcmToken,
            "licenseUrl" to licenseUrl,
            "verifiedBy" to verifiedBy,
            "verifiedAt" to verifiedAt,
            "rejectionReason" to rejectionReason,
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
                status = try {
                    AccountStatus.valueOf(map["status"] as? String ?: "APPROVED")
                } catch (_: Exception) {
                    AccountStatus.APPROVED
                },
                assignedDoctors = doctors,
                assignedDoctorNames = doctorNames,
                phoneNumber = map["phoneNumber"] as? String ?: "",
                fcmToken = map["fcmToken"] as? String ?: "",
                licenseUrl = map["licenseUrl"] as? String ?: "",
                verifiedBy = map["verifiedBy"] as? String ?: "",
                verifiedAt = (map["verifiedAt"] as? com.google.firebase.Timestamp)?.toDate(),
                rejectionReason = map["rejectionReason"] as? String ?: "",
                createdAt = map["createdAt"] as? Date,
                updatedAt = map["updatedAt"] as? Date
            )
        }
    }
}

enum class UserRole {
    PATIENT,
    DOCTOR,
    ADMIN,
    PHARMACY
}

/**
 * Account approval status.
 *
 * PATIENT → auto-APPROVED on signup
 * DOCTOR / PHARMACY → PENDING until admin approves
 * ADMIN can set SUSPENDED at any time
 */
enum class AccountStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED
}

