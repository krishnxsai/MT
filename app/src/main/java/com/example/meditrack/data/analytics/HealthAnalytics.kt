package com.example.meditrack.data.analytics

import com.example.meditrack.data.model.HealthLog
import com.example.meditrack.data.model.Medicine
import java.util.Calendar
import java.util.Date

/**
 * Analytics module for computing health statistics
 */
object HealthAnalytics {

    data class VitalStats(
        val average: Double,
        val min: Double,
        val max: Double,
        val count: Int,
        val trend: Trend,
        val standardDeviation: Double = 0.0
    )

    enum class Trend {
        INCREASING,
        DECREASING,
        STABLE,
        INSUFFICIENT_DATA
    }

    data class AdherenceStats(
        val totalScheduled: Int,
        val taken: Int,
        val missed: Int,
        val pending: Int,
        val adherencePercentage: Float,
        val streakDays: Int = 0
    )

    data class HealthInsights(
        val heartRateStats: VitalStats?,
        val systolicBPStats: VitalStats?,
        val diastolicBPStats: VitalStats?,
        val glucoseStats: VitalStats?,
        val weightStats: VitalStats?,
        val temperatureStats: VitalStats?,
        val dataQualityScore: Float = 0f, // 0-100 score based on data completeness
        val riskAlerts: List<RiskAlert> = emptyList()
    )

    data class RiskAlert(
        val type: AlertType,
        val message: String,
        val severity: Severity
    )

    enum class AlertType {
        HIGH_BLOOD_PRESSURE,
        LOW_BLOOD_PRESSURE,
        HIGH_HEART_RATE,
        LOW_HEART_RATE,
        HIGH_GLUCOSE,
        LOW_GLUCOSE,
        ABNORMAL_TEMPERATURE,
        MISSED_MEDICATIONS
    }

    enum class Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Calculate statistics for heart rate from health logs
     */
    fun calculateHeartRateStats(logs: List<HealthLog>): VitalStats? {
        val values = logs.mapNotNull { it.heartRate?.toDouble() }
        return calculateStats(values)
    }

    /**
     * Calculate statistics for systolic blood pressure
     */
    fun calculateSystolicBPStats(logs: List<HealthLog>): VitalStats? {
        val values = logs.mapNotNull { it.bloodPressureSystolic?.toDouble() }
        return calculateStats(values)
    }

    /**
     * Calculate statistics for diastolic blood pressure
     */
    fun calculateDiastolicBPStats(logs: List<HealthLog>): VitalStats? {
        val values = logs.mapNotNull { it.bloodPressureDiastolic?.toDouble() }
        return calculateStats(values)
    }

    /**
     * Calculate statistics for glucose levels
     */
    fun calculateGlucoseStats(logs: List<HealthLog>): VitalStats? {
        val values = logs.mapNotNull { it.glucoseLevel }
        return calculateStats(values)
    }

    /**
     * Calculate statistics for weight
     */
    fun calculateWeightStats(logs: List<HealthLog>): VitalStats? {
        val values = logs.mapNotNull { it.weight }
        return calculateStats(values)
    }

    /**
     * Calculate statistics for temperature
     */
    fun calculateTemperatureStats(logs: List<HealthLog>): VitalStats? {
        val values = logs.mapNotNull { it.temperature }
        return calculateStats(values)
    }

    /**
     * Calculate all health insights from logs
     */
    fun calculateAllInsights(logs: List<HealthLog>): HealthInsights {
        val heartRateStats = calculateHeartRateStats(logs)
        val systolicBPStats = calculateSystolicBPStats(logs)
        val diastolicBPStats = calculateDiastolicBPStats(logs)
        val glucoseStats = calculateGlucoseStats(logs)
        val weightStats = calculateWeightStats(logs)
        val temperatureStats = calculateTemperatureStats(logs)

        // Calculate data quality score
        val dataQualityScore = calculateDataQualityScore(logs)

        // Generate risk alerts
        val riskAlerts = generateRiskAlerts(
            heartRateStats,
            systolicBPStats,
            diastolicBPStats,
            glucoseStats,
            temperatureStats
        )

        return HealthInsights(
            heartRateStats = heartRateStats,
            systolicBPStats = systolicBPStats,
            diastolicBPStats = diastolicBPStats,
            glucoseStats = glucoseStats,
            weightStats = weightStats,
            temperatureStats = temperatureStats,
            dataQualityScore = dataQualityScore,
            riskAlerts = riskAlerts
        )
    }

    /**
     * Calculate data quality score (0-100) based on completeness
     */
    private fun calculateDataQualityScore(logs: List<HealthLog>): Float {
        if (logs.isEmpty()) return 0f

        var totalFields = 0
        var filledFields = 0

        for (log in logs) {
            totalFields += 6 // heart rate, systolic, diastolic, glucose, weight, temperature
            if (log.heartRate != null) filledFields++
            if (log.bloodPressureSystolic != null) filledFields++
            if (log.bloodPressureDiastolic != null) filledFields++
            if (log.glucoseLevel != null) filledFields++
            if (log.weight != null) filledFields++
            if (log.temperature != null) filledFields++
        }

        return if (totalFields > 0) (filledFields.toFloat() / totalFields.toFloat()) * 100f else 0f
    }

    /**
     * Generate risk alerts based on vital statistics
     */
    private fun generateRiskAlerts(
        heartRateStats: VitalStats?,
        systolicBPStats: VitalStats?,
        diastolicBPStats: VitalStats?,
        glucoseStats: VitalStats?,
        temperatureStats: VitalStats?
    ): List<RiskAlert> {
        val alerts = mutableListOf<RiskAlert>()

        // Heart rate alerts
        heartRateStats?.let {
            when {
                it.average > 100 -> alerts.add(RiskAlert(
                    AlertType.HIGH_HEART_RATE,
                    "Average heart rate is elevated (${it.average.toInt()} bpm)",
                    if (it.average > 120) Severity.HIGH else Severity.MEDIUM
                ))
                it.average < 60 -> alerts.add(RiskAlert(
                    AlertType.LOW_HEART_RATE,
                    "Average heart rate is low (${it.average.toInt()} bpm)",
                    if (it.average < 50) Severity.HIGH else Severity.MEDIUM
                ))
            }
        }

        // Blood pressure alerts
        if (systolicBPStats != null && diastolicBPStats != null) {
            val systolicAvg = systolicBPStats.average.toInt()
            val diastolicAvg = diastolicBPStats.average.toInt()

            when {
                systolicAvg >= 140 || diastolicAvg >= 90 -> alerts.add(RiskAlert(
                    AlertType.HIGH_BLOOD_PRESSURE,
                    "Blood pressure is elevated ($systolicAvg/$diastolicAvg mmHg)",
                    if (systolicAvg >= 180 || diastolicAvg >= 120) Severity.CRITICAL else Severity.HIGH
                ))
                systolicAvg < 90 || diastolicAvg < 60 -> alerts.add(RiskAlert(
                    AlertType.LOW_BLOOD_PRESSURE,
                    "Blood pressure is low ($systolicAvg/$diastolicAvg mmHg)",
                    Severity.MEDIUM
                ))
            }
        }

        // Glucose alerts
        glucoseStats?.let {
            when {
                it.average > 126 -> alerts.add(RiskAlert(
                    AlertType.HIGH_GLUCOSE,
                    "Average glucose is high (${it.average.toInt()} mg/dL)",
                    if (it.average > 200) Severity.HIGH else Severity.MEDIUM
                ))
                it.average < 70 -> alerts.add(RiskAlert(
                    AlertType.LOW_GLUCOSE,
                    "Average glucose is low (${it.average.toInt()} mg/dL)",
                    if (it.average < 54) Severity.CRITICAL else Severity.HIGH
                ))
            }
        }

        // Temperature alerts
        temperatureStats?.let {
            when {
                it.average > 37.5 -> alerts.add(RiskAlert(
                    AlertType.ABNORMAL_TEMPERATURE,
                    "Elevated temperature detected (${String.format("%.1f", it.average)}°C)",
                    if (it.average > 38.5) Severity.HIGH else Severity.MEDIUM
                ))
                it.average < 36.0 -> alerts.add(RiskAlert(
                    AlertType.ABNORMAL_TEMPERATURE,
                    "Low temperature detected (${String.format("%.1f", it.average)}°C)",
                    Severity.MEDIUM
                ))
            }
        }

        return alerts.sortedByDescending { it.severity.ordinal }
    }

    /**
     * Calculate medicine adherence statistics for today
     */
    fun calculateTodayAdherence(medicines: List<Medicine>): AdherenceStats {
        val activeMedicines = medicines.filter { it.isActive }

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTimeMinutes = currentHour * 60 + currentMinute

        var totalScheduled = 0
        var passed = 0
        var pending = 0

        for (medicine in activeMedicines) {
            for (timeStr in medicine.reminderTimes) {
                totalScheduled++
                val parts = timeStr.split(":")
                if (parts.size == 2) {
                    val hour = parts[0].toIntOrNull() ?: continue
                    val minute = parts[1].toIntOrNull() ?: continue
                    val timeMinutes = hour * 60 + minute
                    if (timeMinutes <= currentTimeMinutes) {
                        passed++
                    } else {
                        pending++
                    }
                }
            }
        }

        // For now, assume all passed reminders were taken (in real app, track actual intake)
        val taken = passed
        val missed = 0

        val adherencePercentage = if (totalScheduled > 0) {
            (taken.toFloat() / totalScheduled.toFloat()) * 100f
        } else {
            100f
        }

        return AdherenceStats(
            totalScheduled = totalScheduled,
            taken = taken,
            missed = missed,
            pending = pending,
            adherencePercentage = adherencePercentage
        )
    }

    /**
     * Filter logs by date range
     */
    fun filterLogsByDateRange(logs: List<HealthLog>, startDate: Date, endDate: Date): List<HealthLog> {
        return logs.filter { log ->
            log.date.time >= startDate.time && log.date.time <= endDate.time
        }
    }

    /**
     * Get logs for the last N days
     */
    fun getLogsForLastDays(logs: List<HealthLog>, days: Int): List<HealthLog> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -days)
        val startDate = calendar.time
        val endDate = Date()
        return filterLogsByDateRange(logs, startDate, endDate)
    }

    private fun calculateStats(values: List<Double>): VitalStats? {
        if (values.isEmpty()) return null

        val average = values.average()
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 0.0
        val trend = calculateTrend(values)

        // Calculate standard deviation
        val variance = values.map { (it - average) * (it - average) }.average()
        val stdDev = kotlin.math.sqrt(variance)

        return VitalStats(
            average = average,
            min = min,
            max = max,
            count = values.size,
            trend = trend,
            standardDeviation = stdDev
        )
    }

    private fun calculateTrend(values: List<Double>): Trend {
        if (values.size < 3) return Trend.INSUFFICIENT_DATA

        // Simple trend calculation: compare first half average with second half average
        val midPoint = values.size / 2
        val firstHalf = values.take(midPoint)
        val secondHalf = values.drop(midPoint)

        val firstAvg = firstHalf.average()
        val secondAvg = secondHalf.average()

        val threshold = 0.05 // 5% change threshold

        return when {
            secondAvg > firstAvg * (1 + threshold) -> Trend.INCREASING
            secondAvg < firstAvg * (1 - threshold) -> Trend.DECREASING
            else -> Trend.STABLE
        }
    }

    /**
     * Get health status based on vital values
     */
    fun getHeartRateStatus(heartRate: Int): HealthStatus {
        return when {
            heartRate < 60 -> HealthStatus.LOW
            heartRate in 60..100 -> HealthStatus.NORMAL
            else -> HealthStatus.HIGH
        }
    }

    fun getBloodPressureStatus(systolic: Int, diastolic: Int): HealthStatus {
        return when {
            systolic < 90 || diastolic < 60 -> HealthStatus.LOW
            systolic in 90..120 && diastolic in 60..80 -> HealthStatus.NORMAL
            systolic in 121..139 || diastolic in 81..89 -> HealthStatus.ELEVATED
            else -> HealthStatus.HIGH
        }
    }

    fun getGlucoseStatus(glucose: Double): HealthStatus {
        return when {
            glucose < 70 -> HealthStatus.LOW
            glucose in 70.0..100.0 -> HealthStatus.NORMAL
            glucose in 100.0..125.0 -> HealthStatus.ELEVATED
            else -> HealthStatus.HIGH
        }
    }

    enum class HealthStatus {
        LOW,
        NORMAL,
        ELEVATED,
        HIGH
    }
}

