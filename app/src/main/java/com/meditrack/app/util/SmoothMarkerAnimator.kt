package com.meditrack.app.util

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.maps.android.SphericalUtil

/**
 * Utility for animating marker movement smoothly between positions.
 *
 * Uses spherical interpolation for accurate latitude/longitude interpolation
 * and ValueAnimator for smooth animation over time.
 */
object SmoothMarkerAnimator {

    /**
     * Animate a marker from one position to another over a specified duration.
     *
     * @param marker The marker to animate
     * @param fromLatLng Starting position
     * @param toLatLng Ending position
     * @param durationMs Duration of animation in milliseconds (default: 1500ms)
     * @param bearing Optional bearing to rotate marker (0-360 degrees)
     * @param onUpdate Optional callback called for each animation frame
     */
    fun animateMarkerMovement(
        marker: Marker,
        fromLatLng: LatLng,
        toLatLng: LatLng,
        durationMs: Long = 1500,
        bearing: Float = 0f,
        onUpdate: ((LatLng) -> Unit)? = null
    ) {
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
        }

        var lastUpdateTime = 0L

        animator.addUpdateListener { valueAnimator ->
            val progress = valueAnimator.animatedValue as Float

            // Interpolate position using spherical geometry for accuracy
            val interpolatedPosition = interpolateLatLng(fromLatLng, toLatLng, progress.toDouble())

            // Update marker position
            marker.position = interpolatedPosition

            // Rotate marker toward destination (bearing)
            if (bearing > 0) {
                marker.rotation = bearing
            }

            // Call callback (throttled to reduce UI updates)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime > 50) {  // Update at most every 50ms
                onUpdate?.invoke(interpolatedPosition)
                lastUpdateTime = currentTime
            }
        }

        animator.start()
    }

    /**
     * Interpolate between two LatLng coordinates using spherical geometry.
     *
     * This method uses spherical linear interpolation (SLERP) to ensure
     * accurate positioning along the surface of the Earth.
     *
     * @param from Starting position
     * @param to Ending position
     * @param t Interpolation factor (0.0 to 1.0)
     * @return Interpolated position
     */
    private fun interpolateLatLng(from: LatLng, to: LatLng, t: Double): LatLng {
        return SphericalUtil.interpolate(from, to, t)
    }

    /**
     * Calculate the bearing (direction) from one point to another.
     *
     * @param from Starting position
     * @param to Ending position
     * @return Bearing in degrees (0-360)
     */
    fun calculateBearing(from: LatLng, to: LatLng): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lat2 = Math.toRadians(to.latitude)
        val lon2 = Math.toRadians(to.longitude)

        val dLon = lon2 - lon1

        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)

        val bearing = Math.toDegrees(Math.atan2(y, x))

        // Normalize to 0-360 range
        return ((bearing + 360) % 360).toFloat()
    }

    /**
     * Calculate distance between two points in meters.
     *
     * @param from Starting position
     * @param to Ending position
     * @return Distance in meters
     */
    fun calculateDistance(from: LatLng, to: LatLng): Double {
        return SphericalUtil.computeDistanceBetween(from, to)
    }
}
