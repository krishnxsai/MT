package com.meditrack.app.data.repository

import android.util.Log
import com.meditrack.app.data.model.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Repository for calculating order pricing and delivery fees.
 *
 * Handles:
 *  • Item-level pricing
 *  • Delivery fee calculation based on distance
 *  • GST (18%) calculation
 *  • Total price breakdown
 *  • Estimated delivery time based on distance
 */
class PricingRepository @Inject constructor() {

    companion object {
        private const val TAG = "PricingRepository"

        // Pricing constants
        private const val BASE_DELIVERY_FEE = 50.0      // ₹ 50 base fee
        private const val COST_PER_KM = 5.0             // ₹ 5 per km
        private const val GST_RATE = 0.18               // 18% GST
        private const val MIN_DELIVERY_MINUTES = 20     // Minimum 20 min delivery
        private const val ESTIMATED_SPEED_KMH = 25.0   // Average delivery speed
    }

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // ─────────────── Pricing Calculations ───────────────

    /**
     * Calculate complete pricing breakdown for an order.
     *
     * @param items Order items with quantities
     * @param deliveryAddress Delivery destination (lat/lng)
     * @param pharmacyLocation Pharmacy location (lat/lng)
     * @param deliveryWindow Selected delivery window
     * @return Pricing breakdown with itemization
     */
    suspend fun calculateOrderPrice(
        items: List<OrderItem>,
        deliveryAddress: DeliveryAddress?,
        pharmacyLocation: com.google.firebase.firestore.GeoPoint?,
        deliveryWindow: DeliveryWindow = DeliveryWindow.flexible()
    ): Resource<PricingBreakdown> = withContext(Dispatchers.IO) {
        try {
            if (items.isEmpty()) {
                return@withContext Resource.Error("No items in order")
            }

            // Calculate items subtotal
            val itemBreakdown = mutableListOf<ItemPrice>()
            var itemsSubtotal = 0.0

            for (item in items) {
                val unitPrice = item.unitPrice
                val itemTotal = unitPrice * item.quantity
                itemsSubtotal += itemTotal

                itemBreakdown.add(
                    ItemPrice(
                        medicineId = item.medicineId,
                        medicineName = item.medicineName,
                        quantity = item.quantity,
                        unitPrice = unitPrice,
                        totalPrice = itemTotal
                    )
                )
            }

            // Calculate delivery fee based on distance
            var distance = 0.0
            var estimatedDeliveryMinutes = MIN_DELIVERY_MINUTES
            if (deliveryAddress != null && pharmacyLocation != null) {
                distance = calculateDistance(
                    pharmacyLocation.latitude,
                    pharmacyLocation.longitude,
                    deliveryAddress.latitude,
                    deliveryAddress.longitude
                )
                estimatedDeliveryMinutes = (MIN_DELIVERY_MINUTES + (distance / ESTIMATED_SPEED_KMH * 60)).toInt()
            }

            // Calculate delivery fee with window multiplier
            val baseDeliveryFee = BASE_DELIVERY_FEE + (distance * COST_PER_KM)
            val windowMultiplier = deliveryWindow.getDeliveryMultiplier()
            val deliveryFee = baseDeliveryFee * windowMultiplier

            // Calculate GST (18% on subtotal + delivery)
            val subtotalWithDelivery = itemsSubtotal + deliveryFee
            val gst = subtotalWithDelivery * GST_RATE

            // Calculate total
            val totalAmount = subtotalWithDelivery + gst

            val breakdown = PricingBreakdown(
                itemBreakdown = itemBreakdown,
                itemsSubtotal = itemsSubtotal,
                deliveryFee = deliveryFee,
                discount = 0.0,
                gst = gst,
                totalAmount = totalAmount,
                currency = "INR",
                estimatedDeliveryMinutes = estimatedDeliveryMinutes
            )

            Log.d(TAG, "Pricing calculated: items=₹${itemsSubtotal}, delivery=₹${deliveryFee}, gst=₹${gst}, total=₹${totalAmount}")
            Resource.Success(breakdown)
        } catch (e: Exception) {
            Log.e(TAG, "calculateOrderPrice error: ${e.message}")
            Resource.Error(e.message ?: "Failed to calculate price")
        }
    }

    /**
     * Calculate delivery fee for given distance and window.
     *
     * @param distance Distance in km
     * @param deliveryWindow Delivery time window (affects multiplier)
     * @return Delivery fee in ₹
     */
    fun calculateDeliveryFee(distance: Double, deliveryWindow: DeliveryWindow = DeliveryWindow.flexible()): Double {
        val baseFee = BASE_DELIVERY_FEE + (distance * COST_PER_KM)
        return baseFee * deliveryWindow.getDeliveryMultiplier()
    }

    /**
     * Calculate estimated delivery time in minutes.
     *
     * @param distanceInKm Distance to delivery location
     * @return Estimated delivery time in minutes
     */
    fun calculateEstimatedDelivery(distanceInKm: Double): Int {
        return (MIN_DELIVERY_MINUTES + (distanceInKm / ESTIMATED_SPEED_KMH * 60)).toInt()
    }

    /**
     * Calculate distance between two geo-coordinates using Haversine formula.
     * Falls back to straight-line distance if API unavailable.
     *
     * @param lat1 Pharmacy latitude
     * @param lon1 Pharmacy longitude
     * @param lat2 Delivery latitude
     * @param lon2 Delivery longitude
     * @return Distance in kilometers
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }

    /**
     * Get pricing configuration from Firestore (if customized per pharmacy).
     * Falls back to defaults if not configured.
     */
    suspend fun getPricingConfig(pharmacyId: String): Resource<Map<String, Any>> =
        withContext(Dispatchers.IO) {
            try {
                val doc = firestore.collection("pharmacies")
                    .document(pharmacyId)
                    .collection("config")
                    .document("pricing")
                    .get()
                    .await()

                val config = if (doc.exists()) doc.data ?: emptyMap() else emptyMap()
                Resource.Success(config)
            } catch (e: Exception) {
                Log.w(TAG, "getPricingConfig error: ${e.message}, using defaults")
                Resource.Success(emptyMap())  // Return empty map to use defaults
            }
        }
}
