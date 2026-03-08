package com.example.meditrack.data.analytics

import com.example.meditrack.data.model.HealthLog
import com.example.meditrack.data.model.Medicine

/**
 * AI-style health insight & suggestion generator.
 *
 * Uses rule-based logic to combine risk scores, trend alerts,
 * adherence stats, and vital data into actionable suggestions.
 *
 * Architecture:
 * ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐
 * │ RiskScoreResult  │  │ TrendAlerts      │  │ AdherenceStats│
 * └────────┬─────────┘  └────────┬─────────┘  └───────┬───────┘
 *          │                     │                     │
 *          └─────────────┬───────┴─────────────────────┘
 *                        ▼
 *              ┌────────────────────┐
 *              │ HealthInsightEngine │
 *              └─────────┬──────────┘
 *                        ▼
 *              ┌────────────────────┐
 *              │ HealthSuggestion[] │
 *              └────────────────────┘
 */
object HealthInsightEngine {

    // ── Data classes ────────────────────────────────────────────

    enum class ActionType {
        LIFESTYLE,
        MEDICATION,
        DOCTOR_VISIT,
        MONITORING,
        EMERGENCY
    }

    enum class SuggestionPriority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }

    data class HealthSuggestion(
        val title: String,
        val description: String,
        val actionType: ActionType,
        val priority: SuggestionPriority,
        val iconHint: String = "info" // Hint for UI icon selection
    )

    // ── Public API ──────────────────────────────────────────────

    /**
     * Generate comprehensive health suggestions from all available data.
     */
    fun generateInsights(
        riskScore: RiskScoreEngine.RiskScoreResult,
        trendAnalysis: TrendPredictionEngine.TrendAnalysisResult,
        logs: List<HealthLog>,
        medicines: List<Medicine>,
        adherencePercentage: Float
    ): List<HealthSuggestion> {
        val suggestions = mutableListOf<HealthSuggestion>()

        // ── Risk-based suggestions ──
        generateRiskSuggestions(riskScore, suggestions)

        // ── Trend-based suggestions ──
        generateTrendSuggestions(trendAnalysis, suggestions)

        // ── Adherence-based suggestions ──
        generateAdherenceSuggestions(adherencePercentage, medicines, suggestions)

        // ── Vital-specific suggestions ──
        generateVitalSuggestions(logs, suggestions)

        // ── Data quality suggestions ──
        generateDataQualitySuggestions(logs, suggestions)

        // ── Positive reinforcement ──
        generatePositiveFeedback(riskScore, adherencePercentage, suggestions)

        // Deduplicate by title, keep highest priority
        return suggestions
            .groupBy { it.title }
            .map { (_, group) -> group.maxByOrNull { it.priority.ordinal }!! }
            .sortedByDescending { it.priority.ordinal }
            .take(8) // Max 8 suggestions to avoid overwhelming
    }

    // ── Risk-based ──────────────────────────────────────────────

    private fun generateRiskSuggestions(
        riskScore: RiskScoreEngine.RiskScoreResult,
        suggestions: MutableList<HealthSuggestion>
    ) {
        when (riskScore.category) {
            RiskScoreEngine.RiskCategory.CRITICAL -> {
                suggestions.add(
                    HealthSuggestion(
                        title = "Seek Medical Attention",
                        description = "Your health risk score is critical (${riskScore.overallScore}/100). Please consult your doctor immediately or visit an emergency room.",
                        actionType = ActionType.EMERGENCY,
                        priority = SuggestionPriority.URGENT,
                        iconHint = "emergency"
                    )
                )
            }
            RiskScoreEngine.RiskCategory.HIGH -> {
                suggestions.add(
                    HealthSuggestion(
                        title = "Schedule a Doctor Visit",
                        description = "Your health risk score is elevated (${riskScore.overallScore}/100). Consider booking an appointment with your healthcare provider.",
                        actionType = ActionType.DOCTOR_VISIT,
                        priority = SuggestionPriority.HIGH,
                        iconHint = "doctor"
                    )
                )
            }
            RiskScoreEngine.RiskCategory.MODERATE -> {
                suggestions.add(
                    HealthSuggestion(
                        title = "Monitor Your Health Closely",
                        description = "Your risk score is moderate (${riskScore.overallScore}/100). Keep tracking your vitals and stay consistent with medications.",
                        actionType = ActionType.MONITORING,
                        priority = SuggestionPriority.MEDIUM,
                        iconHint = "monitoring"
                    )
                )
            }
            RiskScoreEngine.RiskCategory.LOW -> {
                // Positive feedback handled separately
            }
        }
    }

    // ── Trend-based ─────────────────────────────────────────────

    private fun generateTrendSuggestions(
        trendAnalysis: TrendPredictionEngine.TrendAnalysisResult,
        suggestions: MutableList<HealthSuggestion>
    ) {
        for (alert in trendAnalysis.alerts) {
            when (alert.vitalType) {
                TrendPredictionEngine.VitalType.SYSTOLIC_BP,
                TrendPredictionEngine.VitalType.DIASTOLIC_BP -> {
                    if (alert.direction == TrendPredictionEngine.TrendDirection.RISING ||
                        alert.direction == TrendPredictionEngine.TrendDirection.RISING_FAST
                    ) {
                        suggestions.add(
                            HealthSuggestion(
                                title = "Blood Pressure Rising",
                                description = "Your ${alert.vitalType.displayName} has increased ${String.format("%.1f", kotlin.math.abs(alert.percentChange7Day))}% recently. Consider reducing sodium intake, managing stress, and monitoring more frequently.",
                                actionType = ActionType.LIFESTYLE,
                                priority = if (alert.priority == TrendPredictionEngine.TrendPriority.URGENT) SuggestionPriority.URGENT else SuggestionPriority.HIGH,
                                iconHint = "bp"
                            )
                        )
                    }
                }

                TrendPredictionEngine.VitalType.GLUCOSE -> {
                    if (alert.direction == TrendPredictionEngine.TrendDirection.RISING ||
                        alert.direction == TrendPredictionEngine.TrendDirection.RISING_FAST
                    ) {
                        suggestions.add(
                            HealthSuggestion(
                                title = "Glucose Levels Trending Up",
                                description = "Blood glucose has increased ${String.format("%.1f", kotlin.math.abs(alert.percentChange7Day))}%. Consider scheduling a fasting glucose test and reviewing your diet.",
                                actionType = ActionType.MONITORING,
                                priority = if (alert.priority == TrendPredictionEngine.TrendPriority.URGENT) SuggestionPriority.URGENT else SuggestionPriority.HIGH,
                                iconHint = "glucose"
                            )
                        )
                    } else if (alert.direction == TrendPredictionEngine.TrendDirection.FALLING_FAST) {
                        suggestions.add(
                            HealthSuggestion(
                                title = "Glucose Dropping Rapidly",
                                description = "Blood glucose is falling rapidly. If you feel symptoms of hypoglycemia (shaking, sweating), consume fast-acting sugar immediately.",
                                actionType = ActionType.EMERGENCY,
                                priority = SuggestionPriority.URGENT,
                                iconHint = "glucose"
                            )
                        )
                    }
                }

                TrendPredictionEngine.VitalType.HEART_RATE -> {
                    if (alert.direction == TrendPredictionEngine.TrendDirection.RISING_FAST) {
                        suggestions.add(
                            HealthSuggestion(
                                title = "Heart Rate Accelerating",
                                description = "Your resting heart rate has been rising rapidly. This could indicate stress, dehydration, or cardiac concerns. Consider consulting your doctor.",
                                actionType = ActionType.DOCTOR_VISIT,
                                priority = SuggestionPriority.HIGH,
                                iconHint = "heart"
                            )
                        )
                    }
                }

                TrendPredictionEngine.VitalType.TEMPERATURE -> {
                    if (alert.direction == TrendPredictionEngine.TrendDirection.RISING ||
                        alert.direction == TrendPredictionEngine.TrendDirection.RISING_FAST
                    ) {
                        suggestions.add(
                            HealthSuggestion(
                                title = "Temperature Increasing",
                                description = "Body temperature has been trending upward. Monitor for fever symptoms and stay hydrated.",
                                actionType = ActionType.MONITORING,
                                priority = if (alert.direction == TrendPredictionEngine.TrendDirection.RISING_FAST) SuggestionPriority.HIGH else SuggestionPriority.MEDIUM,
                                iconHint = "temperature"
                            )
                        )
                    }
                }

                TrendPredictionEngine.VitalType.WEIGHT -> {
                    val changeAbs = kotlin.math.abs(alert.percentChange7Day)
                    if (changeAbs > 3) { // Significant weight change
                        val direction = if (alert.direction == TrendPredictionEngine.TrendDirection.RISING ||
                            alert.direction == TrendPredictionEngine.TrendDirection.RISING_FAST
                        ) "gain" else "loss"
                        suggestions.add(
                            HealthSuggestion(
                                title = "Significant Weight ${direction.replaceFirstChar { it.uppercase() }}",
                                description = "Your weight has changed by ${String.format("%.1f", changeAbs)}% recently. Rapid weight $direction may warrant a medical review.",
                                actionType = ActionType.MONITORING,
                                priority = SuggestionPriority.MEDIUM,
                                iconHint = "weight"
                            )
                        )
                    }
                }
            }
        }
    }

    // ── Adherence-based ─────────────────────────────────────────

    private fun generateAdherenceSuggestions(
        adherencePercentage: Float,
        medicines: List<Medicine>,
        suggestions: MutableList<HealthSuggestion>
    ) {
        val activeMeds = medicines.filter { it.isActive }
        if (activeMeds.isEmpty()) return

        when {
            adherencePercentage < 50 -> {
                suggestions.add(
                    HealthSuggestion(
                        title = "Improve Medication Adherence",
                        description = "Only ${adherencePercentage.toInt()}% of your medications have been taken. Missing doses can significantly impact treatment effectiveness. Try setting additional reminders.",
                        actionType = ActionType.MEDICATION,
                        priority = SuggestionPriority.HIGH,
                        iconHint = "medication"
                    )
                )
            }
            adherencePercentage < 80 -> {
                suggestions.add(
                    HealthSuggestion(
                        title = "Medication Reminder",
                        description = "Your adherence rate is ${adherencePercentage.toInt()}%. Try linking medicine times to daily habits like meals for better consistency.",
                        actionType = ActionType.MEDICATION,
                        priority = SuggestionPriority.MEDIUM,
                        iconHint = "medication"
                    )
                )
            }
        }

        // Low stock warning
        val lowStockMeds = activeMeds.filter { it.isLowStock }
        if (lowStockMeds.isNotEmpty()) {
            suggestions.add(
                HealthSuggestion(
                    title = "Refill Medicines Soon",
                    description = "${lowStockMeds.size} medicine${if (lowStockMeds.size > 1) "s are" else " is"} running low on stock: ${lowStockMeds.take(3).joinToString { it.name }}. Order a refill to avoid missed doses.",
                    actionType = ActionType.MEDICATION,
                    priority = SuggestionPriority.MEDIUM,
                    iconHint = "refill"
                )
            )
        }
    }

    // ── Vital-specific ──────────────────────────────────────────

    private fun generateVitalSuggestions(
        logs: List<HealthLog>,
        suggestions: MutableList<HealthSuggestion>
    ) {
        if (logs.isEmpty()) return

        val recentLogs = logs.sortedByDescending { it.date }.take(7)

        // Check for high BP + high glucose combination
        val hasHighBP = recentLogs.any { (it.bloodPressureSystolic ?: 0) >= 140 || (it.bloodPressureDiastolic ?: 0) >= 90 }
        val hasHighGlucose = recentLogs.any { (it.glucoseLevel ?: 0.0) >= 126.0 }

        if (hasHighBP && hasHighGlucose) {
            suggestions.add(
                HealthSuggestion(
                    title = "Metabolic Risk Detected",
                    description = "Both blood pressure and glucose levels are elevated. This combination increases cardiovascular risk. Please discuss a comprehensive care plan with your doctor.",
                    actionType = ActionType.DOCTOR_VISIT,
                    priority = SuggestionPriority.HIGH,
                    iconHint = "warning"
                )
            )
        }

        // Check for frequent chest pain or shortness of breath
        val severeSymptomCount = recentLogs.flatMap { it.symptoms }
            .count { it in listOf("Chest pain", "Shortness of breath") }
        if (severeSymptomCount >= 2) {
            suggestions.add(
                HealthSuggestion(
                    title = "Recurring Severe Symptoms",
                    description = "Chest pain or shortness of breath reported multiple times recently. Seek prompt medical evaluation.",
                    actionType = ActionType.EMERGENCY,
                    priority = SuggestionPriority.URGENT,
                    iconHint = "emergency"
                )
            )
        }
    }

    // ── Data quality ────────────────────────────────────────────

    private fun generateDataQualitySuggestions(
        logs: List<HealthLog>,
        suggestions: MutableList<HealthSuggestion>
    ) {
        if (logs.isEmpty()) {
            suggestions.add(
                HealthSuggestion(
                    title = "Start Tracking Your Health",
                    description = "No health data recorded yet. Begin logging your vitals daily for personalized insights and risk assessment.",
                    actionType = ActionType.MONITORING,
                    priority = SuggestionPriority.MEDIUM,
                    iconHint = "add_data"
                )
            )
            return
        }

        val daysSinceLastLog = run {
            val latest = logs.maxByOrNull { it.date } ?: return@run 30
            ((java.util.Date().time - latest.date.time) / (1000 * 60 * 60 * 24)).toInt()
        }

        if (daysSinceLastLog > 3) {
            suggestions.add(
                HealthSuggestion(
                    title = "Log Your Vitals",
                    description = "It's been $daysSinceLastLog days since your last health log. Regular tracking helps detect problems early.",
                    actionType = ActionType.MONITORING,
                    priority = SuggestionPriority.LOW,
                    iconHint = "add_data"
                )
            )
        }
    }

    // ── Positive reinforcement ──────────────────────────────────

    private fun generatePositiveFeedback(
        riskScore: RiskScoreEngine.RiskScoreResult,
        adherencePercentage: Float,
        suggestions: MutableList<HealthSuggestion>
    ) {
        if (riskScore.category == RiskScoreEngine.RiskCategory.LOW && adherencePercentage >= 90) {
            suggestions.add(
                HealthSuggestion(
                    title = "Great Job! 🎉",
                    description = "Your health risk is low and medication adherence is excellent at ${adherencePercentage.toInt()}%. Keep up the great work!",
                    actionType = ActionType.LIFESTYLE,
                    priority = SuggestionPriority.LOW,
                    iconHint = "success"
                )
            )
        } else if (adherencePercentage >= 95) {
            suggestions.add(
                HealthSuggestion(
                    title = "Excellent Adherence! ⭐",
                    description = "Your medication adherence is ${adherencePercentage.toInt()}% — outstanding consistency!",
                    actionType = ActionType.LIFESTYLE,
                    priority = SuggestionPriority.LOW,
                    iconHint = "success"
                )
            )
        }
    }
}

