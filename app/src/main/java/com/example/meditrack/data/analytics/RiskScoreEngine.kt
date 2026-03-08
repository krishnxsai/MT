package com.example.meditrack.data.analytics

import com.example.meditrack.data.model.HealthLog
import com.example.meditrack.data.model.Medicine
import java.util.Calendar
import java.util.Date

/**
 * Composite health risk scoring engine.
 *
 * Evaluates multiple dimensions of patient health data to produce
 * a single 0–100 risk score with contributing factors.
 *
 * Dimensions:
 *   1. Vital Signs (BP, HR, Glucose, Temp)  — 40% weight
 *   2. Medication Adherence                  — 25% weight
 *   3. Symptom Burden                        — 20% weight
 *   4. Data Recency & Completeness           — 15% weight
 *
 * Architecture:
 * ┌────────────┐  ┌────────────┐  ┌──────────┐
 * │ HealthLogs │  │ Medicines  │  │ Intakes  │
 * └─────┬──────┘  └─────┬──────┘  └────┬─────┘
 *       │               │              │
 *       └───────────┬───┴──────────────┘
 *                   ▼
 *          ┌────────────────┐
 *          │ RiskScoreEngine │
 *          └───────┬────────┘
 *                  ▼
 *         ┌─────────────────┐
 *         │ RiskScoreResult  │
 *         └─────────────────┘
 */
object RiskScoreEngine {

    // ── Weight constants ────────────────────────────────────────
    private const val WEIGHT_VITALS = 0.40
    private const val WEIGHT_ADHERENCE = 0.25
    private const val WEIGHT_SYMPTOMS = 0.20
    private const val WEIGHT_DATA_QUALITY = 0.15

    // ── Data classes ────────────────────────────────────────────

    enum class RiskCategory {
        LOW,       // 0–25
        MODERATE,  // 26–50
        HIGH,      // 51–75
        CRITICAL   // 76–100
    }

    data class RiskScoreResult(
        val overallScore: Int,           // 0–100
        val category: RiskCategory,
        val subscores: Map<String, Int>, // dimension → 0–100
        val contributingFactors: List<ContributingFactor>,
        val timestamp: Date = Date()
    )

    data class ContributingFactor(
        val factor: String,
        val severity: FactorSeverity,
        val description: String
    )

    enum class FactorSeverity {
        INFO,
        WARNING,
        DANGER
    }

    // ── Public API ──────────────────────────────────────────────

    /**
     * Compute the composite risk score from recent health data.
     *
     * @param logs       Health logs (ideally last 7–30 days, sorted by date desc)
     * @param medicines  Active medicines with reminder schedules
     * @param adherencePercentage  Actual adherence 0–100 (from MedicineIntakeRepository)
     */
    fun calculateRiskScore(
        logs: List<HealthLog>,
        medicines: List<Medicine>,
        adherencePercentage: Float = -1f
    ): RiskScoreResult {
        val factors = mutableListOf<ContributingFactor>()

        val vitalScore = calculateVitalScore(logs, factors)
        val adherenceScore = calculateAdherenceScore(medicines, adherencePercentage, factors)
        val symptomScore = calculateSymptomScore(logs, factors)
        val dataQualityScore = calculateDataQualityScore(logs, factors)

        val weighted = (
                vitalScore * WEIGHT_VITALS +
                        adherenceScore * WEIGHT_ADHERENCE +
                        symptomScore * WEIGHT_SYMPTOMS +
                        dataQualityScore * WEIGHT_DATA_QUALITY
                ).toInt().coerceIn(0, 100)

        val category = when {
            weighted >= 76 -> RiskCategory.CRITICAL
            weighted >= 51 -> RiskCategory.HIGH
            weighted >= 26 -> RiskCategory.MODERATE
            else -> RiskCategory.LOW
        }

        // Add combined risk factors
        if (vitalScore > 60 && adherenceScore > 50) {
            factors.add(
                ContributingFactor(
                    "Combined Risk",
                    FactorSeverity.DANGER,
                    "Abnormal vitals combined with poor medication adherence significantly increases health risk"
                )
            )
        }

        return RiskScoreResult(
            overallScore = weighted,
            category = category,
            subscores = mapOf(
                "Vitals" to vitalScore.toInt(),
                "Adherence" to adherenceScore.toInt(),
                "Symptoms" to symptomScore.toInt(),
                "Data Quality" to dataQualityScore.toInt()
            ),
            contributingFactors = factors.sortedByDescending { it.severity.ordinal }
        )
    }

    // ── Vital Signs Scoring ─────────────────────────────────────

    private fun calculateVitalScore(
        logs: List<HealthLog>,
        factors: MutableList<ContributingFactor>
    ): Double {
        if (logs.isEmpty()) return 30.0 // Moderate risk if no data

        val recentLogs = logs.sortedByDescending { it.date }.take(10)
        var score = 0.0
        var count = 0

        // Blood Pressure scoring
        val bpScores = recentLogs.mapNotNull { log ->
            if (log.bloodPressureSystolic != null && log.bloodPressureDiastolic != null) {
                scoreBP(log.bloodPressureSystolic, log.bloodPressureDiastolic)
            } else null
        }
        if (bpScores.isNotEmpty()) {
            val avgBP = bpScores.average()
            score += avgBP
            count++
            if (avgBP > 60) {
                factors.add(
                    ContributingFactor(
                        "Blood Pressure",
                        if (avgBP > 80) FactorSeverity.DANGER else FactorSeverity.WARNING,
                        "Blood pressure readings are consistently abnormal"
                    )
                )
            }
        }

        // Heart Rate scoring
        val hrScores = recentLogs.mapNotNull { log ->
            log.heartRate?.let { scoreHeartRate(it) }
        }
        if (hrScores.isNotEmpty()) {
            val avgHR = hrScores.average()
            score += avgHR
            count++
            if (avgHR > 60) {
                factors.add(
                    ContributingFactor(
                        "Heart Rate",
                        if (avgHR > 80) FactorSeverity.DANGER else FactorSeverity.WARNING,
                        "Heart rate readings are outside normal range"
                    )
                )
            }
        }

        // Glucose scoring
        val glucoseScores = recentLogs.mapNotNull { log ->
            log.glucoseLevel?.let { scoreGlucose(it) }
        }
        if (glucoseScores.isNotEmpty()) {
            val avgGlucose = glucoseScores.average()
            score += avgGlucose
            count++
            if (avgGlucose > 60) {
                factors.add(
                    ContributingFactor(
                        "Glucose Level",
                        if (avgGlucose > 80) FactorSeverity.DANGER else FactorSeverity.WARNING,
                        "Blood glucose levels indicate potential risk"
                    )
                )
            }
        }

        // Temperature scoring
        val tempScores = recentLogs.mapNotNull { log ->
            log.temperature?.let { scoreTemperature(it) }
        }
        if (tempScores.isNotEmpty()) {
            val avgTemp = tempScores.average()
            score += avgTemp
            count++
            if (avgTemp > 50) {
                factors.add(
                    ContributingFactor(
                        "Temperature",
                        if (avgTemp > 70) FactorSeverity.DANGER else FactorSeverity.WARNING,
                        "Body temperature readings are abnormal"
                    )
                )
            }
        }

        return if (count > 0) (score / count) else 30.0
    }

    private fun scoreBP(systolic: Int, diastolic: Int): Double {
        return when {
            systolic >= 180 || diastolic >= 120 -> 100.0  // Hypertensive crisis
            systolic >= 160 || diastolic >= 100 -> 85.0   // Stage 2+
            systolic >= 140 || diastolic >= 90 -> 70.0    // Stage 1
            systolic >= 130 || diastolic >= 85 -> 50.0    // Elevated
            systolic < 80 || diastolic < 50 -> 80.0       // Dangerously low
            systolic < 90 || diastolic < 60 -> 55.0       // Low
            else -> 5.0                                    // Normal
        }
    }

    private fun scoreHeartRate(hr: Int): Double {
        return when {
            hr >= 150 -> 95.0
            hr >= 120 -> 75.0
            hr >= 100 -> 50.0
            hr < 40 -> 90.0
            hr < 50 -> 65.0
            hr < 60 -> 35.0
            else -> 5.0
        }
    }

    private fun scoreGlucose(glucose: Double): Double {
        return when {
            glucose >= 300 -> 100.0
            glucose >= 200 -> 80.0
            glucose >= 126 -> 60.0
            glucose >= 100 -> 35.0
            glucose < 54 -> 95.0
            glucose < 70 -> 65.0
            else -> 5.0
        }
    }

    private fun scoreTemperature(temp: Double): Double {
        return when {
            temp >= 40.0 -> 90.0
            temp >= 39.0 -> 70.0
            temp >= 38.0 -> 50.0
            temp >= 37.5 -> 30.0
            temp < 35.0 -> 80.0
            temp < 36.0 -> 45.0
            else -> 5.0
        }
    }

    // ── Adherence Scoring ───────────────────────────────────────

    private fun calculateAdherenceScore(
        medicines: List<Medicine>,
        adherencePercentage: Float,
        factors: MutableList<ContributingFactor>
    ): Double {
        val activeMeds = medicines.filter { it.isActive }
        if (activeMeds.isEmpty()) return 0.0 // No meds = no adherence risk

        val effectiveAdherence = if (adherencePercentage >= 0) {
            adherencePercentage
        } else {
            // Fallback: use today's schedule-based estimate
            val stats = HealthAnalytics.calculateTodayAdherence(medicines)
            stats.adherencePercentage
        }

        // Invert: 100% adherence = 0 risk, 0% adherence = 100 risk
        val score = (100.0 - effectiveAdherence).coerceIn(0.0, 100.0)

        if (score > 50) {
            factors.add(
                ContributingFactor(
                    "Medication Adherence",
                    if (score > 75) FactorSeverity.DANGER else FactorSeverity.WARNING,
                    "Only ${effectiveAdherence.toInt()}% of medications taken — missing doses increases health risk"
                )
            )
        }

        // Low stock contributes to risk
        val lowStockCount = activeMeds.count { it.isLowStock }
        if (lowStockCount > 0) {
            factors.add(
                ContributingFactor(
                    "Low Medicine Stock",
                    FactorSeverity.WARNING,
                    "$lowStockCount medicine${if (lowStockCount > 1) "s" else ""} running low — refill needed"
                )
            )
        }

        return score
    }

    // ── Symptom Scoring ─────────────────────────────────────────

    private fun calculateSymptomScore(
        logs: List<HealthLog>,
        factors: MutableList<ContributingFactor>
    ): Double {
        if (logs.isEmpty()) return 10.0

        val recentLogs = logs.sortedByDescending { it.date }.take(14)
        val allSymptoms = recentLogs.flatMap { it.symptoms }

        if (allSymptoms.isEmpty()) return 5.0

        // Severity weighting for specific symptoms
        val severeSymptoms = listOf("Chest pain", "Shortness of breath")
        val moderateSymptoms = listOf("Dizziness", "Fever", "Nausea")

        val severeCount = allSymptoms.count { it in severeSymptoms }
        val moderateCount = allSymptoms.count { it in moderateSymptoms }
        val mildCount = allSymptoms.size - severeCount - moderateCount

        val rawScore = (severeCount * 25.0 + moderateCount * 12.0 + mildCount * 5.0)
            .coerceIn(0.0, 100.0)

        // Frequency factor: symptoms in many logs = worse
        val logsWithSymptoms = recentLogs.count { it.symptoms.isNotEmpty() }
        val frequencyMultiplier = if (recentLogs.size > 1) {
            (logsWithSymptoms.toDouble() / recentLogs.size).coerceIn(0.3, 1.5)
        } else 1.0

        val finalScore = (rawScore * frequencyMultiplier).coerceIn(0.0, 100.0)

        if (severeCount > 0) {
            factors.add(
                ContributingFactor(
                    "Severe Symptoms",
                    FactorSeverity.DANGER,
                    "Severe symptoms reported: ${allSymptoms.filter { it in severeSymptoms }.distinct().joinToString()}"
                )
            )
        }

        if (logsWithSymptoms >= 3) {
            factors.add(
                ContributingFactor(
                    "Frequent Symptoms",
                    FactorSeverity.WARNING,
                    "Symptoms reported in $logsWithSymptoms out of ${recentLogs.size} recent logs"
                )
            )
        }

        return finalScore
    }

    // ── Data Quality Scoring ────────────────────────────────────

    private fun calculateDataQualityScore(
        logs: List<HealthLog>,
        factors: MutableList<ContributingFactor>
    ): Double {
        if (logs.isEmpty()) {
            factors.add(
                ContributingFactor(
                    "No Health Data",
                    FactorSeverity.WARNING,
                    "No health logs recorded — monitoring is essential for accurate risk assessment"
                )
            )
            return 50.0 // Unknown = moderate concern
        }

        // Check recency: how old is the latest log?
        val latestLog = logs.maxByOrNull { it.date }
        val daysSinceLastLog = if (latestLog != null) {
            val diff = Date().time - latestLog.date.time
            (diff / (1000 * 60 * 60 * 24)).toInt()
        } else 30

        val recencyScore = when {
            daysSinceLastLog <= 1 -> 5.0
            daysSinceLastLog <= 3 -> 15.0
            daysSinceLastLog <= 7 -> 30.0
            daysSinceLastLog <= 14 -> 50.0
            else -> 70.0
        }

        if (daysSinceLastLog > 7) {
            factors.add(
                ContributingFactor(
                    "Stale Data",
                    FactorSeverity.WARNING,
                    "Last health log was $daysSinceLastLog days ago — please log your vitals regularly"
                )
            )
        }

        // Check completeness over last 7 days
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -7)
        val weekAgo = calendar.time
        val weekLogs = logs.filter { it.date.after(weekAgo) }

        if (weekLogs.size < 2) {
            factors.add(
                ContributingFactor(
                    "Insufficient Data",
                    FactorSeverity.INFO,
                    "Only ${weekLogs.size} log(s) this week — more data improves accuracy"
                )
            )
        }

        return recencyScore
    }
}

