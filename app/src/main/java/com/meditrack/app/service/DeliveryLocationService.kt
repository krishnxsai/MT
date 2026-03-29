package com.meditrack.app.service

import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.meditrack.app.data.model.DeliveryTracking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Background service for updating delivery person's real-time GPS location.
 *
 * Started when order enters OUT_FOR_DELIVERY status (from PharmacyDashboardActivity).
 * Runs continuously (or until delivery complete/cancelled) and sends location updates
 * to Firestore every 3-5 seconds.
 *
 * Includes throttling to reduce writes: only updates if location moved > 10 meters
 * or 30 seconds have passed since last update.
 *
 * Battery: Uses FusedLocationProviderClient with PRIORITY_HIGH_ACCURACY for accurate delivery tracking.
 * Network: Only updates when connected (graceful degradation if offline).
 */
class DeliveryLocationService : Service() {

    companion object {
        private const val TAG = "DeliveryLocationService"
        private const val LOCATION_UPDATE_INTERVAL = 3000L    // 3 seconds
        private const val LOCATION_FASTEST_INTERVAL = 1000L   // 1 second minimum
        private const val THROTTLE_DISTANCE = 10f             // Only update if moved > 10 meters
        private const val THROTTLE_TIME = 30000L              // Or 30 seconds
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastLocationUpdate: Location? = null
    private var lastLocationUpdateTime = 0L
    private var currentOrderId: String? = null
    private var currentDeliveryPersonId: String? = null
    private var currentDeliveryPersonName: String? = null
    private var currentDeliveryPersonPhone: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DeliveryLocationService.onCreate()")

        // Initialize Firebase and Location services
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        fusedLocationClient = com.google.android.gms.location.LocationServices
            .getFusedLocationProviderClient(this)

        // Setup location callback
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "DeliveryLocationService.onStartCommand()")

        // Extract delivery context from intent extras
        currentOrderId = intent?.getStringExtra("orderId")
        currentDeliveryPersonId = intent?.getStringExtra("deliveryPersonId")
        currentDeliveryPersonName = intent?.getStringExtra("deliveryPersonName") ?: "Delivery"
        currentDeliveryPersonPhone = intent?.getStringExtra("deliveryPersonPhone") ?: ""

        if (currentOrderId.isNullOrBlank()) {
            Log.w(TAG, "No orderId provided, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d(TAG, "Starting location tracking for order: $currentOrderId")

        // Start requesting location updates
        startLocationUpdates()

        return START_STICKY  // Restart if killed by system
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DeliveryLocationService.onDestroy()")
        stopLocationUpdates()
        serviceScope.cancel()
    }

    // ─────────────── Location Updates ─────────────

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return

                // Throttle: Only update if distance > 10 meters OR time > 30 seconds
                val distance = lastLocationUpdate?.distanceTo(location) ?: Float.MAX_VALUE
                val timeSinceLastUpdate = System.currentTimeMillis() - lastLocationUpdateTime

                if (distance > THROTTLE_DISTANCE || timeSinceLastUpdate > THROTTLE_TIME) {
                    updateFirestoreLocation(location)
                    lastLocationUpdate = location
                    lastLocationUpdateTime = System.currentTimeMillis()
                }
            }
        }
    }

    private fun startLocationUpdates() {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
                .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
                .setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL + 2000)
                .build()

            // Request location updates (requires permission check)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+: Need BackgroundLocation permission
                try {
                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        this.mainLooper
                    )
                } catch (e: SecurityException) {
                    Log.e(TAG, "Permission denied for location updates: ${e.message}")
                }
            } else {
                // Android <12
                try {
                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        this.mainLooper
                    )
                } catch (e: SecurityException) {
                    Log.e(TAG, "Permission denied for location updates: ${e.message}")
                }
            }

            Log.d(TAG, "Location updates started with interval: $LOCATION_UPDATE_INTERVAL ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location updates: ${e.message}")
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d(TAG, "Location updates stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates: ${e.message}")
        }
    }

    private fun updateFirestoreLocation(location: Location) {
        val orderId = currentOrderId ?: return
        val deliveryPersonId = currentDeliveryPersonId ?: auth.currentUser?.uid ?: return

        try {
            // Create tracking data
            val tracking = DeliveryTracking(
                orderId = orderId,
                deliveryPersonId = deliveryPersonId,
                latitude = location.latitude,
                longitude = location.longitude,
                speed = location.speed * 3.6,  // Convert m/s to km/h
                bearing = location.bearing.toDouble(),
                accuracy = location.accuracy.toDouble(),
                deliveryPersonName = currentDeliveryPersonName ?: "Delivery",
                deliveryPersonPhone = currentDeliveryPersonPhone ?: ""
            )

            // Use trackingId = "ongoing_{orderId}" for easy querying
            val trackingId = "ongoing_$orderId"

            // Update Firestore asynchronously
            serviceScope.launch {
                try {
                    firestore.collection("deliveryTracking")
                        .document(trackingId)
                        .set(tracking.toMap())
                        .addOnSuccessListener {
                            Log.d(TAG, "Location updated for $orderId at (${location.latitude}, ${location.longitude})")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to update location: ${e.message}")
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating Firestore: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating tracking data: ${e.message}")
        }
    }
}
