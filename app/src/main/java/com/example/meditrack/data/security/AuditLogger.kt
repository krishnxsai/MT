package com.example.meditrack.data.security

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Audit Logger for tracking security-sensitive operations.
 * Logs are stored in Firestore for compliance and monitoring.
 */
object AuditLogger {

    private const val TAG = "AuditLogger"
    private const val COLLECTION_AUDIT_LOGS = "auditLogs"

    enum class AuditAction {
        LOGIN,
        LOGOUT,
        SIGNUP,
        PASSWORD_RESET,
        PROFILE_UPDATE,
        MEDICINE_CREATE,
        MEDICINE_UPDATE,
        MEDICINE_DELETE,
        HEALTH_LOG_CREATE,
        HEALTH_LOG_UPDATE,
        HEALTH_LOG_DELETE,
        DOCTOR_NOTE_CREATE,
        DOCTOR_NOTE_UPDATE,
        DOCTOR_NOTE_DELETE,
        PATIENT_VIEW,
        DATA_EXPORT,
        PERMISSION_CHANGE,
        SECURITY_WARNING
    }

    data class AuditEntry(
        val userId: String,
        val action: AuditAction,
        val targetId: String? = null,
        val targetType: String? = null,
        val details: Map<String, Any?> = emptyMap(),
        val ipAddress: String? = null,
        val deviceInfo: String? = null,
        val timestamp: Date = Date(),
        val success: Boolean = true,
        val errorMessage: String? = null
    ) {
        fun toMap(): Map<String, Any?> {
            return mapOf(
                "userId" to userId,
                "action" to action.name,
                "targetId" to targetId,
                "targetType" to targetType,
                "details" to details,
                "ipAddress" to ipAddress,
                "deviceInfo" to deviceInfo,
                "timestamp" to timestamp,
                "success" to success,
                "errorMessage" to errorMessage
            )
        }
    }

    /**
     * Log an audit entry to Firestore
     */
    suspend fun log(
        action: AuditAction,
        targetId: String? = null,
        targetType: String? = null,
        details: Map<String, Any?> = emptyMap(),
        success: Boolean = true,
        errorMessage: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            val userId = currentUser?.uid

            // Skip logging if user is not authenticated
            // Firestore rules require authentication and userId to match auth.uid
            if (userId == null) {
                Log.w(TAG, "Skipping audit log - user not authenticated: ${action.name}")
                return@withContext
            }

            Log.d(TAG, "Creating audit log for user: $userId, action: ${action.name}")

            val entry = AuditEntry(
                userId = userId,
                action = action,
                targetId = targetId,
                targetType = targetType,
                details = details,
                deviceInfo = android.os.Build.MODEL,
                success = success,
                errorMessage = errorMessage
            )

            val entryMap = entry.toMap()
            Log.d(TAG, "Audit entry data - userId in map: ${entryMap["userId"]}")

            val firestore = FirebaseFirestore.getInstance()
            firestore.collection(COLLECTION_AUDIT_LOGS)
                .add(entryMap)
                .addOnSuccessListener {
                    Log.d(TAG, "Audit log created: ${action.name}")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to create audit log: ${e.message}")
                    Log.e(TAG, "Auth UID was: $userId")
                }

        } catch (e: Exception) {
            Log.e(TAG, "Error logging audit entry: ${e.message}")
        }
    }

    /**
     * Log a successful action
     */
    suspend fun logSuccess(
        action: AuditAction,
        targetId: String? = null,
        targetType: String? = null,
        details: Map<String, Any?> = emptyMap()
    ) {
        log(action, targetId, targetType, details, success = true)
    }

    /**
     * Log a failed action
     */
    suspend fun logFailure(
        action: AuditAction,
        errorMessage: String,
        targetId: String? = null,
        targetType: String? = null,
        details: Map<String, Any?> = emptyMap()
    ) {
        log(action, targetId, targetType, details, success = false, errorMessage = errorMessage)
    }

    /**
     * Log a security warning
     */
    suspend fun logSecurityWarning(
        message: String,
        details: Map<String, Any?> = emptyMap()
    ) {
        log(
            action = AuditAction.SECURITY_WARNING,
            details = details + ("warning" to message),
            success = false,
            errorMessage = message
        )
    }

    /**
     * Quick log method for patient data access by doctors
     */
    suspend fun logPatientAccess(patientId: String, dataType: String) {
        log(
            action = AuditAction.PATIENT_VIEW,
            targetId = patientId,
            targetType = "patient",
            details = mapOf("dataType" to dataType)
        )
    }
}

