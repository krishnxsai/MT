package com.meditrack.app.data.repository

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.meditrack.app.data.model.HealthLog
import com.meditrack.app.data.model.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Repository for cloud risk scoring via Firebase Callable Function.
 *
 * The callable is implemented in the core project and can forward requests to a
 * Vertex endpoint hosted in a separate billing project.
 */
class VertexRiskRepository @Inject constructor() {

    companion object {
        private const val TAG = "VertexRiskRepository"
        private const val FUNCTION_NAME = "scoreRiskWithVertex"
    }

    data class VertexRiskPrediction(
        val provider: String,
        val risk: String,
        val reason: String,
        val score: Int,
        val fallbackUsed: Boolean
    )

    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance() }

    suspend fun scoreRisk(
        latestLog: HealthLog?,
        adherencePercentage: Float
    ): Resource<VertexRiskPrediction> = withContext(Dispatchers.IO) {
        try {
            val payload = buildMap<String, Any> {
                put("adherence", adherencePercentage.coerceIn(0f, 100f).toDouble())
                latestLog?.bloodPressureSystolic?.let { put("bpSystolic", it) }
                latestLog?.bloodPressureDiastolic?.let { put("bpDiastolic", it) }
                latestLog?.glucoseLevel?.let { put("glucose", it) }
                latestLog?.heartRate?.let { put("heartRate", it) }
                latestLog?.temperature?.let { put("temperature", it) }
            }

            @Suppress("UNCHECKED_CAST")
            val response = functions
                .getHttpsCallable(FUNCTION_NAME)
                .call(payload)
                .await()
                .getData() as? Map<String, Any?>
                ?: return@withContext Resource.Error("Invalid cloud risk response")

            val provider = response["provider"] as? String ?: "unknown"
            val risk = response["risk"] as? String ?: "MODERATE"
            val reason = response["reason"] as? String ?: "Cloud risk response received"
            val score = (response["score"] as? Number)?.toInt() ?: 50
            val fallbackUsed = response["fallbackUsed"] as? Boolean ?: false

            Resource.Success(
                VertexRiskPrediction(
                    provider = provider,
                    risk = risk,
                    reason = reason,
                    score = score.coerceIn(0, 100),
                    fallbackUsed = fallbackUsed
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "scoreRisk error: ${e.message}", e)
            Resource.Error(e.message ?: "Failed to score risk in cloud")
        }
    }
}
