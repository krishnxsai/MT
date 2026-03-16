package com.meditrack.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.meditrack.app.data.model.RefillAlert
import com.meditrack.app.data.model.RefillUrgency
import com.meditrack.app.data.model.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Repository for managing refill alerts in Firestore.
 * Handles CRUD operations for the `refillAlerts` collection.
 */
class RefillAlertRepository {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val alertsCollection by lazy { firestore.collection("refillAlerts") }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    /**
     * Create a new refill alert.
     */
    suspend fun createAlert(alert: RefillAlert): Resource<String> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

            val docRef = alertsCollection.document()
            val alertWithId = alert.copy(
                id = docRef.id,
                userId = userId
            )

            val data = alertWithId.toMap().toMutableMap()
            data["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

            docRef.set(data).await()

            Resource.Success(docRef.id)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to create alert", e)
        }
    }

    /**
     * Get the active (non-dismissed) alert for a specific medicine.
     */
    suspend fun getActiveAlert(medicineId: String): RefillAlert? = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext null

            val snapshot = alertsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("medicineId", medicineId)
                .whereEqualTo("isDismissed", false)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            snapshot.documents.firstOrNull()?.toObject(RefillAlert::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get all active alerts for the current user.
     */
    suspend fun getActiveAlerts(): Resource<List<RefillAlert>> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

            val snapshot = alertsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isDismissed", false)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()

            val alerts = snapshot.documents.mapNotNull { doc ->
                doc.toObject(RefillAlert::class.java)
            }.sortedByDescending { it.urgencyLevel.ordinal }

            Resource.Success(alerts)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get alerts", e)
        }
    }

    /**
     * Get a real-time flow of active alerts for the current user.
     */
    fun getActiveAlertsFlow(): Flow<Resource<List<RefillAlert>>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            trySend(Resource.Error("User not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = alertsCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("isDismissed", false)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to get alerts"))
                    return@addSnapshotListener
                }

                val alerts = snapshot?.documents
                    ?.mapNotNull { it.toObject(RefillAlert::class.java) }
                    ?.sortedByDescending { it.urgencyLevel.ordinal }
                    ?: emptyList()

                trySend(Resource.Success(alerts))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Dismiss an alert for a medicine.
     */
    suspend fun dismissAlert(medicineId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

            val snapshot = alertsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("medicineId", medicineId)
                .whereEqualTo("isDismissed", false)
                .get()
                .await()

            for (doc in snapshot.documents) {
                doc.reference.update(
                    mapOf(
                        "isDismissed" to true,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to dismiss alert", e)
        }
    }

    /**
     * Mark an alert as read.
     */
    suspend fun markAlertRead(alertId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            alertsCollection.document(alertId).update(
                mapOf(
                    "isRead" to true,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to mark alert as read", e)
        }
    }

    /**
     * Mark an alert as actioned when user places an order.
     */
    suspend fun markOrderPlaced(medicineId: String, orderId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

            val snapshot = alertsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("medicineId", medicineId)
                .whereEqualTo("isDismissed", false)
                .get()
                .await()

            for (doc in snapshot.documents) {
                doc.reference.update(
                    mapOf(
                        "actionTakenAt" to Date(),
                        "orderId" to orderId,
                        "isDismissed" to true,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update alert", e)
        }
    }

    /**
     * Create or update an alert for a medicine based on its current stock status.
     */
    suspend fun createOrUpdateAlert(
        medicineId: String,
        medicineName: String,
        medicineDosage: String,
        currentQuantity: Int,
        estimatedDaysLeft: Int,
        urgencyLevel: RefillUrgency
    ): Resource<String> = withContext(Dispatchers.IO) {
        try {
            // Check if there's already an active alert for this medicine
            val existingAlert = getActiveAlert(medicineId)

            if (existingAlert != null) {
                // Update existing alert if urgency changed
                if (existingAlert.urgencyLevel != urgencyLevel ||
                    existingAlert.currentQuantity != currentQuantity) {
                    alertsCollection.document(existingAlert.id).update(
                        mapOf(
                            "currentQuantity" to currentQuantity,
                            "estimatedDaysLeft" to estimatedDaysLeft,
                            "urgencyLevel" to urgencyLevel.name,
                            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                Resource.Success(existingAlert.id)
            } else {
                // Create new alert
                val newAlert = RefillAlert(
                    medicineId = medicineId,
                    medicineName = medicineName,
                    medicineDosage = medicineDosage,
                    currentQuantity = currentQuantity,
                    estimatedDaysLeft = estimatedDaysLeft,
                    urgencyLevel = urgencyLevel
                )
                createAlert(newAlert)
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to create/update alert", e)
        }
    }

    /**
     * Delete all alerts for a specific medicine (when medicine is deleted).
     */
    suspend fun deleteAlertsForMedicine(medicineId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

            val snapshot = alertsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("medicineId", medicineId)
                .get()
                .await()

            for (doc in snapshot.documents) {
                doc.reference.delete().await()
            }

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to delete alerts", e)
        }
    }

    /**
     * Get the count of unread alerts for the current user.
     */
    suspend fun getUnreadAlertCount(): Int = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext 0

            val snapshot = alertsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isDismissed", false)
                .whereEqualTo("isRead", false)
                .get()
                .await()

            snapshot.size()
        } catch (e: Exception) {
            0
        }
    }
}
