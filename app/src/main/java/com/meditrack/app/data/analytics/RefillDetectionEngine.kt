package com.meditrack.app.data.analytics

import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.RefillUrgency
import com.meditrack.app.data.model.RepeatType

/**
 * Engine for analyzing medicine stock levels and detecting refill needs.
 * Calculates urgency levels based on current stock and consumption patterns.
 */
object RefillDetectionEngine {

    /** Days threshold for urgent alerts */
    private const val URGENT_DAYS_THRESHOLD = 2

    /**
     * Calculate the urgency level for a medicine based on its stock and consumption pattern.
     *
     * @param medicine The medicine to analyze
     * @return The urgency level for refill alerts
     */
    fun calculateUrgency(medicine: Medicine): RefillUrgency {
        if (!medicine.isRefillTrackingEnabled) {
            return RefillUrgency.NONE
        }

        val currentQty = medicine.currentQuantity
        val threshold = medicine.lowStockThreshold
        val dailyConsumption = calculateDailyConsumption(medicine)
        val daysLeft = estimateDaysRemaining(medicine, dailyConsumption)

        return when {
            currentQty <= 0 -> RefillUrgency.OUT_OF_STOCK
            daysLeft <= URGENT_DAYS_THRESHOLD -> RefillUrgency.URGENT
            currentQty <= threshold -> RefillUrgency.LOW
            else -> RefillUrgency.NONE
        }
    }

    /**
     * Calculate the average daily consumption based on reminder schedule.
     *
     * @param medicine The medicine to analyze
     * @return Estimated number of units consumed per day
     */
    fun calculateDailyConsumption(medicine: Medicine): Double {
        val timesPerDay = medicine.reminderTimes.size.coerceAtLeast(1)

        return when (medicine.repeatType) {
            RepeatType.DAILY -> timesPerDay.toDouble()
            RepeatType.WEEKLY -> timesPerDay / 7.0
            RepeatType.AS_NEEDED -> 0.5 // Conservative estimate: half a dose per day
        }
    }

    /**
     * Estimate the number of days until stock depletes.
     *
     * @param medicine The medicine to analyze
     * @return Estimated days remaining, or -1 if not tracked
     */
    fun estimateDaysRemaining(medicine: Medicine): Int {
        if (!medicine.isRefillTrackingEnabled) {
            return -1
        }
        return estimateDaysRemaining(medicine, calculateDailyConsumption(medicine))
    }

    /**
     * Estimate the number of days until stock depletes with pre-calculated consumption.
     */
    private fun estimateDaysRemaining(medicine: Medicine, dailyConsumption: Double): Int {
        if (!medicine.isRefillTrackingEnabled || dailyConsumption <= 0) {
            return medicine.currentQuantity.coerceAtLeast(0)
        }
        return (medicine.currentQuantity / dailyConsumption).toInt()
    }

    /**
     * Get a human-readable description of the stock status.
     *
     * @param medicine The medicine to describe
     * @return A short status string for display
     */
    fun getStockStatusText(medicine: Medicine): String {
        if (!medicine.isRefillTrackingEnabled) {
            return "Not tracked"
        }

        val qty = medicine.currentQuantity
        val unit = medicine.unit.ifEmpty { "units" }
        val daysLeft = estimateDaysRemaining(medicine)

        return when {
            qty <= 0 -> "Out of stock"
            daysLeft <= 0 -> "Out of stock"
            daysLeft == 1 -> "Only $qty $unit left (~1 day)"
            daysLeft <= URGENT_DAYS_THRESHOLD -> "Only $qty $unit left (~$daysLeft days)"
            else -> "$qty $unit left (~$daysLeft days)"
        }
    }

    /**
     * Get the alert title based on urgency level.
     */
    fun getAlertTitle(urgency: RefillUrgency): String {
        return when (urgency) {
            RefillUrgency.OUT_OF_STOCK -> "URGENT — OUT OF STOCK"
            RefillUrgency.URGENT -> "URGENT — LOW STOCK"
            RefillUrgency.LOW -> "LOW STOCK ALERT"
            RefillUrgency.NONE -> ""
        }
    }

    /**
     * Get the action button text based on urgency level.
     */
    fun getActionButtonText(urgency: RefillUrgency): String {
        return when (urgency) {
            RefillUrgency.OUT_OF_STOCK -> "Order Now"
            RefillUrgency.URGENT -> "Order Now"
            RefillUrgency.LOW -> "Order Refill"
            RefillUrgency.NONE -> "Order"
        }
    }

    /**
     * Check if a medicine needs an urgent refill (out of stock or will run out soon).
     */
    fun needsUrgentRefill(medicine: Medicine): Boolean {
        val urgency = calculateUrgency(medicine)
        return urgency == RefillUrgency.OUT_OF_STOCK || urgency == RefillUrgency.URGENT
    }

    /**
     * Filter a list of medicines to only those needing refills.
     *
     * @param medicines List of medicines to filter
     * @return List of medicines sorted by urgency (most urgent first)
     */
    fun filterLowStockMedicines(medicines: List<Medicine>): List<Medicine> {
        return medicines
            .filter { it.isRefillTrackingEnabled && it.isLowStock }
            .sortedByDescending { calculateUrgency(it).ordinal }
    }

    /**
     * Data class to hold medicine with its calculated refill information.
     */
    data class MedicineRefillInfo(
        val medicine: Medicine,
        val urgency: RefillUrgency,
        val daysRemaining: Int,
        val dailyConsumption: Double,
        val stockPercentage: Int,
        val statusText: String
    )

    /**
     * Analyze a medicine and return comprehensive refill information.
     */
    fun analyzeMedicine(medicine: Medicine): MedicineRefillInfo {
        val dailyConsumption = calculateDailyConsumption(medicine)
        val daysRemaining = estimateDaysRemaining(medicine)
        val urgency = calculateUrgency(medicine)

        return MedicineRefillInfo(
            medicine = medicine,
            urgency = urgency,
            daysRemaining = daysRemaining,
            dailyConsumption = dailyConsumption,
            stockPercentage = medicine.stockPercentage,
            statusText = getStockStatusText(medicine)
        )
    }

    /**
     * Analyze multiple medicines and return refill information for low-stock items.
     */
    fun analyzeLowStockMedicines(medicines: List<Medicine>): List<MedicineRefillInfo> {
        return medicines
            .filter { it.isRefillTrackingEnabled }
            .map { analyzeMedicine(it) }
            .filter { it.urgency != RefillUrgency.NONE }
            .sortedByDescending { it.urgency.ordinal }
    }
}
