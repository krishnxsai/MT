package com.meditrack.app.data.analytics

import com.meditrack.app.data.model.HealthLog
import com.meditrack.app.data.model.Medicine
import java.util.Date

/**
 * Unifying facade for the entire analytics stack.
 *
 * Provides a single entry point that orchestrates all analytics engines
 * and returns a comprehensive insight result.
 *
 * Architecture:
 * ┌──────────────────────────────────────────────────────────┐
 * │                      InsightEngine                       │
 * │  ┌─────────────┐ ┌────────────┐ ┌──────────────────┐    │
 * │  │RiskScoreEng. │ │TrendPred.  │ │HealthInsightEng. │    │
 * │  └──────┬───────┘ └─────┬──────┘ └────────┬─────────┘    │
 * │         │               │                 │              │
 * │  ┌──────┴───────┐ ┌────┴───────┐         │              │
 * │  │VitalAlertEng.│ │UnifiedAlert│◀────────┘              │
 * │  └──────────────┘ │Prioritizer │                         │
 * │                    └────────────┘                         │
 * └──────────────────────────┬───────────────────────────────┘
 *                            ▼
 *                  ComprehensiveInsight
 */
object InsightEngine {

    data class ComprehensiveInsight(
        val riskScore: RiskScoreEngine.RiskScoreResult,
        val trendAnalysis: TrendPredictionEngine.TrendAnalysisResult,
        val suggestions: List<HealthInsightEngine.HealthSuggestion>,
        val vitalAlerts: VitalAlertEngine.AlertSummary,
        val unifiedAlerts: List<UnifiedAlertPrioritizer.UnifiedAlert>,
        val computedAt: Date = Date()
    )

    /**
     * Single entry point: computes risk score, trend analysis, suggestions,
     * vital alerts, and unified alert prioritization from raw data.
     *
     * @param logs       All health logs (will be filtered to last 30 days internally)
     * @param medicines  Active medicines with reminder schedules
     * @param adherencePercentage  Actual adherence 0–100 (from MedicineIntakeRepository)
     */
    fun computeFullInsight(
        logs: List<HealthLog>,
        medicines: List<Medicine>,
        adherencePercentage: Float
    ): ComprehensiveInsight {
        val recentLogs = HealthAnalytics.getLogsForLastDays(logs, 30)

        val riskResult = RiskScoreEngine.calculateRiskScore(
            logs = recentLogs,
            medicines = medicines,
            adherencePercentage = adherencePercentage
        )

        val trendResult = TrendPredictionEngine.analyzeAllTrends(recentLogs)

        val suggestions = HealthInsightEngine.generateInsights(
            riskScore = riskResult,
            trendAnalysis = trendResult,
            logs = recentLogs,
            medicines = medicines,
            adherencePercentage = adherencePercentage
        )

        val vitalAlerts = if (recentLogs.isNotEmpty()) {
            VitalAlertEngine.evaluateLatestReadings(recentLogs)
        } else {
            VitalAlertEngine.AlertSummary(
                overallSeverity = VitalAlertEngine.AlertSeverity.NORMAL,
                alerts = emptyList(),
                criticalCount = 0,
                warningCount = 0
            )
        }

        val unifiedAlerts = UnifiedAlertPrioritizer.prioritize(
            riskResult, trendResult, vitalAlerts, suggestions
        )

        return ComprehensiveInsight(
            riskScore = riskResult,
            trendAnalysis = trendResult,
            suggestions = suggestions,
            vitalAlerts = vitalAlerts,
            unifiedAlerts = unifiedAlerts
        )
    }
}
