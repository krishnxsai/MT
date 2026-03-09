package com.example.meditrack.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a pharmacy in the system.
 * Stored in the top-level `pharmacies` Firestore collection.
 */
data class Pharmacy(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
    val phone: String = "",
    val email: String = "",

    /** Latitude / longitude for map-based lookups. */
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,

    /** Operating hours, e.g., "Mon–Fri: 8AM–9PM, Sat: 9AM–6PM" */
    val operatingHours: String = "",

    /** Whether the pharmacy supports delivery. */
    @get:PropertyName("isDeliveryAvailable") @set:PropertyName("isDeliveryAvailable")
    var isDeliveryAvailable: Boolean = false,

    /** Whether the pharmacy is currently accepting orders. */
    @get:PropertyName("isActive") @set:PropertyName("isActive")
    var isActive: Boolean = true,

    /** Average user rating (1.0–5.0). */
    val rating: Double = 0.0,
    val ratingCount: Int = 0,

    /** Optional logo/image URL. */
    val imageUrl: String = "",

    /** Estimated delivery time, e.g., "30–60 min" or "Same day". */
    val estimatedDeliveryTime: String = "",

    /** Services offered. */
    val services: List<String> = emptyList(),

    /** UID of the pharmacy-role user who owns/manages this pharmacy. */
    val ownerId: String = "",

    /** License number for regulatory compliance. */
    val licenseNumber: String = "",

    /** URL of the uploaded license document (e.g., Firebase Storage URL). */
    val licenseDocumentUrl: String = "",

    /** Verification status: PENDING, APPROVED, REJECTED. */
    val verificationStatus: String = "PENDING",

    @ServerTimestamp
    val createdAt: Date? = null
) {
    constructor() : this(id = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "address" to address,
        "city" to city,
        "state" to state,
        "zipCode" to zipCode,
        "phone" to phone,
        "email" to email,
        "latitude" to latitude,
        "longitude" to longitude,
        "operatingHours" to operatingHours,
        "isDeliveryAvailable" to isDeliveryAvailable,
        "isActive" to isActive,
        "rating" to rating,
        "ratingCount" to ratingCount,
        "imageUrl" to imageUrl,
        "estimatedDeliveryTime" to estimatedDeliveryTime,
        "services" to services,
        "ownerId" to ownerId,
        "licenseNumber" to licenseNumber,
        "licenseDocumentUrl" to licenseDocumentUrl,
        "verificationStatus" to verificationStatus
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Pharmacy = Pharmacy(
            id = id,
            name = map["name"] as? String ?: "",
            address = map["address"] as? String ?: "",
            city = map["city"] as? String ?: "",
            state = map["state"] as? String ?: "",
            zipCode = map["zipCode"] as? String ?: "",
            phone = map["phone"] as? String ?: "",
            email = map["email"] as? String ?: "",
            latitude = (map["latitude"] as? Number)?.toDouble() ?: 0.0,
            longitude = (map["longitude"] as? Number)?.toDouble() ?: 0.0,
            operatingHours = map["operatingHours"] as? String ?: "",
            isDeliveryAvailable = map["isDeliveryAvailable"] as? Boolean ?: false,
            isActive = map["isActive"] as? Boolean ?: true,
            rating = (map["rating"] as? Number)?.toDouble() ?: 0.0,
            ratingCount = (map["ratingCount"] as? Number)?.toInt() ?: 0,
            imageUrl = map["imageUrl"] as? String ?: "",
            estimatedDeliveryTime = map["estimatedDeliveryTime"] as? String ?: "",
            services = (map["services"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            ownerId = map["ownerId"] as? String ?: "",
            licenseNumber = map["licenseNumber"] as? String ?: "",
            licenseDocumentUrl = map["licenseDocumentUrl"] as? String ?: "",
            verificationStatus = map["verificationStatus"] as? String ?: "PENDING",
            createdAt = (map["createdAt"] as? Timestamp)?.toDate()
        )
    }
}

