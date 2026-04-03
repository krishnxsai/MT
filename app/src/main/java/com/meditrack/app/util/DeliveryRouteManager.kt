package com.meditrack.app.util

import android.graphics.Color
import android.util.Log
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil
import com.google.maps.android.SphericalUtil
import com.meditrack.app.data.model.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Manages delivery route visualization on Google Maps.
 *
 * Draws polyline from delivery person to patient destination.
 * Uses Google Routes API for road routes and falls back to straight-line routing.
 * Includes refresh throttling to reduce API usage while keeping ETA responsive.
 */
class DeliveryRouteManager(private val mapsApiKey: String) {

    companion object {
        private const val TAG = "DeliveryRouteManager"
        private const val POLYLINE_WIDTH_PX = 10f
        private const val POLYLINE_COLOR = Color.BLUE
        private const val MIN_ROUTE_REFRESH_INTERVAL_MS = 8000L
        private const val MIN_MOVEMENT_FOR_REFRESH_METERS = 8.0
        private const val GOOGLE_ROUTES_ENDPOINT = "https://routes.googleapis.com/directions/v2:computeRoutes"
        private const val GOOGLE_ROUTES_FIELD_MASK =
            "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline"
    }

    private var currentPolyline: com.google.android.gms.maps.model.Polyline? = null
    private var lastDeliveryLocation: LatLng? = null
    private var lastPatientLocation: LatLng? = null
    private var lastRouteDistance: Int? = null
    private var lastRouteDurationSeconds: Int? = null
    private var lastRoutePolylineEncoded: String = ""
    private var lastRouteOverviewPolyline: List<LatLng> = emptyList()
    private var lastRouteUpdatedAtMs: Long = 0L

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
            val movement = SphericalUtil.computeDistanceBetween(
                lastDeliveryLocation ?: deliveryLocation,
                deliveryLocation
            )
            val now = System.currentTimeMillis()

            val canReuseCachedRoute =
                movement < MIN_MOVEMENT_FOR_REFRESH_METERS &&
                    (now - lastRouteUpdatedAtMs) < MIN_ROUTE_REFRESH_INTERVAL_MS &&
                    lastRouteDistance != null &&
                    lastRouteOverviewPolyline.isNotEmpty()

            if (canReuseCachedRoute) {
                withContext(Dispatchers.Main) {
                    if (currentPolyline == null) {
                        drawPolylineOnMap(map, lastRouteOverviewPolyline)
                    }
                }

                val cachedDistance = lastRouteDistance ?: 0
                val cachedDuration = lastRouteDurationSeconds ?: 0
                onRouteUpdate?.invoke(cachedDistance, cachedDuration)

                Log.d(TAG, "Route cache hit (movement=${"%.2f".format(movement)}m)")
                return@withContext Resource.Success(
                    RouteData(
                        distanceMeters = cachedDistance,
                        durationSeconds = cachedDuration,
                        polylinePoints = lastRoutePolylineEncoded,
                        overviewPolyline = lastRouteOverviewPolyline
                    )
                )
            }

            val routeData = fetchRoadRoute(
                deliveryLocation = deliveryLocation,
                patientLocation = patientLocation,
                travelMode = travelMode
            ) ?: buildFallbackRouteData(deliveryLocation, patientLocation)

            withContext(Dispatchers.Main) {
                drawPolylineOnMap(map, routeData.overviewPolyline)
                lastDeliveryLocation = deliveryLocation
                lastPatientLocation = patientLocation
                lastRouteDistance = routeData.distanceMeters
                lastRouteDurationSeconds = routeData.durationSeconds
                lastRoutePolylineEncoded = routeData.polylinePoints
                lastRouteOverviewPolyline = routeData.overviewPolyline
                lastRouteUpdatedAtMs = now
            }

            onRouteUpdate?.invoke(routeData.distanceMeters, routeData.durationSeconds)
            Log.d(
                TAG,
                "Route updated: ${routeData.distanceMeters}m, ${routeData.durationSeconds}s"
            )

            Resource.Success(routeData)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating route: ${e.message}")
            onError?.invoke("Routes API unavailable, using fallback route")

            val fallback = buildFallbackRouteData(deliveryLocation, patientLocation)
            withContext(Dispatchers.Main) {
                drawPolylineOnMap(map, fallback.overviewPolyline)
                lastDeliveryLocation = deliveryLocation
                lastPatientLocation = patientLocation
                lastRouteDistance = fallback.distanceMeters
                lastRouteDurationSeconds = fallback.durationSeconds
                lastRoutePolylineEncoded = fallback.polylinePoints
                lastRouteOverviewPolyline = fallback.overviewPolyline
                lastRouteUpdatedAtMs = System.currentTimeMillis()
            }
            onRouteUpdate?.invoke(fallback.distanceMeters, fallback.durationSeconds)
            Resource.Success(fallback)
        }
    }

    /**
     * Fetch route distance/duration for ETA calculations in non-map surfaces.
     * Reuses cached route data when movement is minimal to limit API traffic.
     */
    suspend fun getRouteDataForEta(
        deliveryLocation: LatLng,
        patientLocation: LatLng,
        travelMode: String = "DRIVING"
    ): RouteData = withContext(Dispatchers.Default) {
        try {
            val movement = SphericalUtil.computeDistanceBetween(
                lastDeliveryLocation ?: deliveryLocation,
                deliveryLocation
            )
            val destinationMovement = SphericalUtil.computeDistanceBetween(
                lastPatientLocation ?: patientLocation,
                patientLocation
            )
            val now = System.currentTimeMillis()

            val canReuseCachedRoute =
                movement < MIN_MOVEMENT_FOR_REFRESH_METERS &&
                    destinationMovement < MIN_MOVEMENT_FOR_REFRESH_METERS &&
                    (now - lastRouteUpdatedAtMs) < MIN_ROUTE_REFRESH_INTERVAL_MS &&
                    lastRouteDistance != null &&
                    lastRouteDurationSeconds != null &&
                    lastRouteOverviewPolyline.isNotEmpty()

            if (canReuseCachedRoute) {
                return@withContext RouteData(
                    distanceMeters = lastRouteDistance ?: 0,
                    durationSeconds = lastRouteDurationSeconds ?: 0,
                    polylinePoints = lastRoutePolylineEncoded,
                    overviewPolyline = lastRouteOverviewPolyline
                )
            }

            val routeData = fetchRoadRoute(
                deliveryLocation = deliveryLocation,
                patientLocation = patientLocation,
                travelMode = travelMode
            ) ?: buildFallbackRouteData(deliveryLocation, patientLocation)

            lastDeliveryLocation = deliveryLocation
            lastPatientLocation = patientLocation
            lastRouteDistance = routeData.distanceMeters
            lastRouteDurationSeconds = routeData.durationSeconds
            lastRoutePolylineEncoded = routeData.polylinePoints
            lastRouteOverviewPolyline = routeData.overviewPolyline
            lastRouteUpdatedAtMs = now

            routeData
        } catch (e: Exception) {
            Log.w(TAG, "getRouteDataForEta failed: ${e.message}")

            val fallback = buildFallbackRouteData(deliveryLocation, patientLocation)
            lastDeliveryLocation = deliveryLocation
            lastPatientLocation = patientLocation
            lastRouteDistance = fallback.distanceMeters
            lastRouteDurationSeconds = fallback.durationSeconds
            lastRoutePolylineEncoded = fallback.polylinePoints
            lastRouteOverviewPolyline = fallback.overviewPolyline
            lastRouteUpdatedAtMs = System.currentTimeMillis()
            fallback
        }
    }

    private suspend fun fetchRoadRoute(
        deliveryLocation: LatLng,
        patientLocation: LatLng,
        travelMode: String
    ): RouteData? = withContext(Dispatchers.IO) {
        if (!isRoutesApiUsable()) {
            return@withContext null
        }

        var connection: HttpURLConnection? = null

        try {
            connection = (URL(GOOGLE_ROUTES_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Goog-Api-Key", mapsApiKey)
                setRequestProperty("X-Goog-FieldMask", GOOGLE_ROUTES_FIELD_MASK)
                connectTimeout = 6000
                readTimeout = 6000
                doOutput = true
            }

            val payload = buildRoutesRequestBody(deliveryLocation, patientLocation, travelMode)
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload)
            }

            val responseCode = connection.responseCode
            val responseBody = readResponseBody(connection)

            if (responseCode !in 200..299) {
                Log.w(TAG, "Routes API failed ($responseCode): $responseBody")
                return@withContext null
            }

            parseRoutesResponse(responseBody, deliveryLocation, patientLocation)
        } catch (e: Exception) {
            Log.w(TAG, "Routes API error: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildRoutesRequestBody(
        deliveryLocation: LatLng,
        patientLocation: LatLng,
        travelMode: String
    ): String {
        val resolvedTravelMode = when (travelMode.uppercase(Locale.ROOT)) {
            "DRIVING" -> "DRIVE"
            "WALKING" -> "WALK"
            "BICYCLING" -> "BICYCLE"
            "TWO_WHEELER" -> "TWO_WHEELER"
            else -> "DRIVE"
        }

        val root = JSONObject()
            .put("origin", buildRoutePoint(deliveryLocation))
            .put("destination", buildRoutePoint(patientLocation))
            .put("travelMode", resolvedTravelMode)
            .put("routingPreference", "TRAFFIC_AWARE_OPTIMAL")
            .put("polylineQuality", "OVERVIEW")
            .put("polylineEncoding", "ENCODED_POLYLINE")
            .put("computeAlternativeRoutes", false)
            .put("languageCode", "en-US")
            .put("units", "METRIC")

        return root.toString()
    }

    private fun buildRoutePoint(location: LatLng): JSONObject {
        return JSONObject().put(
            "location",
            JSONObject().put(
                "latLng",
                JSONObject()
                    .put("latitude", location.latitude)
                    .put("longitude", location.longitude)
            )
        )
    }

    private fun parseRoutesResponse(
        responseBody: String,
        deliveryLocation: LatLng,
        patientLocation: LatLng
    ): RouteData? {
        if (responseBody.isBlank()) {
            return null
        }

        val root = JSONObject(responseBody)
        val routes = root.optJSONArray("routes") ?: return null
        if (routes.length() == 0) {
            return null
        }

        val firstRoute = routes.optJSONObject(0) ?: return null
        val encodedPolyline = firstRoute
            .optJSONObject("polyline")
            ?.optString("encodedPolyline")
            .orEmpty()

        val decodedPolyline = if (encodedPolyline.isNotBlank()) {
            runCatching { PolyUtil.decode(encodedPolyline) }.getOrElse {
                Log.w(TAG, "Failed to decode route polyline: ${it.message}")
                emptyList()
            }
        } else {
            emptyList()
        }

        val distanceMeters = firstRoute.optInt(
            "distanceMeters",
            SphericalUtil.computeDistanceBetween(deliveryLocation, patientLocation).toInt()
        )
        val durationSeconds = parseDurationSeconds(
            firstRoute.optString("duration")
        ).takeIf { it > 0 } ?: estimateDurationSeconds(distanceMeters)

        val overviewPolyline = decodedPolyline.ifEmpty {
            listOf(deliveryLocation, patientLocation)
        }

        return RouteData(
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            polylinePoints = encodedPolyline,
            overviewPolyline = overviewPolyline
        )
    }

    private fun parseDurationSeconds(duration: String): Int {
        val normalized = duration.trim().lowercase(Locale.ROOT)
        if (!normalized.endsWith("s")) {
            return 0
        }

        val seconds = normalized.removeSuffix("s").toDoubleOrNull() ?: return 0
        return seconds.toInt()
    }

    private fun estimateDurationSeconds(distanceMeters: Int): Int {
        val averageSpeedKmh = 25.0
        return ((distanceMeters / 1000.0) / averageSpeedKmh * 3600).toInt()
    }

    private fun buildFallbackRouteData(
        deliveryLocation: LatLng,
        patientLocation: LatLng
    ): RouteData {
        val distanceMeters = SphericalUtil.computeDistanceBetween(
            deliveryLocation,
            patientLocation
        ).toInt()

        val durationSeconds = estimateDurationSeconds(distanceMeters)
        val pathPoints = listOf(deliveryLocation, patientLocation)

        return RouteData(
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            polylinePoints = "",
            overviewPolyline = pathPoints
        )
    }

    private fun readResponseBody(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return ""

        return BufferedReader(stream.reader()).use { reader ->
            reader.readText()
        }
    }

    private fun isRoutesApiUsable(): Boolean {
        return mapsApiKey.isNotBlank() &&
            mapsApiKey.startsWith("AIza") &&
            !mapsApiKey.contains("configure", ignoreCase = true)
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
            lastRouteDurationSeconds = null
            lastRoutePolylineEncoded = ""
            lastRouteOverviewPolyline = emptyList()
            lastRouteUpdatedAtMs = 0L
            Log.d(TAG, "Route cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing route: ${e.message}")
        }
    }
}
