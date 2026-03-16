package com.meditrack.app.data.analytics

import com.meditrack.app.data.model.HealthLog

/**
 * Medical-grade vital alert engine.
 *
 * Evaluates **individual** health log readings (not averages) against
 * clinically-informed thresholds and returns real-time alert badges.
 *
 * Architecture:
 * ┌──────────────┐     ┌──────────────────┐     ┌──────────────┐
 * │  HealthLog   │────▶│  VitalAlertEngine │────▶│  VitalAlert  │
 * │ (single read)│     │  (rule evaluator) │     │ (badge ready)│
 * └──────────────┘     └──────────────────┘     └──────────────┘
 *
 * Risk categorization:
 *   CRITICAL  → Immediate danger, hypertensive crisis, severe hypo/hyperglycemia
 *   WARNING   → Abnormal but not immediately life-threatening
 *   NORMAL    → Within healthy reference range
 */
object VitalAlertEngine {

    // ── Thresholds ──────────────────────────────────────────────

    object BPThresholds {
        // Hypertensive crisis
        const val SYSTOLIC_CRITICAL_HIGH = 180
        const val DIASTOLIC_CRITICAL_HIGH = 120
        // Stage 2 hypertension
        const val SYSTOLIC_HIGH = 140
        const val DIASTOLIC_HIGH = 90
        // Normal lower bound
        const val SYSTOLIC_LOW = 90
        const val DIASTOLIC_LOW = 60
        // Dangerously low
        const val SYSTOLIC_CRITICAL_LOW = 80
        const val DIASTOLIC_CRITICAL_LOW = 50
    }

    object GlucoseThresholds {
        const val CRITICAL_HIGH = 300.0   // Diabetic emergency
        const val HIGH = 200.0            // Very high
        const val ELEVATED = 126.0        // Diabetic range (fasting)
        const val NORMAL_LOW = 70.0       // Hypoglycemia boundary
        const val CRITICAL_LOW = 54.0     // Severe hypoglycemia
    }

    object HeartRateThresholds {
        const val CRITICAL_HIGH = 150     // Tachycardia - danger zone
        const val HIGH = 120              // Elevated
        const val ELEVATED = 100          // Above normal resting
        const val LOW = 60                // Bradycardia boundary
        const val CRITICAL_LOW = 40       // Dangerously low
    }

    object TemperatureThresholds {
        const val CRITICAL_HIGH = 39.5    // High fever
        const val HIGH = 38.0             // Fever
        const val LOW = 36.0              // Hypothermia boundary
        const val CRITICAL_LOW = 35.0     // Severe hypothermia
    }

    // ── Data classes ────────────────────────────────────────────

    data class VitalAlert(
        val vitalType: VitalType,
        val severity: AlertSeverity,
        val title: String,
        val message: String,
        val value: String,
        val referenceRange: String
    )

    enum class VitalType {
        BLOOD_PRESSURE,
        GLUCOSE,
        HEART_RATE,
        TEMPERATURE
    }

    enum class AlertSeverity {
        NORMAL,
        WARNING,
        CRITICAL;

        /** Return the highest severity between two alerts */
        fun max(other: AlertSeverity): AlertSeverity =
            if (this.ordinal >= other.ordinal) this else other
    }

    data class AlertSummary(
        val overallSeverity: AlertSeverity,
        val alerts: List<VitalAlert>,
        val criticalCount: Int,
        val warningCount: Int
    )

    // ── Public API ──────────────────────────────────────────────

    /**
     * Evaluate a single health log entry and return all applicable alerts.
     */
    fun evaluateReading(log: HealthLog): AlertSummary {
        val alerts = mutableListOf<VitalAlert>()

        evaluateBloodPressure(log)?.let { alerts.add(it) }
        evaluateGlucose(log)?.let { alerts.add(it) }
        evaluateHeartRate(log)?.let { alerts.add(it) }
        evaluateTemperature(log)?.let { alerts.add(it) }

        val criticalCount = alerts.count { it.severity == AlertSeverity.CRITICAL }
        val warningCount = alerts.count { it.severity == AlertSeverity.WARNING }
        val overall = when {
            criticalCount > 0 -> AlertSeverity.CRITICAL
            warningCount > 0 -> AlertSeverity.WARNING
            else -> AlertSeverity.NORMAL
        }

        return AlertSummary(
            overallSeverity = overall,
            alerts = alerts,
            criticalCount = criticalCount,
            warningCount = warningCount
        )
    }

    /**
     * Evaluate a batch of logs and return the most severe summary.
     * Useful for dashboard badge display.
     */
    fun evaluateLatestReadings(logs: List<HealthLog>, count: Int = 3): AlertSummary {
        val recentLogs = logs.sortedByDescending { it.date }.take(count)
        val allAlerts = recentLogs.flatMap { evaluateReading(it).alerts }

        // Deduplicate by vital type, keeping highest severity
        val deduplicated = allAlerts
            .groupBy { it.vitalType }
            .map { (_, typeAlerts) -> typeAlerts.maxByOrNull { it.severity.ordinal }!! }

        val criticalCount = deduplicated.count { it.severity == AlertSeverity.CRITICAL }
        val warningCount = deduplicated.count { it.severity == AlertSeverity.WARNING }
        val overall = when {
            criticalCount > 0 -> AlertSeverity.CRITICAL
            warningCount > 0 -> AlertSeverity.WARNING
            else -> AlertSeverity.NORMAL
        }

        return AlertSummary(
            overallSeverity = overall,
            alerts = deduplicated,
            criticalCount = criticalCount,
            warningCount = warningCount
        )
    }

    /**
     * Quick badge-level check: return highest severity from recent logs.
     */
    fun getBadgeSeverity(logs: List<HealthLog>): AlertSeverity {
        return evaluateLatestReadings(logs).overallSeverity
    }

    // ── Individual vital evaluators ─────────────────────────────

    private fun evaluateBloodPressure(log: HealthLog): VitalAlert? {
        val sys = log.bloodPressureSystolic ?: return null
        val dia = log.bloodPressureDiastolic ?: return null

        val (severity, title, message) = when {
            sys >= BPThresholds.SYSTOLIC_CRITICAL_HIGH || dia >= BPThresholds.DIASTOLIC_CRITICAL_HIGH ->
                Triple(AlertSeverity.CRITICAL,
                    "Hypertensive Crisis",
                    "BP $sys/$dia mmHg — seek immediate medical attention")
            sys <= BPThresholds.SYSTOLIC_CRITICAL_LOW || dia <= BPThresholds.DIASTOLIC_CRITICAL_LOW ->
                Triple(AlertSeverity.CRITICAL,
                    "Dangerously Low BP",
                    "BP $sys/$dia mmHg — risk of shock")
            sys >= BPThresholds.SYSTOLIC_HIGH || dia >= BPThresholds.DIASTOLIC_HIGH ->
                Triple(AlertSeverity.WARNING,
                    "High Blood Pressure",
                    "BP $sys/$dia mmHg — stage 2 hypertension range")
            sys < BPThresholds.SYSTOLIC_LOW || dia < BPThresholds.DIASTOLIC_LOW ->
                Triple(AlertSeverity.WARNING,
                    "Low Blood Pressure",
                    "BP $sys/$dia mmHg — may cause dizziness")
            else -> return null
        }

        return VitalAlert(
            vitalType = VitalType.BLOOD_PRESSURE,
            severity = severity,
            title = title,
            message = message,
            value = "$sys/$dia mmHg",
            referenceRange = "90-120 / 60-80 mmHg"
        )
    }

    private fun evaluateGlucose(log: HealthLog): VitalAlert? {
        val glucose = log.glucoseLevel ?: return null

        val (severity, title, message) = when {
            glucose >= GlucoseThresholds.CRITICAL_HIGH ->
                Triple(AlertSeverity.CRITICAL,
                    "Severe Hyperglycemia",
                    "Glucose ${glucose.toInt()} mg/dL — diabetic emergency")
            glucose <= GlucoseThresholds.CRITICAL_LOW ->
                Triple(AlertSeverity.CRITICAL,
                    "Severe Hypoglycemia",
                    "Glucose ${glucose.toInt()} mg/dL — immediate sugar intake required")
            glucose >= GlucoseThresholds.HIGH ->
                Triple(AlertSeverity.WARNING,
                    "Very High Glucose",
                    "Glucose ${glucose.toInt()} mg/dL — consult your doctor")
            glucose >= GlucoseThresholds.ELEVATED ->
                Triple(AlertSeverity.WARNING,
                    "Elevated Glucose",
                    "Glucose ${glucose.toInt()} mg/dL — diabetic fasting range")
            glucose < GlucoseThresholds.NORMAL_LOW ->
                Triple(AlertSeverity.WARNING,
                    "Low Glucose",
                    "Glucose ${glucose.toInt()} mg/dL — hypoglycemia range")
            else -> return null
        }

        return VitalAlert(
            vitalType = VitalType.GLUCOSE,
            severity = severity,
            title = title,
            message = message,
            value = "${glucose.toInt()} mg/dL",
            referenceRange = "70-100 mg/dL (fasting)"
        )
    }

    private fun evaluateHeartRate(log: HealthLog): VitalAlert? {
        val hr = log.heartRate ?: return null

        val (severity, title, message) = when {
            hr >= HeartRateThresholds.CRITICAL_HIGH ->
                Triple(AlertSeverity.CRITICAL,
                    "Dangerous Tachycardia",
                    "Heart rate $hr bpm — seek immediate care")
            hr <= HeartRateThresholds.CRITICAL_LOW ->
                Triple(AlertSeverity.CRITICAL,
                    "Dangerous Bradycardia",
                    "Heart rate $hr bpm — dangerously low")
            hr >= HeartRateThresholds.HIGH ->
                Triple(AlertSeverity.WARNING,
                    "Elevated Heart Rate",
                    "Heart rate $hr bpm — tachycardia range")
            hr >= HeartRateThresholds.ELEVATED ->
                Triple(AlertSeverity.WARNING,
                    "Above Normal Heart Rate",
                    "Heart rate $hr bpm — above resting normal")
            hr < HeartRateThresholds.LOW ->
                Triple(AlertSeverity.WARNING,
                    "Low Heart Rate",
                    "Heart rate $hr bpm — bradycardia range")
            else -> return null
        }

        return VitalAlert(
            vitalType = VitalType.HEART_RATE,
            severity = severity,
            title = title,
            message = message,
            value = "$hr bpm",
            referenceRange = "60-100 bpm (resting)"
        )
    }

    private fun evaluateTemperature(log: HealthLog): VitalAlert? {
        val temp = log.temperature ?: return null

        val (severity, title, message) = when {
            temp >= TemperatureThresholds.CRITICAL_HIGH ->
                Triple(AlertSeverity.CRITICAL,
                    "High Fever",
                    "Temperature ${"%.1f".format(temp)}°C — seek medical attention")
            temp <= TemperatureThresholds.CRITICAL_LOW ->
                Triple(AlertSeverity.CRITICAL,
                    "Severe Hypothermia",
                    "Temperature ${"%.1f".format(temp)}°C — dangerously low")
            temp >= TemperatureThresholds.HIGH ->
                Triple(AlertSeverity.WARNING,
                    "Fever Detected",
                    "Temperature ${"%.1f".format(temp)}°C — monitor closely")
            temp < TemperatureThresholds.LOW ->
                Triple(AlertSeverity.WARNING,
                    "Low Temperature",
                    "Temperature ${"%.1f".format(temp)}°C — below normal")
            else -> return null
        }

        return VitalAlert(
            vitalType = VitalType.TEMPERATURE,
            severity = severity,
            title = title,
            message = message,
            value = "${"%.1f".format(temp)}°C",
            referenceRange = "36.1-37.2°C"
        )
    }
}

