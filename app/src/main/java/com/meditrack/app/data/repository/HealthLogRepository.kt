package com.meditrack.app.data.repository

import com.meditrack.app.data.model.HealthLog
import com.meditrack.app.data.model.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

class HealthLogRepository {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val healthLogsCollection by lazy { firestore.collection("healthLogs") }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    suspend fun addHealthLog(healthLog: HealthLog): Resource<HealthLog> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

            val logWithUser = healthLog.copy(userId = userId)
            val docRef = healthLogsCollection.document()
            val logToSave = logWithUser.copy(id = docRef.id)

            val data = logToSave.toMap().toMutableMap()
            data["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

            docRef.set(data).await()

            Resource.Success(logToSave)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to add health log", e)
        }
    }

    suspend fun updateHealthLog(healthLog: HealthLog): Resource<HealthLog> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

            if (healthLog.userId != userId) {
                return@withContext Resource.Error("Permission denied")
            }

            healthLogsCollection.document(healthLog.id).update(healthLog.toMap()).await()

            Resource.Success(healthLog)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update health log", e)
        }
    }

    suspend fun deleteHealthLog(logId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

            // Verify ownership before deletion
            val doc = healthLogsCollection.document(logId).get().await()
            val logUserId = doc.getString("userId")

            if (logUserId != userId) {
                return@withContext Resource.Error("Permission denied")
            }

            healthLogsCollection.document(logId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to delete health log", e)
        }
    }

    suspend fun getHealthLog(logId: String): Resource<HealthLog> = withContext(Dispatchers.IO) {
        try {
            val doc = healthLogsCollection.document(logId).get().await()

            if (doc.exists()) {
                val healthLog = doc.toObject(HealthLog::class.java)
                if (healthLog != null) {
                    Resource.Success(healthLog)
                } else {
                    Resource.Error("Failed to parse health log")
                }
            } else {
                Resource.Error("Health log not found")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get health log", e)
        }
    }

    suspend fun getHealthLogs(): Resource<List<HealthLog>> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

            val snapshot = healthLogsCollection
                .whereEqualTo("userId", userId)
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(60)
                .get()
                .await()

            val logs = snapshot.documents.mapNotNull { doc ->
                doc.toObject(HealthLog::class.java)
            }

            Resource.Success(logs)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get health logs", e)
        }
    }

    fun getHealthLogsFlow(): Flow<Resource<List<HealthLog>>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            trySend(Resource.Error("User not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = healthLogsCollection
            .whereEqualTo("userId", userId)
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

    suspend fun getHealthLogsForDateRange(startDate: Date, endDate: Date): Resource<List<HealthLog>> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

            val snapshot = healthLogsCollection
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("date", startDate)
                .whereLessThanOrEqualTo("date", endDate)
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(60)
                .get()
                .await()

            val logs = snapshot.documents.mapNotNull { doc ->
                doc.toObject(HealthLog::class.java)
            }

            Resource.Success(logs)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get health logs", e)
        }
    }

    suspend fun getTodaysLogs(): Resource<List<HealthLog>> = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.time

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.time

        getHealthLogsForDateRange(startOfDay, endOfDay)
    }
}

