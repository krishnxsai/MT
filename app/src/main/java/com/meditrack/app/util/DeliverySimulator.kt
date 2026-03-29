package com.meditrack.app.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import com.google.firebase.firestore.FirebaseFirestore
import com.meditrack.app.data.model.DeliveryTracking
import kotlin.random.Random

/**
 * Simulates delivery person GPS movement for testing without real device.
 *
 * Usage (in OrderTrackingActivity or debug menu):
 * ```kotlin
 * val simulator = DeliverySimulator(FirebaseFirestore.getInstance())
 * simulator.simulateDelivery(
 *     orderId = "order_123",
 *     pharmacyLocation = LatLng(14.4406, 79.9864),
 *     patientLocation = LatLng(14.4426, 79.9864),
 *     durationSeconds = 120
 * )
 * ```
 *
 * Simulates:
 * - Realistic movement along interpolated path (pharmacy → patient)
 * - Variable speed (20-30 km/h with random fluctuations)
 * - Bearing changes as delivery person moves
 * - 100ms update intervals for smooth animation
 * - Location accuracy degradation to simulate real GPS noise
 */
class DeliverySimulator(private val db: FirebaseFirestore) {

    companion object {
        private const val TAG = "DeliverySimulator"
        private const val DEFAULT_SPEED_KMH_MIN = 20.0
        private const val DEFAULT_SPEED_KMH_MAX = 30.0
        private const val LOCATION_UPDATE_INTERVAL_MS = 100L
        private const val DEFAULT_ACCURACY_METERS = 8.0  // Realistic GPS accuracy
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentStep = 0
    private var simulationRunning = false
    private var lastPosition: LatLng? = null

    /**
     * Simulate delivery person traveling from pharmacy to patient.
     *
     * @param orderId Order ID to track
     * @param pharmacyLocation Starting point (pharmacy)
     * @param patientLocation Ending point (patient/destination)
     * @param durationSeconds Total simulation duration in seconds (default: 2 min)
     * @param deliveryPersonId Simulated delivery person ID (default: "sim_delivery_person")
     * @param deliveryPersonName Display name (default: "Test Delivery")
     * @param deliveryPersonPhone Contact number (default: "+91 98765 43210")
     */
    fun simulateDelivery(
        orderId: String,
        pharmacyLocation: LatLng,
        patientLocation: LatLng,
        durationSeconds: Int = 120,
        deliveryPersonId: String = "sim_delivery_person",
        deliveryPersonName: String = "Test Delivery",
        deliveryPersonPhone: String = "+91 98765 43210"
    ) {
        if (simulationRunning) {
            Log.w(TAG, "Simulation already running for $orderId")
            return
        }

        Log.d(TAG, "Starting delivery simulation: $orderId")
        simulationRunning = true
        currentStep = 0

        // Generate interpolated path from pharmacy to patient
        val totalSteps = (durationSeconds * 1000L / LOCATION_UPDATE_INTERVAL_MS).toInt()
        val positions = generateInterpolatedPath(pharmacyLocation, patientLocation, totalSteps)

        Log.d(TAG, "Generated $totalSteps steps for $durationSeconds second simulation")

        // Simulate each position step
        positions.forEachIndexed { index, latLng ->
            val delayMs = index * LOCATION_UPDATE_INTERVAL_MS

            handler.postDelayed({
                if (simulationRunning) {
                    updateSimulatedLocation(
                        orderId,
                        latLng,
                        deliveryPersonId,
                        deliveryPersonName,
                        deliveryPersonPhone,
                        lastPosition
                    )
                    lastPosition = latLng
                    currentStep = index
                }

                // Stop simulation when done
                if (index == positions.size - 1) {
                    simulationRunning = false
                    Log.d(TAG, "Delivery simulation ended for $orderId")
                }
            }, delayMs)
        }
    }

    /**
     * Stop ongoing simulation.
     */
    fun stopSimulation() {
        simulationRunning = false
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "Simulation stopped by user")
    }

    /**
     * Generate smooth path from pharmacy to patient using spherical interpolation.
     *
     * @param start Pharmacy location
     * @param end Patient location
     * @param steps Number of interpolation steps
     * @return List of LatLng points along the path
     */
    private fun generateInterpolatedPath(start: LatLng, end: LatLng, steps: Int): List<LatLng> {
        return (0..steps).map { step ->
            val t = step.toDouble() / steps
            // Spherical interpolation for accurate path on Earth surface
            SphericalUtil.interpolate(start, end, t)
        }
    }

    /**
     * Create tracking update for one position and write to Firestore.
     */
    private fun updateSimulatedLocation(
        orderId: String,
        position: LatLng,
        deliveryPersonId: String,
        deliveryPersonName: String,
        deliveryPersonPhone: String,
        previousPosition: LatLng?
    ) {
        try {
            // Calculate bearing between previous and current position
            val bearing = if (previousPosition != null) {
                calculateBearing(previousPosition, position)
            } else {
                0.0
            }

            // Simulate variable speed: 20-30 km/h with random +/- 2 km/h variation
            val baseSpeed = Random.nextDouble(DEFAULT_SPEED_KMH_MIN, DEFAULT_SPEED_KMH_MAX)
            val speedVariation = Random.nextDouble(-2.0, 2.0)
            val speed = (baseSpeed + speedVariation).coerceIn(DEFAULT_SPEED_KMH_MIN, DEFAULT_SPEED_KMH_MAX)

            // Simulate GPS accuracy: 5-15 meters (realistic for Android FusedLocationProvider)
            val accuracy = Random.nextDouble(5.0, 15.0)

            // Create tracking data
            val tracking = DeliveryTracking(
                orderId = orderId,
                deliveryPersonId = deliveryPersonId,
                latitude = position.latitude,
                longitude = position.longitude,
                speed = speed,
                bearing = bearing,
                accuracy = accuracy,
                deliveryPersonName = deliveryPersonName,
                deliveryPersonPhone = deliveryPersonPhone
                // updatedAt: Firestore @ServerTimestamp will be set automatically
            )

            // Write to Firestore
            val trackingId = "ongoing_$orderId"
            db.collection("deliveryTracking")
                .document(trackingId)
                .set(tracking.toMap())
                .addOnSuccessListener {
                    Log.d(TAG, "Sim step $currentStep: (${"%.4f".format(position.latitude)}, ${"%.4f".format(position.longitude)}) speed=${speed.toInt()}km/h")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to update simulated location: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating simulated tracking: ${e.message}")
        }
    }

    /**
     * Calculate bearing (direction) from one point to another in degrees (0-360).
     *
     * @param from Starting position
     * @param to Ending position
     * @return Bearing in degrees
     */
    private fun calculateBearing(from: LatLng, to: LatLng): Double {
        val lat1Rad = Math.toRadians(from.latitude)
        val lon1Rad = Math.toRadians(from.longitude)
        val lat2Rad = Math.toRadians(to.latitude)
        val lon2Rad = Math.toRadians(to.longitude)

        val dLon = lon2Rad - lon1Rad

        val y = Math.sin(dLon) * Math.cos(lat2Rad)
        val x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon)

        val bearing = Math.toDegrees(Math.atan2(y, x))

        // Normalize bearing to 0-360 range
        return (bearing + 360) % 360
    }

    /**
     * Get simulation progress (0-100).
     */
    fun getProgress(): Int {
        return if (simulationRunning) (currentStep / 100) else 0
    }

    /**
     * Check if simulation is running.
     */
    fun isRunning(): Boolean = simulationRunning
}
