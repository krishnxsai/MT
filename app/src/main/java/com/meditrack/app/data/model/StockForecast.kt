package com.meditrack.app.data.model

/**
 * StockForecast - Represents a predicted medicine stock level for a future day
 *
 * Used in pharmacy inventory management for proactive ordering.
 * Predictions generated using ARIMA(1,1,1) time-series model on 90-day order history.
 *
 * @param medicineCategory Category or aggregation label (e.g., "All Medicines", "Antibiotics")
 * @param dayAhead Days in the future (1-14 for forecast horizon)
 * @param predictedUnits Predicted total demand (units) for this day
 * @param confidenceMin Lower bound of 95% confidence interval (units)
 * @param confidenceMax Upper bound of 95% confidence interval (units)
 * @param modelAccuracy References MAE from model training (for context in UI)
 */
data class StockForecast(
    val medicineCategory: String,
    val dayAhead: Int,
    val predictedUnits: Int,
    val confidenceMin: Int,
    val confidenceMax: Int,
    val modelAccuracy: Double  // MAE in units
)

/**
 * ModelMetrics - Aggregated accuracy metrics from ARIMA model training
 *
 * Helps pharmacy staff understand model reliability at a glance.
 *
 * @param mae Mean Absolute Error (units) - Average magnitude of prediction error
 * @param rmse Root Mean Squared Error (units) - Penalizes larger errors more
 * @param mape Mean Absolute Percentage Error (%) - Relative error across different scales
 * @param trainingDataPoints Number of days in training set (72 days)
 * @param testDataPoints Number of days in test set (18 days)
 * @param modelName Full model identifier (e.g., "ARIMA(1,1,1)")
 */
data class ModelMetrics(
    val mae: Double,
    val rmse: Double,
    val mape: Double,
    val trainingDataPoints: Int,
    val testDataPoints: Int,
    val modelName: String = "ARIMA(1,1,1)",
    val generatedAtMS: Long = System.currentTimeMillis()
)

/**
 * 14-Day Forecast bundle with metadata
 *
 * Represents the complete forecast output ready for UI consumption.
 */
data class ForecastBundle(
    val forecasts: List<StockForecast>,  // 14 items for days 1-14
    val metrics: ModelMetrics,
    val dataQualityScore: Double = 0.85  // 85% data quality (90*85% = 77 days effective)
)
