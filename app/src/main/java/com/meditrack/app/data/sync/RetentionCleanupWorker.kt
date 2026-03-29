package com.meditrack.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.meditrack.app.data.model.DataRetentionPolicy
import com.meditrack.app.data.model.DeletionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

/**
 * WorkManager task for automated data retention cleanup.
 * Runs daily at 2 AM (low traffic time).
 *
 * Responsibilities:
 *  • Query records older than retention period
 *  • Mark for deletion (gracePeriod applies)
 *  • Permanently delete after grace period expires
 *  • Log all deletions to auditLogs
 */
class RetentionCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RetentionCleanupWorker"
    }

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting data retention cleanup job")

            val policy = DataRetentionPolicy.default()

            // Run all cleanup tasks
            cleanupOldRecords(policy)
            processPendingDeletions(policy)
            cleanupMarkedForDeletion(policy)

            Log.d(TAG, "Data retention cleanup completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Retention cleanup worker failed: ${e.message}", e)
            Result.retry()  // Retry on next scheduled run
        }
    }

    /**
     * Mark old records for deletion (mark phase).
     * Records are marked but not deleted until grace period expires.
     */
    private suspend fun cleanupOldRecords(policy: DataRetentionPolicy) {
        try {
            val now = System.currentTimeMillis()

            // ── Prescriptions (3 years) ────────────────────────
            markOldRecords(
                "prescriptions",
                (now - policy.prescriptionRetentionDays * 24 * 60 * 60 * 1000L),
                "Prescription (3-year retention expired)"
            )

            // ── Orders (18 months) ─────────────────────────────
            markOldRecords(
                "orders",
                (now - policy.orderHistoryRetentionDays * 24 * 60 * 60 * 1000L),
                "Order (18-month retention expired)"
            )

            // ── Chat Messages (2 years) ────────────────────────
            markOldRecords(
                "messages",
                (now - policy.chatMessageRetentionDays * 24 * 60 * 60 * 1000L),
                "Chat message (2-year retention expired)"
            )

            // ── Health Logs (5 years) ──────────────────────────
            markOldRecords(
                "healthLogs",
                (now - policy.healthLogRetentionDays * 24 * 60 * 60 * 1000L),
                "Health log (5-year retention expired)"
            )

            // ── Audit Logs (3 years) ──────────────────────────
            markOldRecords(
                "auditLogs",
                (now - policy.auditLogRetentionDays * 24 * 60 * 60 * 1000L),
                "Audit log (3-year retention expired)"
            )

            Log.d(TAG, "Old records marked for deletion")
        } catch (e: Exception) {
            Log.e(TAG, "Error marking old records: ${e.message}")
            throw e
        }
    }

    /**
     * Mark collection records older than cutoffTime for deletion.
     */
    private suspend fun markOldRecords(
        collectionName: String,
        cutoffTime: Long,
        reason: String
    ) {
        try {
            val policy = DataRetentionPolicy.default()
            val snapshot = firestore.collection(collectionName)
                .whereLessThan("createdAt", Date(cutoffTime))
                .whereNotEqualTo("markedForDeletion", true)
                .limit(policy.batchDeleteSize.toLong())
                .get()
                .await()

            if (snapshot.isEmpty) {
                Log.d(TAG, "No records to mark in $collectionName")
                return
            }

            val gracePeriodMs = policy.deletionGracePeriodDays * 24 * 60 * 60 * 1000L
            val scheduledDeleteTime = System.currentTimeMillis() + gracePeriodMs

            for (doc in snapshot.documents) {
                firestore.collection(collectionName).document(doc.id).update(mapOf(
                    "markedForDeletion" to true,
                    "deletionScheduledAt" to Date(scheduledDeleteTime),
                    "deletionReason" to reason
                )).await()
            }

            Log.d(TAG, "Marked ${snapshot.documents.size} records in $collectionName for deletion")
        } catch (e: Exception) {
            Log.w(TAG, "Error marking records in $collectionName: ${e.message}")
        }
    }

    /**
     * Delete records that were marked and grace period has expired.
     */
    private suspend fun cleanupMarkedForDeletion(policy: DataRetentionPolicy) {
        try {
            val now = Date()

            // Collections to check for marked-for-deletion records
            val collections = listOf(
                "prescriptions",
                "orders",
                "messages",
                "healthLogs",
                "auditLogs",
                "appointments",
                "transactions"
            )

            for (collectionName in collections) {
                deleteMarkedRecordsInCollection(collectionName, now)
            }

            Log.d(TAG, "Cleaned up all marked-for-deletion records")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning marked records: ${e.message}")
            throw e
        }
    }

    /**
     * Delete marked records in a specific collection if grace period expired.
     */
    private suspend fun deleteMarkedRecordsInCollection(
        collectionName: String,
        now: Date
    ) {
        try {
            val policy = DataRetentionPolicy.default()
            val snapshot = firestore.collection(collectionName)
                .whereEqualTo("markedForDeletion", true)
                .whereLessThanOrEqualTo("deletionScheduledAt", now)
                .limit(policy.batchDeleteSize.toLong())
                .get()
                .await()

            if (snapshot.isEmpty) {
                return
            }

            val batch = firestore.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)

                // Log deletion to auditLogs
                logDeletion(collectionName, doc.id, doc.data?.get("deletionReason") as? String ?: "")
            }

            batch.commit().await()
            Log.d(TAG, "Deleted ${snapshot.documents.size} expired records from $collectionName")
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting marked records in $collectionName: ${e.message}")
        }
    }

    /**
     * Process pending user deletion requests.
     * Check if grace period expired and execute full deletion.
     */
    private suspend fun processPendingDeletions(policy: DataRetentionPolicy) {
        try {
            val now = System.currentTimeMillis()

            // Query users with pending deletion requests
            val snapshot = firestore.collection("users")
                .whereEqualTo("markedForDeletion", true)
                .get()
                .await()

            for (userDoc in snapshot.documents) {
                val scheduledDeleteAt = (userDoc.get("deletionScheduledAt") as? Long) ?: continue

                if (now >= scheduledDeleteAt) {
                    // Grace period expired - delete user data
                    deleteUserData(userDoc.id, policy)
                }
            }

            Log.d(TAG, "Processed pending user deletions")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing pending deletions: ${e.message}")
        }
    }

    /**
     * Delete all data for a user (after grace period).
     * Cascading deletion across all collections.
     */
    private suspend fun deleteUserData(userId: String, policy: DataRetentionPolicy) {
        try {
            Log.d(TAG, "Deleting all data for user: $userId")

            val batch = firestore.batch()

            // Delete from all user-related collections
            val collectionsToDelete = listOf(
                "medicines",
                "healthLogs",
                "prescriptions",
                "orders",
                "appointments",
                "conversations",
                "messages",
                "doctorNotes",
                "medicineIntakes",
                "refillAlerts",
                "riskScores",
                "transactions",
                "carts",
                "notificationPreferences",
                "deletionRequests"
            )

            for (collectionName in collectionsToDelete) {
                val snapshot = firestore.collection(collectionName)
                    .whereEqualTo("userId", userId)
                    .limit(policy.batchDeleteSize.toLong())
                    .get()
                    .await()

                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }
            }

            // Delete user document itself
            batch.delete(firestore.collection("users").document(userId))

            batch.commit().await()

            // Log complete user deletion
            firestore.collection("auditLogs").document().set(mapOf(
                "timestamp" to Date(),
                "action" to "USER_DELETED",
                "userId" to userId,
                "reason" to "Data retention policy - grace period expired"
            )).await()

            Log.d(TAG, "Completed deletion for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user data for $userId: ${e.message}")
            // Log failure but continue (don't crash worker)
        }
    }

    /**
     * Log record deletion to auditLogs for compliance.
     */
    private suspend fun logDeletion(
        collectionName: String,
        documentId: String,
        reason: String
    ) {
        try {
            firestore.collection("auditLogs").document().set(mapOf(
                "timestamp" to Date(),
                "action" to "RECORD_DELETED",
                "collectionName" to collectionName,
                "documentId" to documentId,
                "reason" to reason,
                "deletedBy" to "RetentionCleanupWorker"
            )).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log deletion: ${e.message}")
        }
    }
}
