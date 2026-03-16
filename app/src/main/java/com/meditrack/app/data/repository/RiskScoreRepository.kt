package com.meditrack.app.data.repository

import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.RiskScore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

class RiskScoreRepository {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val riskScoresCollection by lazy { firestore.collection("riskScores") }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    suspend fun saveRiskScore(score: RiskScore): Resource<RiskScore> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

            val scoreWithUser = score.copy(userId = userId)
            val docRef = riskScoresCollection.document()
            docRef.set(scoreWithUser.toMap()).await()

            Resource.Success(scoreWithUser.copy(id = docRef.id))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to save risk score", e)
        }
    }

    suspend fun getLatestRiskScore(): Resource<RiskScore?> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

            val snapshot = riskScoresCollection
                .whereEqualTo("userId", userId)
                .orderBy("computedAt", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            val score = snapshot.documents.firstOrNull()?.let { doc ->
                doc.toObject(RiskScore::class.java)
            }

            Resource.Success(score)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get latest risk score", e)
        }
    }

    suspend fun getRiskScoreHistory(daysBack: Int = 30): Resource<List<RiskScore>> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, -daysBack)
            val startDate = calendar.time

            val snapshot = riskScoresCollection
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("computedAt", startDate)
                .orderBy("computedAt", Query.Direction.DESCENDING)
                .limit(30)
                .get()
                .await()

            val scores = snapshot.documents.mapNotNull { doc ->
                doc.toObject(RiskScore::class.java)
            }

            Resource.Success(scores)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get risk score history", e)
        }
    }
}
