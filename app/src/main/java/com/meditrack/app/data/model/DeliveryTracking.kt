package com.meditrack.app.data.model

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Real-time delivery tracking data for a delivery person.
 *
 * Stored in Firestore collection: deliveryTracking/{docId}
 * Used for live GPS tracking of delivery persons while order is OUT_FOR_DELIVERY.
 */
data class DeliveryTracking(
    @DocumentId
    val id: String = "",

    /** Order ID being delivered */
    val orderId: String = "",

    /** Delivery person's unique ID */
    val deliveryPersonId: String = "",

    /** Current latitude position */
    val latitude: Double = 0.0,

    /** Current longitude position */
    val longitude: Double = 0.0,

    /** Current speed in km/h */
    val speed: Double = 0.0,

    /** Direction bearing (0-360 degrees) */
    val bearing: Double = 0.0,

    /** Location accuracy in meters */
    val accuracy: Double = 0.0,

    /** Server timestamp of this update */
    @ServerTimestamp
    val updatedAt: Date? = null,

    /** Delivery person's name (denormalized for UI) */
    val deliveryPersonName: String = "",

    /** Delivery person's phone (emergency contact) */
    val deliveryPersonPhone: String = ""
) {
    /**
     * Convert latitude/longitude to LatLng for Google Maps.
     */
    fun toLatLng(): LatLng = LatLng(latitude, longitude)

    /**
     * Convert to Firestore map for serialization.
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "orderId" to orderId,
        "deliveryPersonId" to deliveryPersonId,
        "latitude" to latitude,
        "longitude" to longitude,
        "speed" to speed,
        "bearing" to bearing,
        "accuracy" to accuracy,
        "updatedAt" to (updatedAt ?: FieldValue.serverTimestamp()),
        "deliveryPersonName" to deliveryPersonName,
        "deliveryPersonPhone" to deliveryPersonPhone
    )

    /**
     * Deserialize from Firestore map.
     */
    companion object {
        fun fromMap(id: String, data: Map<String, Any?>): DeliveryTracking {
            return DeliveryTracking(
                id = id,
                orderId = data["orderId"] as? String ?: "",
                deliveryPersonId = data["deliveryPersonId"] as? String ?: "",
                latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
                speed = (data["speed"] as? Number)?.toDouble() ?: 0.0,
                bearing = (data["bearing"] as? Number)?.toDouble() ?: 0.0,
                accuracy = (data["accuracy"] as? Number)?.toDouble() ?: 0.0,
                updatedAt = when (val rawUpdatedAt = data["updatedAt"]) {
                    is Timestamp -> rawUpdatedAt.toDate()
                    is Date -> rawUpdatedAt
                    else -> null
                },
                deliveryPersonName = data["deliveryPersonName"] as? String ?: "",
                deliveryPersonPhone = data["deliveryPersonPhone"] as? String ?: ""
            )
        }
    }
}
