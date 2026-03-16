package com.meditrack.app.data.model

import com.meditrack.app.data.analytics.RiskScoreEngine
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class RiskScore(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val overallScore: Int = 0,
    val category: String = "LOW",
    val vitalScore: Int = 0,
    val adherenceScore: Int = 0,
    val symptomScore: Int = 0,
    val dataQualityScore: Int = 0,
    val contributingFactorCount: Int = 0,
    val topFactor: String = "",
    val correlationRulesTriggered: List<String> = emptyList(),
    @ServerTimestamp
    val computedAt: Date? = null
) {
    constructor() : this(id = "")

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "userId" to userId,
            "overallScore" to overallScore,
            "category" to category,
            "vitalScore" to vitalScore,
            "adherenceScore" to adherenceScore,
            "symptomScore" to symptomScore,
            "dataQualityScore" to dataQualityScore,
            "contributingFactorCount" to contributingFactorCount,
            "topFactor" to topFactor,
            "correlationRulesTriggered" to correlationRulesTriggered,
            "computedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): RiskScore {
            @Suppress("UNCHECKED_CAST")
            return RiskScore(
                id = id,
                userId = map["userId"] as? String ?: "",
                overallScore = (map["overallScore"] as? Number)?.toInt() ?: 0,
                category = map["category"] as? String ?: "LOW",
                vitalScore = (map["vitalScore"] as? Number)?.toInt() ?: 0,
                adherenceScore = (map["adherenceScore"] as? Number)?.toInt() ?: 0,
                symptomScore = (map["symptomScore"] as? Number)?.toInt() ?: 0,
                dataQualityScore = (map["dataQualityScore"] as? Number)?.toInt() ?: 0,
                contributingFactorCount = (map["contributingFactorCount"] as? Number)?.toInt() ?: 0,
                topFactor = map["topFactor"] as? String ?: "",
                correlationRulesTriggered = (map["correlationRulesTriggered"] as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList(),
                computedAt = (map["computedAt"] as? com.google.firebase.Timestamp)?.toDate()
            )
        }

        fun fromResult(userId: String, result: RiskScoreEngine.RiskScoreResult): RiskScore {
            val topFactor = result.contributingFactors.firstOrNull()?.factor ?: ""
            return RiskScore(
                userId = userId,
                overallScore = result.overallScore,
                category = result.category.name,
                vitalScore = result.subscores["Vitals"] ?: 0,
                adherenceScore = result.subscores["Adherence"] ?: 0,
                symptomScore = result.subscores["Symptoms"] ?: 0,
                dataQualityScore = result.subscores["Data Quality"] ?: 0,
                contributingFactorCount = result.contributingFactors.size,
                topFactor = topFactor,
                correlationRulesTriggered = result.correlationRulesTriggered
            )
        }
    }
}
