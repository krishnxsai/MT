package com.meditrack.app.util

import android.util.Log
import com.meditrack.app.data.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Stock Prediction Helper
 *
 * Handles feature engineering and time-series analysis for medicine stock prediction.
 * Features are extracted from historical inventory data to train the ML model.
 */
object StockPredictionHelper {

    private const val TAG = "StockPredictionHelper"
    private const val MIN_DATA_POINTS = 7  // Need at least 7 days of data

    /**
     * Engineer features from historical stock data
     *
     * @param timeSeries List of historical stock data points (oldest first)
     * @param currentDate Today's date (for day-of-week calculation)
     * @param minStockLevel Pharmacy's reorder threshold
     * @return MLFeatures ready for model input, or null if insufficient data
     */
    fun engineerFeatures(
        timeSeries: List<StockDataPoint>,
        currentDate: LocalDate = LocalDate.now(),
        minStockLevel: Int = 10
    ): MLFeatures? {
        if (timeSeries.size < MIN_DATA_POINTS) {
            Log.w(TAG, "Insufficient data points: ${timeSeries.size} < $MIN_DATA_POINTS")
            return null
        }

        // 1. Calculate daily consumption rate
        val dailyConsumptionRate = calculateDailyConsumption(timeSeries)

        // 2. Calculate trend (slope of best-fit line)
        val trend = calculateTrend(timeSeries)

        // 3. Calculate volatility (std dev of consumption)
        val volatility = calculateVolatility(timeSeries)

        // 4. Day of week encoding (0-6, Monday-Sunday)
        val dayOfWeek = currentDate.dayOfWeek.value % 7  // Convert to 0-6

        // 5. Seasonality factor (weekday vs weekend, high-demand periods)
        val seasonalityFactor = calculateSeasonalityFactor(timeSeries, currentDate)

        // 6. Current stock level
        val currentStock = timeSeries.lastOrNull()?.stockLevel ?: 0

        // 7. Min stock observed
        val minStock = timeSeries.minOf { it.stockLevel }

        // 8. Days of stock remaining at current consumption
        val daysRemaining = if (dailyConsumptionRate > 0) {
            (currentStock.toDouble() - minStockLevel) / dailyConsumptionRate
        } else {
            Double.POSITIVE_INFINITY
        }

        return MLFeatures(
            currentStock = currentStock,
            dailyConsumptionRate = dailyConsumptionRate,
            trend = trend,
            volatility = volatility,
            dayOfWeek = dayOfWeek,
            seasonalityFactor = seasonalityFactor,
            minStockLevel = minStock,
            daysOfStockRemaining = daysRemaining
        )
    }

    /**
     * Calculate average daily consumption from time series
     */
    private fun calculateDailyConsumption(timeSeries: List<StockDataPoint>): Double {
        if (timeSeries.size < 2) return 0.0

        val totalConsumption = timeSeries.sumOf { it.consumption.toLong() }
        val days = timeSeries.size.toDouble()

        return (totalConsumption / days).toDouble()
    }

    /**
     * Calculate trend using linear regression slope (Haversine method)
     * Positive = stock increasing, Negative = stock decreasing
     * Range: -1 to 1
     */
    private fun calculateTrend(timeSeries: List<StockDataPoint>): Double {
        if (timeSeries.size < 2) return 0.0

        val n = timeSeries.size
        val xValues = (0 until n).map { it.toDouble() }
        val yValues = timeSeries.map { it.stockLevel.toDouble() }

        // Linear regression: y = ax + b, we need slope (a)
        val meanX = xValues.average()
        val meanY = yValues.average()

        val numerator = (0 until n).sumOf { i ->
            (xValues[i] - meanX) * (yValues[i] - meanY)
        }

        val denominator = (0 until n).sumOf { i ->
            (xValues[i] - meanX).pow(2)
        }

        return if (denominator != 0.0) {
            // Normalize slope to -1 to 1 range
            val slope = numerator / denominator
            val maxChange = timeSeries.maxOf { it.stockLevel } - timeSeries.minOf { it.stockLevel }

            if (maxChange > 0) slope / maxChange.toDouble() else 0.0
        } else {
            0.0
        }
    }

    /**
     * Calculate volatility (standard deviation of consumption)
     * High volatility = unpredictable demand, Low volatility = stable demand
     */
    private fun calculateVolatility(timeSeries: List<StockDataPoint>): Double {
        if (timeSeries.size < 2) return 0.0

        val consumption = timeSeries.map { it.consumption.toDouble() }
        val mean = consumption.average()

        val variance = consumption.map { (it - mean).pow(2) }.average()
        val stdDev = sqrt(variance)

        // Normalize to 0-100 scale
        return minOf((stdDev / 10.0) * 100, 100.0)  // Assume max reasonable stdDev is 10
    }

    /**
     * Calculate seasonality factor based on day of week and historical patterns
     * Returns 0.5 to 1.5
     * - 0.5 = low-demand day (e.g., Sunday)
     * - 1.0 = average demand
     * - 1.5 = high-demand day (e.g., Monday after weekend)
     */
    private fun calculateSeasonalityFactor(
        timeSeries: List<StockDataPoint>,
        referenceDate: LocalDate
    ): Double {
        if (timeSeries.size < 14) {
            // Not enough data to detect weekly pattern
            return 1.0
        }

        // Group by day of week and calculate average consumption
        val byDayOfWeek = mutableMapOf<Int, MutableList<Int>>()

        timeSeries.forEachIndexed { index, dataPoint ->
            val date = referenceDate.minus((timeSeries.size - index).toLong(), ChronoUnit.DAYS)
            val dayOfWeek = date.dayOfWeek.value % 7

            byDayOfWeek.computeIfAbsent(dayOfWeek) { mutableListOf() }
                .add(dataPoint.consumption)
        }

        // Calculate average consumption for today's day of week
        val todayDayOfWeek = referenceDate.dayOfWeek.value % 7
        val todayConsumption = byDayOfWeek[todayDayOfWeek]
            ?.let { it.takeIf { list -> list.isNotEmpty() }?.average() ?: 0.0 }
            ?: 0.0

        // Calculate overall average
        val overallAverage = timeSeries.map { it.consumption.toDouble() }.average()

        // Seasonality factor = today's avg / overall avg, clamped to 0.5-1.5
        return if (overallAverage > 0) {
            (todayConsumption / overallAverage).coerceIn(0.5, 1.5)
        } else {
            1.0
        }
    }

    /**
     * Predict stock levels for the next N days using simple linear extrapolation
     * (This is the basic model before ML; actual predictions use trained model)
     *
     * @param features Engineered features
     * @param daysAhead Number of days to predict
     * @return List of predicted stock levels
     */
    fun predictStockLevelsBasic(
        features: MLFeatures,
        daysAhead: Int = 7
    ): List<Prediction> {
        val predictions = mutableListOf<Prediction>()
        val predictions_list = mutableListOf<Int>()

        // Simple linear extrapolation: stock_day_n = current - (daily_consumption * n)
        for (day in 1..daysAhead) {
            val expectedConsumption = features.dailyConsumptionRate * day * features.seasonalityFactor
            val predictedStock = (features.currentStock - expectedConsumption).toInt()
                .coerceAtLeast(0)

            // Confidence decreases with distance (±10% per day)
            val confidence = maxOf(50.0, 100.0 - (day * 5))
            val errorMargin = (features.currentStock * 0.15).toInt().coerceAtLeast(1)

            predictions_list.add(predictedStock)
        }

        return predictions_list.mapIndexed { index, stock ->
            val day = index + 1
            val daysToRunout = if (features.dailyConsumptionRate > 0) {
                (features.currentStock - features.minStockLevel) / features.dailyConsumptionRate + day
            } else {
                Double.POSITIVE_INFINITY
            }

            Prediction(
                medicineId = "",  // Will be filled by caller
                medicineName = "",  // Will be filled by caller
                predictedStock = stock,
                confidence = maxOf(50.0, 100.0 - (day * 5)),
                upperBound = stock + (features.currentStock * 0.15).toInt(),
                lowerBound = (stock - (features.currentStock * 0.15).toInt()).coerceAtLeast(0),
                estimatedRunoutDate = LocalDate.now().plusDays(daysToRunout.toLong()),
                daysInFuture = day
            )
        }
    }

    /**
     * Calculate days until stock runs out at current consumption rate
     */
    fun calculateDaysUntilRunout(
        currentStock: Int,
        dailyConsumption: Double,
        minStockLevel: Int = 0
    ): Double {
        return if (dailyConsumption > 0) {
            (currentStock - minStockLevel) / dailyConsumption
        } else {
            Double.POSITIVE_INFINITY
        }
    }

    /**
     * Determine urgency level based on predicted runout date
     */
    fun determineUrgencyLevel(daysUntilRunout: Double): LowStockAlert.UrgencyLevel {
        return when {
            daysUntilRunout < 2 -> LowStockAlert.UrgencyLevel.IMMEDIATE
            daysUntilRunout < 4 -> LowStockAlert.UrgencyLevel.HIGH
            daysUntilRunout < 7 -> LowStockAlert.UrgencyLevel.MEDIUM
            else -> LowStockAlert.UrgencyLevel.LOW
        }
    }

    /**
     * Calculate recommended reorder quantity based on average consumption
     */
    fun calculateReorderQuantity(
        dailyConsumption: Double,
        leadTimeDays: Int = 3,
        safetyStockDays: Int = 7
    ): Int {
        val replenishmentQuantity = dailyConsumption * (leadTimeDays + safetyStockDays)
        return replenishmentQuantity.toInt().coerceAtLeast(1)
    }

    /**
     * Validate time series data quality
     */
    fun validateTimeSeries(timeSeries: List<StockDataPoint>): Boolean {
        if (timeSeries.isEmpty()) return false
        if (timeSeries.size < MIN_DATA_POINTS) return false

        // Check for extreme outliers (consumption > 10x average)
        val avgConsumption = timeSeries.map { it.consumption }.average()
        val hasOutliers = timeSeries.any {
            it.consumption > avgConsumption * 10 || it.consumption < 0
        }

        return !hasOutliers
    }

    /**
     * Remove outliers from time series data (values > 3 standard deviations)
     */
    fun removeOutliers(timeSeries: List<StockDataPoint>): List<StockDataPoint> {
        if (timeSeries.size < 3) return timeSeries

        val consumption = timeSeries.map { it.consumption.toDouble() }
        val mean = consumption.average()
        val stdDev = sqrt(consumption.map { (it - mean).pow(2) }.average())

        val threshold = 3 * stdDev

        return timeSeries.filter {
            abs(it.consumption - mean) <= threshold
        }
    }
}
