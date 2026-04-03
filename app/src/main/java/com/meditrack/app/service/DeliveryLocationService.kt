package com.meditrack.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.meditrack.app.R
import com.meditrack.app.data.model.DeliveryTracking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
        private const val CHANNEL_ID = "delivery_tracking_channel"
        private const val CHANNEL_NAME = "Delivery Tracking"
        private const val NOTIFICATION_ID = 43021
        private const val PREFS_NAME = "delivery_tracking_service"
        private const val KEY_ACTIVE_ORDER_IDS = "active_order_ids"

        const val ACTION_ADD_TRACKING = "com.meditrack.app.service.action.ADD_TRACKING"
        const val ACTION_REMOVE_TRACKING = "com.meditrack.app.service.action.REMOVE_TRACKING"
        const val ACTION_STOP_ALL_TRACKING = "com.meditrack.app.service.action.STOP_ALL_TRACKING"

        const val EXTRA_ORDER_ID = "orderId"
        const val EXTRA_DELIVERY_PERSON_ID = "deliveryPersonId"
        const val EXTRA_DELIVERY_PERSON_NAME = "deliveryPersonName"
        const val EXTRA_DELIVERY_PERSON_PHONE = "deliveryPersonPhone"

        private const val LOCATION_UPDATE_INTERVAL = 3000L    // 3 seconds
        private const val LOCATION_FASTEST_INTERVAL = 1000L   // 1 second minimum
        private const val THROTTLE_DISTANCE = 10f             // Only update if moved > 10 meters
        private const val THROTTLE_TIME = 30000L              // Or 30 seconds
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var servicePrefs: SharedPreferences
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastLocationUpdate: Location? = null
    private var lastLocationUpdateTime = 0L
    private val activeOrderIds = linkedSetOf<String>()
    private val activeOrderIdsLock = Any()
    private var currentDeliveryPersonId: String? = null
    private var currentDeliveryPersonName: String? = null
    private var currentDeliveryPersonPhone: String? = null
    private var locationUpdatesRunning = false
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DeliveryLocationService.onCreate()")

        // Initialize Firebase and Location services
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        servicePrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        fusedLocationClient = com.google.android.gms.location.LocationServices
            .getFusedLocationProviderClient(this)

        createNotificationChannel()

        loadPersistedActiveOrders()
        hydrateDeliveryPersonContextFromAuth()

        // Setup location callback
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_ADD_TRACKING
        Log.d(TAG, "DeliveryLocationService.onStartCommand(action=$action)")

        when (action) {
            ACTION_ADD_TRACKING -> handleAddTracking(intent)
            ACTION_REMOVE_TRACKING -> handleRemoveTracking(intent)
            ACTION_STOP_ALL_TRACKING -> handleStopAllTracking()
            else -> {
                Log.w(TAG, "Unknown action '$action', treating as add")
                handleAddTracking(intent)
            }
        }

        val activeOrders = getActiveOrderIdsSnapshot()
        return if (activeOrders.isNotEmpty()) {
            startAsForeground(activeOrders)
            startLocationUpdatesIfNeeded()
            START_STICKY
        } else {
            stopLocationUpdates()
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DeliveryLocationService.onDestroy()")
        stopLocationUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        serviceScope.cancel()
    }

    private fun handleAddTracking(intent: Intent?) {
        updateDeliveryPersonContextFromIntent(intent)

        val orderId = intent?.getStringExtra(EXTRA_ORDER_ID)
            ?: intent?.getStringExtra("orderId")

        if (orderId.isNullOrBlank()) {
            if (getActiveOrderIdsSnapshot().isNotEmpty()) {
                Log.d(TAG, "No new order provided, continuing existing active tracking")
            } else {
                Log.w(TAG, "No orderId provided for add action")
            }
            return
        }

        val wasAdded = synchronized(activeOrderIdsLock) {
            activeOrderIds.add(orderId)
        }

        if (wasAdded) {
            persistActiveOrderIds()
            Log.d(TAG, "Added order for live tracking: $orderId")
        }

        refreshForegroundNotification()
    }

    private fun handleRemoveTracking(intent: Intent?) {
        val orderId = intent?.getStringExtra(EXTRA_ORDER_ID)
            ?: intent?.getStringExtra("orderId")

        if (orderId.isNullOrBlank()) {
            Log.w(TAG, "No orderId provided for remove action")
            return
        }

        val removed = synchronized(activeOrderIdsLock) {
            activeOrderIds.remove(orderId)
        }

        if (removed) {
            persistActiveOrderIds()
            Log.d(TAG, "Removed order from live tracking: $orderId")
        }

        refreshForegroundNotification()
    }

    private fun handleStopAllTracking() {
        synchronized(activeOrderIdsLock) {
            activeOrderIds.clear()
        }
        persistActiveOrderIds()
        Log.d(TAG, "Stopped tracking for all active orders")
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
                    updateFirestoreLocationForActiveOrders(location)
                    lastLocationUpdate = location
                    lastLocationUpdateTime = System.currentTimeMillis()
                }
            }
        }
    }

    private fun startLocationUpdatesIfNeeded() {
        if (locationUpdatesRunning) {
            return
        }

        try {
            if (!hasLocationPermission()) {
                Log.e(TAG, "Location permission missing, stopping delivery tracking service")
                stopSelf()
                return
            }

            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
                .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
                .setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL + 2000)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                this.mainLooper
            )

            locationUpdatesRunning = true
            Log.d(TAG, "Location updates started with interval: $LOCATION_UPDATE_INTERVAL ms")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for location updates: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location updates: ${e.message}")
        }
    }

    private fun stopLocationUpdates() {
        if (!locationUpdatesRunning) {
            return
        }

        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            locationUpdatesRunning = false
            Log.d(TAG, "Location updates stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates: ${e.message}")
        }
    }

    private fun updateFirestoreLocationForActiveOrders(location: Location) {
        val orderIds = getActiveOrderIdsSnapshot()
        if (orderIds.isEmpty()) {
            return
        }

        val deliveryPersonId = currentDeliveryPersonId ?: auth.currentUser?.uid ?: return

        try {
            serviceScope.launch {
                for (orderId in orderIds) {
                    try {
                        val tracking = DeliveryTracking(
                            orderId = orderId,
                            deliveryPersonId = deliveryPersonId,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            speed = location.speed * 3.6,  // Convert m/s to km/h
                            bearing = location.bearing.toDouble(),
                            accuracy = location.accuracy.toDouble(),
                            isActive = true,
                            trackingStatus = "ACTIVE",
                            deliveryPersonName = currentDeliveryPersonName ?: "Delivery",
                            deliveryPersonPhone = currentDeliveryPersonPhone ?: ""
                        )

                        val trackingId = "ongoing_$orderId"

                        firestore.collection("deliveryTracking")
                            .document(trackingId)
                            .set(tracking.toMap())
                            .await()

                        appendTrackingPoint(trackingId, tracking)

                        Log.d(
                            TAG,
                            "Location updated for $orderId at (${location.latitude}, ${location.longitude})"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating Firestore for $orderId: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating tracking data: ${e.message}")
        }
    }

    private suspend fun appendTrackingPoint(trackingId: String, tracking: DeliveryTracking) {
        val point = mapOf(
            "orderId" to tracking.orderId,
            "deliveryPersonId" to tracking.deliveryPersonId,
            "latitude" to tracking.latitude,
            "longitude" to tracking.longitude,
            "speed" to tracking.speed,
            "bearing" to tracking.bearing,
            "accuracy" to tracking.accuracy,
            "trackingStatus" to tracking.trackingStatus,
            "isActive" to tracking.isActive,
            "recordedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("deliveryTracking")
            .document(trackingId)
            .collection("points")
            .document()
            .set(point)
            .await()
    }

    private fun hasLocationPermission(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocationGranted || coarseLocationGranted
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows active courier location tracking"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    private fun startAsForeground(activeOrders: List<String>) {
        val notification = createTrackingNotification(activeOrders)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun createTrackingNotification(activeOrders: List<String>): Notification {
        val count = activeOrders.size
        val summary = if (count == 1) {
            "1 active order"
        } else {
            "$count active orders"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("Live delivery tracking active")
            .setContentText("Updating courier location for $summary")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun refreshForegroundNotification() {
        if (!foregroundStarted) {
            return
        }

        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        notificationManager.notify(
            NOTIFICATION_ID,
            createTrackingNotification(getActiveOrderIdsSnapshot())
        )
    }

    private fun getActiveOrderIdsSnapshot(): List<String> = synchronized(activeOrderIdsLock) {
        activeOrderIds.toList()
    }

    private fun persistActiveOrderIds() {
        val snapshot = synchronized(activeOrderIdsLock) {
            activeOrderIds.toSet()
        }
        servicePrefs.edit().putStringSet(KEY_ACTIVE_ORDER_IDS, snapshot).apply()
    }

    private fun loadPersistedActiveOrders() {
        val persisted = servicePrefs.getStringSet(KEY_ACTIVE_ORDER_IDS, emptySet()) ?: emptySet()
        if (persisted.isNotEmpty()) {
            synchronized(activeOrderIdsLock) {
                activeOrderIds.clear()
                activeOrderIds.addAll(persisted)
            }
            Log.d(TAG, "Recovered ${persisted.size} active tracking orders from disk")
        }
    }

    private fun hydrateDeliveryPersonContextFromAuth() {
        val user = auth.currentUser ?: return
        currentDeliveryPersonId = user.uid
        currentDeliveryPersonName = user.displayName ?: "Delivery"

        serviceScope.launch {
            try {
                val userDoc = firestore.collection("users").document(user.uid).get().await()
                currentDeliveryPersonPhone = userDoc.getString("phoneNumber") ?: ""
            } catch (e: Exception) {
                Log.w(TAG, "Could not hydrate delivery phone from profile: ${e.message}")
            }
        }
    }

    private fun updateDeliveryPersonContextFromIntent(intent: Intent?) {
        if (intent == null) {
            return
        }

        val id = intent.getStringExtra(EXTRA_DELIVERY_PERSON_ID)
            ?: intent.getStringExtra("deliveryPersonId")
        val name = intent.getStringExtra(EXTRA_DELIVERY_PERSON_NAME)
            ?: intent.getStringExtra("deliveryPersonName")
        val phone = intent.getStringExtra(EXTRA_DELIVERY_PERSON_PHONE)
            ?: intent.getStringExtra("deliveryPersonPhone")

        if (!id.isNullOrBlank()) {
            currentDeliveryPersonId = id
        }
        if (!name.isNullOrBlank()) {
            currentDeliveryPersonName = name
        }
        if (phone != null) {
            currentDeliveryPersonPhone = phone
        }
    }
}
