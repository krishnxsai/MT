package com.example.meditrack.data.analytics

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.meditrack.data.model.HealthLog
import com.example.meditrack.data.model.Medicine

/**
 * Manages predictive alert notifications with debouncing.
 *
 * Prevents re-alerting for the same condition within a 24-hour window
 * using SharedPreferences for persistence.
 */
class PredictiveAlertManager(context: Context) {

    companion object {
        private const val TAG = "PredictiveAlertManager"
        private const val PREFS_NAME = "predictive_alerts"
        private const val DEBOUNCE_MILLIS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Data classes ────────────────────────────────────────────

    data class PredictiveAlert(
        val id: String,
        val title: String,
        val message: String,
        val severity: AlertLevel,
        val category: String
    )

    enum class AlertLevel {
        INFO,
        WARNING,
        CRITICAL
    }

    // ── Public API ──────────────────────────────────────────────

    /**
     * Evaluate current data and return new (non-debounced) alerts.
     */
    fun evaluateAndGetNewAlerts(
        logs: List<HealthLog>,
        medicines: List<Medicine>,
        adherencePercentage: Float
    ): List<PredictiveAlert> {
        val allAlerts = mutableListOf<PredictiveAlert>()

        // 1. Risk score alerts
        val riskResult = RiskScoreEngine.calculateRiskScore(logs, medicines, adherencePercentage)
        when (riskResult.category) {
            RiskScoreEngine.RiskCategory.CRITICAL -> {
                allAlerts.add(
                    PredictiveAlert(
                        id = "risk_critical",
                        title = "Critical Health Risk",
                        message = "Your health risk score is ${riskResult.overallScore}/100. Please consult a doctor.",
                        severity = AlertLevel.CRITICAL,
                        category = "risk"
                    )
                )
            }
            RiskScoreEngine.RiskCategory.HIGH -> {
                allAlerts.add(
                    PredictiveAlert(
                        id = "risk_high",
                        title = "Elevated Health Risk",
                        message = "Your risk score is ${riskResult.overallScore}/100. Consider scheduling a check-up.",
                        severity = AlertLevel.WARNING,
                        category = "risk"
                    )
                )
            }
            else -> {} // No alert for moderate/low
        }

        // 2. Trend alerts
        val trendResult = TrendPredictionEngine.analyzeAllTrends(logs)
        for (alert in trendResult.alerts) {
            if (alert.priority == TrendPredictionEngine.TrendPriority.URGENT ||
                alert.priority == TrendPredictionEngine.TrendPriority.HIGH
            ) {
                allAlerts.add(
                    PredictiveAlert(
                        id = "trend_${alert.vitalType.name}_${alert.direction.name}",
                        title = "${alert.vitalType.displayName} Trend Alert",
                        message = alert.message,
                        severity = if (alert.priority == TrendPredictionEngine.TrendPriority.URGENT)
                            AlertLevel.CRITICAL else AlertLevel.WARNING,
                        category = "trend"
                    )
                )
            }
        }

        // 3. Adherence alert
        if (adherencePercentage in 0f..50f) {
            allAlerts.add(
                PredictiveAlert(
                    id = "adherence_low",
                    title = "Low Medication Adherence",
                    message = "Only ${adherencePercentage.toInt()}% of medications taken. Missing doses affects treatment.",
                    severity = AlertLevel.WARNING,
                    category = "adherence"
                )
            )
        }

        // Filter out debounced alerts
        return allAlerts.filter { shouldAlert(it.id) }
    }

    /**
     * Mark an alert as fired (starts the debounce window).
     */
    fun markAlertFired(alertId: String) {
        prefs.edit()
            .putLong("last_fired_$alertId", System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Alert fired: $alertId")
    }

    /**
     * Check if enough time has passed since the last alert of this type.
     */
    fun shouldAlert(alertId: String): Boolean {
        val lastFired = prefs.getLong("last_fired_$alertId", 0L)
        val elapsed = System.currentTimeMillis() - lastFired
        return elapsed >= DEBOUNCE_MILLIS
    }

    /**
     * Clear all debounce records.
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}

