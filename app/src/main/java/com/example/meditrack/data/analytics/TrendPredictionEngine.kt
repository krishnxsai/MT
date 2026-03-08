package com.example.meditrack.data.analytics

import com.example.meditrack.data.model.HealthLog
import java.util.Date

/**
 * Trend Prediction Engine — detects consecutive rising/falling vital patterns
 * and projects near-term direction.
 *
 * Detection rules:
 *   • ≥3 consecutive readings in the same direction → Trend Alert
 *   • ≥5 consecutive → Accelerating Trend (higher priority)
 *   • Projects next value via simple linear regression on recent window
 *
 * Architecture:
 * ┌────────────────┐     ┌────────────────────────┐     ┌─────────────┐
 * │  HealthLog[]   │────▶│  TrendPredictionEngine  │────▶│ TrendAlert[]│
 * │ (time-sorted)  │     │  (pattern detector)     │     │ (actionable)│
 * └────────────────┘     └────────────────────────┘     └─────────────┘
 */
object TrendPredictionEngine {

    // ── Configuration ───────────────────────────────────────────

    /** Minimum consecutive readings to trigger a trend alert */
    private const val MIN_CONSECUTIVE = 3

    /** Minimum consecutive readings for an accelerating alert */
    private const val ACCEL_CONSECUTIVE = 5

    /** Minimum % change between readings to count as "rising" or "falling" */
    private const val CHANGE_THRESHOLD_PERCENT = 1.5

    // ── Data classes ────────────────────────────────────────────

    enum class TrendDirection {
        RISING,
        FALLING,
        RISING_FAST,   // ≥5 consecutive + steep slope
        FALLING_FAST,
        STABLE
    }

    enum class TrendPriority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }

    data class TrendAlert(
        val vitalType: VitalType,
        val direction: TrendDirection,
        val consecutiveCount: Int,
        val latestValue: Double,
        val projectedNextValue: Double,
        val percentChange7Day: Double, // % change over the analysis window
        val priority: TrendPriority,
        val message: String,
        val values: List<Double> // the recent values used for detection
    )

    enum class VitalType(val displayName: String, val unit: String) {
        HEART_RATE("Heart Rate", "bpm"),
        SYSTOLIC_BP("Systolic BP", "mmHg"),
        DIASTOLIC_BP("Diastolic BP", "mmHg"),
        GLUCOSE("Glucose", "mg/dL"),
        WEIGHT("Weight", "kg"),
        TEMPERATURE("Temperature", "°C")
    }

    data class TrendAnalysisResult(
        val alerts: List<TrendAlert>,
        val vitalTrends: Map<VitalType, TrendDirection>,
        val analysisTimestamp: Date = Date()
    )

    // ── Public API ──────────────────────────────────────────────

    /**
     * Analyze all vital types for trend patterns.
     *
     * @param logs Health logs sorted by date (oldest first preferred, will be sorted internally)
     * @return TrendAnalysisResult containing all detected trend alerts
     */
    fun analyzeAllTrends(logs: List<HealthLog>): TrendAnalysisResult {
        val sortedLogs = logs.sortedBy { it.date }
        val alerts = mutableListOf<TrendAlert>()
        val vitalTrends = mutableMapOf<VitalType, TrendDirection>()

        // Heart Rate
        val hrValues = sortedLogs.mapNotNull { it.heartRate?.toDouble() }
        analyzeSingleVital(hrValues, VitalType.HEART_RATE)?.let { alert ->
            alerts.add(alert)
            vitalTrends[VitalType.HEART_RATE] = alert.direction
        } ?: run { vitalTrends[VitalType.HEART_RATE] = TrendDirection.STABLE }

        // Systolic BP
        val sysBPValues = sortedLogs.mapNotNull { it.bloodPressureSystolic?.toDouble() }
        analyzeSingleVital(sysBPValues, VitalType.SYSTOLIC_BP)?.let { alert ->
            alerts.add(alert)
            vitalTrends[VitalType.SYSTOLIC_BP] = alert.direction
        } ?: run { vitalTrends[VitalType.SYSTOLIC_BP] = TrendDirection.STABLE }

        // Diastolic BP
        val diaBPValues = sortedLogs.mapNotNull { it.bloodPressureDiastolic?.toDouble() }
        analyzeSingleVital(diaBPValues, VitalType.DIASTOLIC_BP)?.let { alert ->
            alerts.add(alert)
            vitalTrends[VitalType.DIASTOLIC_BP] = alert.direction
        } ?: run { vitalTrends[VitalType.DIASTOLIC_BP] = TrendDirection.STABLE }

        // Glucose
        val glucoseValues = sortedLogs.mapNotNull { it.glucoseLevel }
        analyzeSingleVital(glucoseValues, VitalType.GLUCOSE)?.let { alert ->
            alerts.add(alert)
            vitalTrends[VitalType.GLUCOSE] = alert.direction
        } ?: run { vitalTrends[VitalType.GLUCOSE] = TrendDirection.STABLE }

        // Weight
        val weightValues = sortedLogs.mapNotNull { it.weight }
        analyzeSingleVital(weightValues, VitalType.WEIGHT)?.let { alert ->
            alerts.add(alert)
            vitalTrends[VitalType.WEIGHT] = alert.direction
        } ?: run { vitalTrends[VitalType.WEIGHT] = TrendDirection.STABLE }

        // Temperature
        val tempValues = sortedLogs.mapNotNull { it.temperature }
        analyzeSingleVital(tempValues, VitalType.TEMPERATURE)?.let { alert ->
            alerts.add(alert)
            vitalTrends[VitalType.TEMPERATURE] = alert.direction
        } ?: run { vitalTrends[VitalType.TEMPERATURE] = TrendDirection.STABLE }

        return TrendAnalysisResult(
            alerts = alerts.sortedByDescending { it.priority.ordinal },
            vitalTrends = vitalTrends
        )
    }

    // ── Core Analysis ───────────────────────────────────────────

    /**
     * Analyze a single vital type's values for consecutive trends.
     */
    private fun analyzeSingleVital(
        values: List<Double>,
        vitalType: VitalType
    ): TrendAlert? {
        if (values.size < MIN_CONSECUTIVE) return null

        val recentValues = values.takeLast(10) // Focus on last 10 readings
        val consecutiveResult = detectConsecutiveTrend(recentValues)

        if (consecutiveResult.count < MIN_CONSECUTIVE) return null

        val latestValue = recentValues.last()
        val projectedNext = projectNextValue(recentValues)
        val percentChange = calculatePercentChange(recentValues)

        val isAccelerating = consecutiveResult.count >= ACCEL_CONSECUTIVE
        val direction = when {
            consecutiveResult.isRising && isAccelerating -> TrendDirection.RISING_FAST
            !consecutiveResult.isRising && isAccelerating -> TrendDirection.FALLING_FAST
            consecutiveResult.isRising -> TrendDirection.RISING
            else -> TrendDirection.FALLING
        }

        val priority = determinePriority(vitalType, direction, latestValue, percentChange)
        val message = buildAlertMessage(vitalType, direction, consecutiveResult.count, percentChange)

        return TrendAlert(
            vitalType = vitalType,
            direction = direction,
            consecutiveCount = consecutiveResult.count,
            latestValue = latestValue,
            projectedNextValue = projectedNext,
            percentChange7Day = percentChange,
            priority = priority,
            message = message,
            values = recentValues
        )
    }

    private data class ConsecutiveResult(
        val count: Int,
        val isRising: Boolean
    )

    /**
     * Count the longest consecutive rising or falling streak from the end.
     */
    private fun detectConsecutiveTrend(values: List<Double>): ConsecutiveResult {
        if (values.size < 2) return ConsecutiveResult(0, true)

        var risingCount = 0
        var fallingCount = 0

        // Count consecutive rises from end
        for (i in values.size - 1 downTo 1) {
            val current = values[i]
            val previous = values[i - 1]
            val pctChange = if (previous != 0.0) ((current - previous) / previous) * 100 else 0.0

            if (pctChange > CHANGE_THRESHOLD_PERCENT) {
                risingCount++
            } else {
                break
            }
        }

        // Count consecutive falls from end
        if (risingCount < MIN_CONSECUTIVE) {
            for (i in values.size - 1 downTo 1) {
                val current = values[i]
                val previous = values[i - 1]
                val pctChange = if (previous != 0.0) ((current - previous) / previous) * 100 else 0.0

                if (pctChange < -CHANGE_THRESHOLD_PERCENT) {
                    fallingCount++
                } else {
                    break
                }
            }
        }

        return if (risingCount >= fallingCount) {
            ConsecutiveResult(risingCount, true)
        } else {
            ConsecutiveResult(fallingCount, false)
        }
    }

    /**
     * Simple linear regression to project the next value.
     */
    private fun projectNextValue(values: List<Double>): Double {
        if (values.size < 2) return values.lastOrNull() ?: 0.0

        val n = values.size
        val xMean = (n - 1) / 2.0
        val yMean = values.average()

        var numerator = 0.0
        var denominator = 0.0

        for (i in values.indices) {
            numerator += (i - xMean) * (values[i] - yMean)
            denominator += (i - xMean) * (i - xMean)
        }

        val slope = if (denominator != 0.0) numerator / denominator else 0.0
        val intercept = yMean - slope * xMean

        return slope * n + intercept
    }

    /**
     * Calculate overall percent change from first to last value.
     */
    private fun calculatePercentChange(values: List<Double>): Double {
        if (values.size < 2 || values.first() == 0.0) return 0.0
        return ((values.last() - values.first()) / values.first()) * 100.0
    }

    /**
     * Determine alert priority based on vital type and trend characteristics.
     */
    private fun determinePriority(
        vitalType: VitalType,
        direction: TrendDirection,
        latestValue: Double,
        percentChange: Double
    ): TrendPriority {
        val isRapid = direction == TrendDirection.RISING_FAST || direction == TrendDirection.FALLING_FAST
        val absChange = kotlin.math.abs(percentChange)

        // Critical vitals get higher priority
        val isCriticalVital = vitalType in listOf(
            VitalType.SYSTOLIC_BP, VitalType.DIASTOLIC_BP,
            VitalType.GLUCOSE, VitalType.HEART_RATE
        )

        return when {
            isRapid && isCriticalVital -> TrendPriority.URGENT
            isRapid || (isCriticalVital && absChange > 15) -> TrendPriority.HIGH
            isCriticalVital || absChange > 10 -> TrendPriority.MEDIUM
            else -> TrendPriority.LOW
        }
    }

    /**
     * Build a human-readable alert message.
     */
    private fun buildAlertMessage(
        vitalType: VitalType,
        direction: TrendDirection,
        consecutiveCount: Int,
        percentChange: Double
    ): String {
        val directionText = when (direction) {
            TrendDirection.RISING_FAST -> "rapidly increasing"
            TrendDirection.FALLING_FAST -> "rapidly decreasing"
            TrendDirection.RISING -> "steadily increasing"
            TrendDirection.FALLING -> "steadily decreasing"
            TrendDirection.STABLE -> "stable"
        }

        val changeText = String.format("%.1f%%", kotlin.math.abs(percentChange))

        return "${vitalType.displayName} has been $directionText for $consecutiveCount consecutive readings ($changeText change)"
    }
}

