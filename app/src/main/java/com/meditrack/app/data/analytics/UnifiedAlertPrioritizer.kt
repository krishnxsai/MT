package com.meditrack.app.data.analytics

import java.util.Date

/**
 * Unifies alerts from all analytics engines into a single prioritized list.
 *
 * Maps disparate priority systems (AlertSeverity, TrendPriority, FactorSeverity,
 * SuggestionPriority) into a common UnifiedPriority scale, deduplicates by
 * alert ID prefix, and sorts by priority descending.
 */
object UnifiedAlertPrioritizer {

    enum class UnifiedPriority(val rank: Int) {
        INFORMATIONAL(0),
        LOW(1),
        MODERATE(2),
        HIGH(3),
        URGENT(4),
        EMERGENCY(5)
    }

    enum class AlertSource {
        VITAL_READING,
        TREND_DETECTION,
        RISK_SCORE,
        INSIGHT_ENGINE,
        CORRELATION_RULE
    }

    data class UnifiedAlert(
        val id: String,
        val title: String,
        val message: String,
        val priority: UnifiedPriority,
        val source: AlertSource,
        val actionType: HealthInsightEngine.ActionType? = null,
        val timestamp: Date = Date()
    )

    fun prioritize(
        riskResult: RiskScoreEngine.RiskScoreResult,
        trendResult: TrendPredictionEngine.TrendAnalysisResult,
        vitalAlerts: VitalAlertEngine.AlertSummary,
        suggestions: List<HealthInsightEngine.HealthSuggestion>
    ): List<UnifiedAlert> {
        val alerts = mutableListOf<UnifiedAlert>()

        // 1. Vital reading alerts
        for (alert in vitalAlerts.alerts) {
            val priority = when (alert.severity) {
                VitalAlertEngine.AlertSeverity.CRITICAL -> UnifiedPriority.EMERGENCY
                VitalAlertEngine.AlertSeverity.WARNING -> UnifiedPriority.HIGH
                VitalAlertEngine.AlertSeverity.NORMAL -> UnifiedPriority.LOW
            }
            alerts.add(
                UnifiedAlert(
                    id = "vital_${alert.vitalType.name.lowercase()}",
                    title = alert.title,
                    message = alert.message,
                    priority = priority,
                    source = AlertSource.VITAL_READING
                )
            )
        }

        // 2. Trend alerts
        for (alert in trendResult.alerts) {
            val priority = when (alert.priority) {
                TrendPredictionEngine.TrendPriority.URGENT -> UnifiedPriority.URGENT
                TrendPredictionEngine.TrendPriority.HIGH -> UnifiedPriority.HIGH
                TrendPredictionEngine.TrendPriority.MEDIUM -> UnifiedPriority.MODERATE
                TrendPredictionEngine.TrendPriority.LOW -> UnifiedPriority.LOW
            }
            alerts.add(
                UnifiedAlert(
                    id = "trend_${alert.vitalType.name.lowercase()}_${alert.direction.name.lowercase()}",
                    title = "${alert.vitalType.displayName} Trend",
                    message = alert.message,
                    priority = priority,
                    source = AlertSource.TREND_DETECTION
                )
            )
        }

        // 3. Correlation rule factors
        for (factor in riskResult.contributingFactors) {
            if (factor.factor in riskResult.correlationRulesTriggered) {
                val priority = when (factor.severity) {
                    RiskScoreEngine.FactorSeverity.DANGER -> UnifiedPriority.URGENT
                    RiskScoreEngine.FactorSeverity.WARNING -> UnifiedPriority.MODERATE
                    RiskScoreEngine.FactorSeverity.INFO -> UnifiedPriority.INFORMATIONAL
                }
                alerts.add(
                    UnifiedAlert(
                        id = "rule_${factor.factor.lowercase().replace(' ', '_')}",
                        title = factor.factor,
                        message = factor.description,
                        priority = priority,
                        source = AlertSource.CORRELATION_RULE
                    )
                )
            }
        }

        // 4. Insight suggestions (only HIGH and URGENT)
        for (suggestion in suggestions) {
            val priority = when (suggestion.priority) {
                HealthInsightEngine.SuggestionPriority.URGENT -> UnifiedPriority.EMERGENCY
                HealthInsightEngine.SuggestionPriority.HIGH -> UnifiedPriority.HIGH
                HealthInsightEngine.SuggestionPriority.MEDIUM -> UnifiedPriority.MODERATE
                HealthInsightEngine.SuggestionPriority.LOW -> UnifiedPriority.LOW
            }
            alerts.add(
                UnifiedAlert(
                    id = "insight_${suggestion.title.lowercase().replace(' ', '_').take(30)}",
                    title = suggestion.title,
                    message = suggestion.description,
                    priority = priority,
                    source = AlertSource.INSIGHT_ENGINE,
                    actionType = suggestion.actionType
                )
            )
        }

        // Deduplicate: group by ID prefix (before last _), keep highest priority
        val deduplicated = alerts
            .groupBy { it.id.substringBeforeLast('_', it.id) }
            .map { (_, group) -> group.maxByOrNull { it.priority.rank }!! }

        return deduplicated.sortedByDescending { it.priority.rank }
    }
}
