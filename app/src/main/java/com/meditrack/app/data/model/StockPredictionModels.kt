package com.meditrack.app.data.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Represents a single data point in the stock history time series
 */
data class StockDataPoint(
    val date: LocalDate,
    val stockLevel: Int,
    val consumption: Int = 0,
    val reorderQuantity: Int = 0
)

/**
 * Features engineered from historical data for ML model training
 */
data class MLFeatures(
    val currentStock: Int,
    val dailyConsumptionRate: Double,  // Average units consumed per day
    val trend: Double,                  // Slope of stock level over time (-1 to 1)
    val volatility: Double,             // Std dev of consumption (0-100)
    val dayOfWeek: Int,                 // 0-6 (Monday-Sunday)
    val seasonalityFactor: Double,      // 0.5 to 1.5 (low to high demand days)
    val minStockLevel: Int,             // Historical minimum
    val daysOfStockRemaining: Double    // Estimated days at current consumption
)

/**
 * Stock level prediction with confidence bounds
 */
data class Prediction(
    val medicineId: String,
    val medicineName: String,
    val predictedStock: Int,
    val confidence: Double,             // 0-100 (%)
    val upperBound: Int,                // Prediction ± stdDev
    val lowerBound: Int,
    val estimatedRunoutDate: LocalDate,
    val predictionDate: LocalDateTime = LocalDateTime.now(),
    val daysInFuture: Int = 0           // How many days ahead is this prediction
)

/**
 * Alert for medicines predicted to run low soon
 */
data class LowStockAlert(
    val medicineId: String,
    val medicineName: String,
    val currentStock: Int,
    val minStockLevel: Int,
    val predictedStock7Days: Int,
    val runOutDate: LocalDate,
    val confidence: Double,             // 0-100 (%)
    val recommendedOrderDate: LocalDate,
    val recommendedOrderQuantity: Int,
    val urgencyLevel: UrgencyLevel      // IMMEDIATE, HIGH, MEDIUM, LOW
) {
    enum class UrgencyLevel {
        IMMEDIATE,  // Will run out in < 2 days
        HIGH,       // Will run out in 2-4 days
        MEDIUM,     // Will run out in 4-7 days
        LOW         // Will run out in > 7 days
    }
}

/**
 * Prediction metric for accuracy tracking and paper validation
 */
data class PredictionMetric(
    val timestamp: LocalDateTime,
    val medicineId: String,
    val medicineName: String,
    val predictedStock: Int,
    val actualStock: Int,
    val error: Int,                     // Predicted - Actual
    val absoluteError: Int,             // |Error|
    val percentError: Double,           // Error / Actual * 100
    val daysInFuture: Int,              // How many days ahead was this prediction
    val modelVersion: String = "1.0"
)

/**
 * Aggregated model accuracy metrics for paper
 */
data class ModelAccuracyMetrics(
    val totalSamples: Int,
    val rmse: Double,                   // Root Mean Squared Error
    val mae: Double,                    // Mean Absolute Error (average error magnitude)
    val mape: Double,                   // Mean Absolute Percentage Error
    val r2Score: Double,                // R² coefficient (variance explained: 0-1)
    val averageDaysInFuture: Double,    // Average prediction horizon
    val bestAccuracyAtDays: Int,        // Most accurate at N days ahead
    val predictionsByDay: Map<Int, DayAccuracy>  // Breakdown by prediction horizon
) {
    data class DayAccuracy(
        val day: Int,
        val sampleCount: Int,
        val rmse: Double,
        val mae: Double,
        val percentWithin10Percent: Double  // % of predictions within ±10%
    )
}

/**
 * Model training metadata stored in Firestore
 */
data class ModelMetadata(
    val medicineId: String,
    val lastTrained: LocalDateTime,
    val trainingDataPoints: Int,
    val trainingPeriodDays: Int,
    val modelVersion: String,
    val coefficients: Map<String, Double>,  // Feature weights
    val intercept: Double,
    val trainRmse: Double,
    val testRmse: Double,
    val r2Score: Double,
    val accuracy: Double                // (1 - testRmse / meanStockLevel)
)

/**
 * Feature importance scores (which features predict stock best)
 */
data class FeatureImportance(
    val feature: String,
    val score: Double,                  // Importance from 0-1
    val direction: FeatureDirection     // Whether feature increases/decreases stock
) {
    enum class FeatureDirection {
        INCREASES_STOCK,
        DECREASES_STOCK,
        NEUTRAL
    }
}
