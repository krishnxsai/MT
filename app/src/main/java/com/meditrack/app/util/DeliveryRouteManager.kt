package com.meditrack.app.util

import android.graphics.Color
import android.util.Log
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.SphericalUtil
import com.meditrack.app.data.model.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages delivery route visualization on Google Maps.
 *
 * Draws polyline from delivery person to patient destination.
 * Uses straight-line distance as a fallback (can integrate Directions API later).
 * Efficient caching and update batching to minimize map updates.
 */
class DeliveryRouteManager(private val mapsApiKey: String) {

    companion object {
        private const val TAG = "DeliveryRouteManager"
        private const val POLYLINE_WIDTH_PX = 10f
        private const val POLYLINE_COLOR = Color.BLUE
    }

    private var currentPolyline: com.google.android.gms.maps.model.Polyline? = null
    private var lastDeliveryLocation: LatLng? = null
    private var lastPatientLocation: LatLng? = null
    private var lastRouteDistance: Int? = null

    data class RouteData(
        val distanceMeters: Int,
        val durationSeconds: Int,
        val polylinePoints: String,
        val overviewPolyline: List<LatLng>
    )

    /**
     * Update delivery route by drawing polyline on map.
     *
     * Currently uses straight-line distance and speed-based ETA.
     * Can be enhanced later with Google Directions API for real routes.
     *
     * @param map GoogleMap instance to draw polyline on
     * @param deliveryLocation Current location of delivery person
     * @param patientLocation Destination (patient's delivery address)
     * @param onRouteUpdate Callback with distance for ETA calculation
     * @param onError Error callback if route calculation fails
     * @return Resource.Success with route data
     */
    suspend fun updateRouteWithDirections(
        map: GoogleMap,
        deliveryLocation: LatLng,
        patientLocation: LatLng,
        travelMode: String = "DRIVING",
        onRouteUpdate: ((distanceMeters: Int, durationSeconds: Int) -> Unit)? = null,
        onError: ((message: String) -> Unit)? = null
    ): Resource<RouteData> = withContext(Dispatchers.Default) {
        try {
            // Check if location changed significantly (> 5 meters)
            val distance = SphericalUtil.computeDistanceBetween(
                lastDeliveryLocation ?: deliveryLocation,
                deliveryLocation
            )

            if (distance < 5 && currentPolyline != null) {
                // No significant movement, skip route update
                Log.d(TAG, "Route unchanged (movement < 5m), skipping update")
                return@withContext Resource.Success(
                    RouteData(
                        distanceMeters = lastRouteDistance ?: 0,
                        durationSeconds = 0,
                        polylinePoints = "",
                        overviewPolyline = emptyList()
                    )
                )
            }

            // Calculate straight-line distance and estimated time
            val distanceMeters = SphericalUtil.computeDistanceBetween(
                deliveryLocation,
                patientLocation
            ).toInt()

            // Estimate duration: 25 km/h average delivery speed
            val averageSpeedKmh = 25.0
            val durationSeconds = ((distanceMeters / 1000.0) / averageSpeedKmh * 3600).toInt()

            // Create simple path: straight line from delivery to patient
            val pathPoints = listOf(deliveryLocation, patientLocation)

            val routeData = RouteData(
                distanceMeters = distanceMeters,
                durationSeconds = durationSeconds,
                polylinePoints = "",
                overviewPolyline = pathPoints
            )

            // Draw polyline on map
            withContext(Dispatchers.Main) {
                drawPolylineOnMap(map, pathPoints)
                lastDeliveryLocation = deliveryLocation
                lastPatientLocation = patientLocation
                lastRouteDistance = distanceMeters
            }

            // Notify with route data
            onRouteUpdate?.invoke(distanceMeters, durationSeconds)

            Log.d(TAG, "Route updated: ${distanceMeters}m, ${durationSeconds}s")

            Resource.Success(routeData)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating route: ${e.message}")
            onError?.invoke(e.message ?: "Unknown error")
            Resource.Error(e.message ?: "Failed to update route")
        }
    }

    /**
     * Draw polyline on map with styling.
     */
    private fun drawPolylineOnMap(map: GoogleMap, path: List<LatLng>) {
        if (path.isEmpty()) {
            Log.w(TAG, "Empty path, skipping polyline drawing")
            return
        }

        try {
            // Remove old polyline
            currentPolyline?.remove()

            // Create new polyline
            currentPolyline = map.addPolyline(
                PolylineOptions()
                    .addAll(path)
                    .color(POLYLINE_COLOR)
                    .width(POLYLINE_WIDTH_PX)
                    .geodesic(true)
            )

            Log.d(TAG, "Polyline drawn with ${path.size} points")
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing polyline: ${e.message}")
        }
    }

    /**
     * Get cached route distance (meters).
     */
    fun getLastRouteDistance(): Int? = lastRouteDistance

    /**
     * Clear route (remove polyline from map).
     */
    fun clearRoute(map: GoogleMap) {
        try {
            currentPolyline?.remove()
            currentPolyline = null
            lastDeliveryLocation = null
            lastPatientLocation = null
            lastRouteDistance = null
            Log.d(TAG, "Route cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing route: ${e.message}")
        }
    }
}
