package com.meditrack.app.data.repository

import com.meditrack.app.data.model.ForecastBundle
import com.meditrack.app.data.model.ModelMetrics
import com.meditrack.app.data.model.StockForecast
import javax.inject.Inject

/**
 * StockForecastRepository - Manages ML-based stock prediction data
 *
 * **ACADEMIC CONTEXT**: This repository integrates ARIMA time-series forecasting
 * into MediTrack for proactive inventory management. The model was trained on
 * 90 days of pharmaceutical order history (633 orders, 5,935 units).
 *
 * **Model Configuration**:
 * - Algorithm: ARIMA(1,1,1)
 * - Training set: 72 days (80%), Test set: 18 days (20%)
 * - Accuracy: MAE=25.87 units, RMSE=30.44 units
 * - Stationarity: Confirmed via ADF test (p<0.001)
 *
 * **Future Enhancements** (Post-MVP):
 * - Real-time retraining on new order data (daily)
 * - Multi-product forecasting (ML models per product category)
 * - Firebase Cloud Functions for server-side predictions
 * - Storage of historical forecasts for audit trail
 */
class StockForecastRepository @Inject constructor() {

    /**
     * Get 14-day medicine stock forecast
     *
     * Current implementation: Returns hardcoded forecast from ARIMA analysis.
     * Production version would:
     * 1. Store forecast_14days.csv in Firebase Cloud Storage
     * 2. Load & parse on app launch
     * 3. Cache locally for offline access
     * 4. Update daily via background job
     *
     * @return List of 14 StockForecast objects (days 1-14 ahead)
     */
    fun getForecast14Days(): List<StockForecast> {
        // Embedded from analysis/forecast_14days.csv ARIMA output
        // Format: "Day X: 65 units [-8, 140]" where -8 is lower CI, 140 is upper CI
        return listOf(
            StockForecast("All Medicines", 1, 65, -8, 140, 25.87),
            StockForecast("All Medicines", 2, 65, -8, 140, 25.87),
            StockForecast("All Medicines", 3, 65, -8, 140, 25.87),
            StockForecast("All Medicines", 4, 65, -8, 140, 25.87),
            StockForecast("All Medicines", 5, 65, -8, 140, 25.87),
            StockForecast("All Medicines", 6, 65, -8, 140, 25.87),
            StockForecast("All Medicines", 7, 65, -8, 140, 25.87),
            StockForecast("All Medicines", 8, 65, -8, 140, 25.87),
            StockForecast("All Medicines", 9, 65, -8, 140, 25.87),
            StockForecast("All Medicines", 10, 65, -8, 140, 25.87),
            StockForecast("All Medicines", 11, 65, -8, 140, 25.87),
            StockForecast("All Medicines", 12, 65, -8, 140, 25.87),
            StockForecast("All Medicines", 13, 65, -8, 140, 25.87),
            StockForecast("All Medicines", 14, 65, -8, 140, 25.87),
        )
    }

    /**
     * Get model accuracy metrics for display in pharmacy dashboard
     *
     * @return ModelMetrics object with MAE, RMSE, MAPE, training/test split
     */
    fun getModelAccuracy(): ModelMetrics {
        return ModelMetrics(
            mae = 25.87,          // Mean Absolute Error: ~26 units off on average
            rmse = 30.44,         // Root Mean Squared Error: larger errors penalized more
            mape = 90.83,         // Mean Absolute Percentage Error: 91% relative error
            trainingDataPoints = 72,   // 80% of 90 days
            testDataPoints = 18,       // 20% of 90 days
            modelName = "ARIMA(1,1,1)",
            generatedAtMS = System.currentTimeMillis()
        )
    }

    /**
     * Get complete forecast bundle with metrics
     *
     * Convenience method for UI components that need both forecast and diagnostics.
     *
     * @return ForecastBundle containing 14-day forecasts + model metrics
     */
    fun getForecastBundle(): ForecastBundle {
        return ForecastBundle(
            forecasts = getForecast14Days(),
            metrics = getModelAccuracy(),
            dataQualityScore = 0.85  // 90 days * 85% quality = 77 effective days
        )
    }

    /**
     * Calculate recommended re-order point
     *
     * Business logic: If predicted daily demand (65 units) exceeds current stock
     * within the next 7 days, trigger re-order alert.
     *
     * @param currentStock Current stock level (units)
     * @param dayWindow Next N days to forecast (default 7 days)
     * @return True if re-order recommended, False otherwise
     */
    fun isReorderRecommended(
        currentStock: Int,
        dayWindow: Int = 7
    ): Boolean {
        val forecast = getForecast14Days().take(dayWindow)
        val totalForecastedDemand = forecast.sumOf { it.predictedUnits }

        // Re-order if current stock < forecasted demand (with safety margin)
        // Safety margin: 1.2x multiplier for 20% buffer
        return currentStock < (totalForecastedDemand * 1.2).toInt()
    }

    /**
     * Get estimated days until stockout
     *
     * Based on current stock and predicted daily demand average.
     *
     * @param currentStock Current stock level (units)
     * @return Days remaining before stockout (-1 if already below safety threshold)
     */
    fun daysUntilStockout(currentStock: Int): Int {
        val avgDailyDemand = 65  // From ARIMA forecast
        val safetyThreshold = 20 // Min required stock (units)

        if (currentStock <= safetyThreshold) return -1
        return (currentStock - safetyThreshold) / avgDailyDemand
    }
}
