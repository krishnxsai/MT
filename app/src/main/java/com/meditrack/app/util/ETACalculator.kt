package com.meditrack.app.util

import com.google.android.gms.maps.model.LatLng
import com.meditrack.app.data.model.DeliveryTracking
import com.google.maps.android.SphericalUtil

/**
 * Calculate Estimated Time of Arrival (ETA) for delivery.
 *
 * Uses hybrid approach:
 * - CLIENT-SIDE: Instant calculation based on distance and speed
 * - SERVER-SIDE: Cloud Function validates for anomalies
 *
 * This provides instant feedback to patient while maintaining accuracy.
 */
object ETACalculator {

    /**
     * Calculate ETA in hybrid mode.
     *
     * Uses Directions API distance if available (accurate route-based),
     * otherwise uses straight-line distance as fallback.
     *
     * @param deliveryTracking Current delivery person tracking data (position, speed)
     * @param patientLocation Patient's delivery destination
     * @param routeDistance Distance in meters from Directions API (nullable)
     * @return ETA as string (e.g., "5 min") or null if cannot calculate
     */
    fun calculateETAHybrid(
        deliveryTracking: DeliveryTracking?,
        patientLocation: LatLng?,
        routeDistance: Int? = null
    ): String? {
        if (deliveryTracking == null || patientLocation == null) {
            return null
        }

        try {
            val deliveryPoint = deliveryTracking.toLatLng()

            // Use Directions API distance if available, otherwise straight-line
            val distanceMeters = if (routeDistance != null && routeDistance > 0) {
                routeDistance.toDouble()
            } else {
                SphericalUtil.computeDistanceBetween(deliveryPoint, patientLocation)
            }

            // Use speed from tracking data
            val speedKmh = if (deliveryTracking.speed > 0) {
                deliveryTracking.speed
            } else {
                20.0  // Default fallback: 20 km/h
            }

            // Calculate ETA in minutes
            val etaMinutes = ((distanceMeters / 1000.0) / speedKmh * 60).toInt().coerceAtLeast(1)

            return "$etaMinutes min"
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Calculate detailed ETA information (minutes, meters, km/h).
     *
     * Useful for debugging or detailed UI display.
     */
    data class ETADetails(
        val etaMinutes: Int,
        val distanceMeters: Int,
        val speedKmh: Double,
        val isEstimate: Boolean  // true if using fallback values
    )

    fun calculateETADetails(
        deliveryTracking: DeliveryTracking?,
        patientLocation: LatLng?,
        routeDistance: Int? = null
    ): ETADetails? {
        if (deliveryTracking == null || patientLocation == null) {
            return null
        }

        try {
            val deliveryPoint = deliveryTracking.toLatLng()

            // Distance
            val distanceMeters = if (routeDistance != null && routeDistance > 0) {
                routeDistance
            } else {
                SphericalUtil.computeDistanceBetween(deliveryPoint, patientLocation).toInt()
            }

            // Speed
            val speedKmh = if (deliveryTracking.speed > 0) {
                deliveryTracking.speed
            } else {
                20.0  // Fallback
            }

            // ETA
            val etaMinutes = ((distanceMeters / 1000.0) / speedKmh * 60).toInt().coerceAtLeast(1)

            // Check if using fallback values
            val isEstimate = deliveryTracking.speed == 0.0 || routeDistance == null

            return ETADetails(
                etaMinutes = etaMinutes,
                distanceMeters = distanceMeters,
                speedKmh = speedKmh,
                isEstimate = isEstimate
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Format ETA for display with context.
     *
     * Examples:
     * - "5 min away"
     * - "Less than 1 min"
     * - "About 15 min"
     */
    fun formatETAForDisplay(etaMinutes: Int?): String {
        return when (etaMinutes) {
            null -> "Calculating..."
            0 -> "Less than 1 min"
            1 -> "About ${etaMinutes} min"
            else -> "About ${etaMinutes} min"
        }
    }

    /**
     * Check if ETA seems reasonable (for validation/anomaly detection).
     *
     * Called by Cloud Function or client-side validation.
     * Returns false if values are anomalous (e.g., impossible speeds).
     */
    fun isETAReasonable(
        speedKmh: Double,
        distanceMeters: Int,
        etaMinutes: Int
    ): Boolean {
        // Speed sanity checks
        if (speedKmh < 0 || speedKmh > 120) return false  // 0-120 km/h reasonable for delivery

        // Distance sanity checks
        if (distanceMeters < 0 || distanceMeters > 200000) return false  // 0-200 km

        // ETA sanity checks
        if (etaMinutes < 0 || etaMinutes > 300) return false  // 0-300 minutes

        // Cross-check: distance / speed / time should be consistent
        val calculatedMinutes = (distanceMeters / 1000.0) / speedKmh * 60
        val diff = Math.abs(calculatedMinutes - etaMinutes.toDouble())

        // Allow 5% difference for rounding
        return diff < (calculatedMinutes * 0.05)
    }
}
