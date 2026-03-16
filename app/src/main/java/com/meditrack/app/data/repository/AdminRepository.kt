package com.meditrack.app.data.repository

import com.meditrack.app.data.model.AccountStatus
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.User
import com.meditrack.app.data.model.UserRole
import com.meditrack.app.data.security.AuditLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Repository for admin-only operations:
 *  • Approve / reject / suspend user accounts
 *  • Query users by status
 *  • View audit logs
 */
class AdminRepository {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val usersCollection by lazy { firestore.collection("users") }
    private val auditLogsCollection by lazy { firestore.collection("auditLogs") }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // ══════════════════════════════════════════════════════════════
    // User Queries
    // ══════════════════════════════════════════════════════════════

    /** Get all users in the system. */
    suspend fun getAllUsers(): Resource<List<User>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = usersCollection.limit(50).get().await()
            val users = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { User.fromMap(doc.id, it) }
            }
            Resource.Success(users)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load users", e)
        }
    }

    /** Get users with PENDING status (doctors + pharmacies awaiting approval). */
    suspend fun getPendingUsers(): Resource<List<User>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = usersCollection
                .whereEqualTo("status", AccountStatus.PENDING.name)
                .limit(50)
                .get()
                .await()
            val users = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { User.fromMap(doc.id, it) }
            }
            Resource.Success(users)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load pending users", e)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Account Status Actions
    // ══════════════════════════════════════════════════════════════

    /** Approve a pending account. */
    suspend fun approveUser(userId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val adminId = currentUserId ?: return@withContext Resource.Error("Not logged in")
            usersCollection.document(userId).update(
                mapOf(
                    "status" to AccountStatus.APPROVED.name,
                    "verifiedBy" to adminId,
                    "verifiedAt" to Date(),
                    "rejectionReason" to "",
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()

            AuditLogger.logSuccess(
                AuditLogger.AuditAction.PERMISSION_CHANGE,
                targetId = userId,
                targetType = "user",
                details = mapOf("action" to "approve", "newStatus" to "APPROVED")
            )

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to approve user", e)
        }
    }

    /** Reject a pending account with a reason. */
    suspend fun rejectUser(userId: String, reason: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val adminId = currentUserId ?: return@withContext Resource.Error("Not logged in")
            usersCollection.document(userId).update(
                mapOf(
                    "status" to AccountStatus.REJECTED.name,
                    "verifiedBy" to adminId,
                    "verifiedAt" to Date(),
                    "rejectionReason" to reason,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()

            AuditLogger.logSuccess(
                AuditLogger.AuditAction.PERMISSION_CHANGE,
                targetId = userId,
                targetType = "user",
                details = mapOf("action" to "reject", "reason" to reason)
            )

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to reject user", e)
        }
    }

    /** Suspend an active account. */
    suspend fun suspendUser(userId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val adminId = currentUserId ?: return@withContext Resource.Error("Not logged in")
            usersCollection.document(userId).update(
                mapOf(
                    "status" to AccountStatus.SUSPENDED.name,
                    "verifiedBy" to adminId,
                    "verifiedAt" to Date(),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()

            AuditLogger.logSuccess(
                AuditLogger.AuditAction.PERMISSION_CHANGE,
                targetId = userId,
                targetType = "user",
                details = mapOf("action" to "suspend")
            )

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to suspend user", e)
        }
    }

    /** Re-activate a suspended or rejected account. */
    suspend fun reactivateUser(userId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val adminId = currentUserId ?: return@withContext Resource.Error("Not logged in")
            usersCollection.document(userId).update(
                mapOf(
                    "status" to AccountStatus.APPROVED.name,
                    "verifiedBy" to adminId,
                    "verifiedAt" to Date(),
                    "rejectionReason" to "",
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()

            AuditLogger.logSuccess(
                AuditLogger.AuditAction.PERMISSION_CHANGE,
                targetId = userId,
                targetType = "user",
                details = mapOf("action" to "reactivate", "newStatus" to "APPROVED")
            )

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to reactivate user", e)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Audit Logs
    // ══════════════════════════════════════════════════════════════

    /** Get recent audit logs (admin-scoped — reads all logs). */
    suspend fun getAuditLogs(limit: Long = 100): Resource<List<Map<String, Any?>>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = auditLogsCollection
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            val logs = snapshot.documents.map { doc ->
                val data = doc.data ?: emptyMap()
                data.plus("id" to doc.id)
            }
            Resource.Success(logs)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load audit logs", e)
        }
    }
}

