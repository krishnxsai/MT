package com.example.meditrack.util

import com.example.meditrack.data.model.Pharmacy
import kotlin.math.*

/**
 * Utility for pharmacy search and distance calculations.
 * Uses the Haversine formula for distance between two GPS coordinates.
 */
object PharmacySearchUtil {

    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Calculate distance between two GPS coordinates in kilometers.
     */
    fun calculateDistance(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Sort pharmacies by distance from user's location.
     */
    fun sortByDistance(
        pharmacies: List<Pharmacy>,
        userLat: Double,
        userLng: Double
    ): List<Pair<Pharmacy, Double>> {
        return pharmacies.map { pharmacy ->
            val distance = calculateDistance(userLat, userLng, pharmacy.latitude, pharmacy.longitude)
            pharmacy to distance
        }.sortedBy { it.second }
    }

    /**
     * Filter pharmacies within a given radius (km).
     */
    fun filterByRadius(
        pharmacies: List<Pharmacy>,
        userLat: Double,
        userLng: Double,
        radiusKm: Double
    ): List<Pair<Pharmacy, Double>> {
        return sortByDistance(pharmacies, userLat, userLng)
            .filter { it.second <= radiusKm }
    }

    /**
     * Filter pharmacies by delivery availability.
     */
    fun filterByDeliveryAvailable(pharmacies: List<Pharmacy>): List<Pharmacy> {
        return pharmacies.filter { it.isDeliveryAvailable }
    }

    /**
     * Sort pharmacies by rating (highest first).
     */
    fun sortByRating(pharmacies: List<Pharmacy>): List<Pharmacy> {
        return pharmacies.sortedByDescending { it.rating }
    }

    /**
     * Format distance for display.
     */
    fun formatDistance(distanceKm: Double): String {
        return when {
            distanceKm < 1.0 -> "${(distanceKm * 1000).toInt()} m"
            distanceKm < 10.0 -> String.format("%.1f km", distanceKm)
            else -> "${distanceKm.toInt()} km"
        }
    }
}

