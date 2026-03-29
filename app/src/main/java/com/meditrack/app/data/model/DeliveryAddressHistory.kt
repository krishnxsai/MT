package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Tracks all changes to delivery addresses for an order.
 * Stored in `orders/{orderId}/addressHistory` subcollection.
 *
 * Enables audit trail for troubleshooting mis-deliveries or
 * address-related disputes.
 */
data class DeliveryAddressHistory(
    @DocumentId
    val id: String = "",
    val orderId: String = "",
    val userId: String = "",

    // ── Previous address ────────────────────────────────────────
    val previousStreet: String = "",
    val previousCity: String = "",
    val previousState: String = "",
    val previousZipCode: String = "",
    val previousCountry: String = "India",
    val previousLocation: GeoPoint? = null,
    val previousInstructions: String = "",

    // ── New address ─────────────────────────────────────────────
    val newStreet: String = "",
    val newCity: String = "",
    val newState: String = "",
    val newZipCode: String = "",
    val newCountry: String = "India",
    val newLocation: GeoPoint? = null,
    val newInstructions: String = "",

    // ── Change context ──────────────────────────────────────────
    /** CUSTOMER_REQUESTED, DELIVERY_FAILED, ADDRESS_INVALID, etc. */
    val changeReason: String = "",
    /** Who made the change (usually orderId owner or admin) */
    val changedBy: String = "",
    /** Optional notes from who made the change */
    val notes: String = "",
    /** Order status at time of change */
    val orderStatusAtChange: String = "",

    @ServerTimestamp
    val createdAt: Date? = null
) {
    constructor() : this(id = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "orderId" to orderId,
        "userId" to userId,
        "previousStreet" to previousStreet,
        "previousCity" to previousCity,
        "previousState" to previousState,
        "previousZipCode" to previousZipCode,
        "previousCountry" to previousCountry,
        "previousLocation" to previousLocation,
        "previousInstructions" to previousInstructions,
        "newStreet" to newStreet,
        "newCity" to newCity,
        "newState" to newState,
        "newZipCode" to newZipCode,
        "newCountry" to newCountry,
        "newLocation" to newLocation,
        "newInstructions" to newInstructions,
        "changeReason" to changeReason,
        "changedBy" to changedBy,
        "notes" to notes,
        "orderStatusAtChange" to orderStatusAtChange,
        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): DeliveryAddressHistory = DeliveryAddressHistory(
            id = id,
            orderId = map["orderId"] as? String ?: "",
            userId = map["userId"] as? String ?: "",
            previousStreet = map["previousStreet"] as? String ?: "",
            previousCity = map["previousCity"] as? String ?: "",
            previousState = map["previousState"] as? String ?: "",
            previousZipCode = map["previousZipCode"] as? String ?: "",
            previousCountry = map["previousCountry"] as? String ?: "India",
            previousLocation = map["previousLocation"] as? GeoPoint,
            previousInstructions = map["previousInstructions"] as? String ?: "",
            newStreet = map["newStreet"] as? String ?: "",
            newCity = map["newCity"] as? String ?: "",
            newState = map["newState"] as? String ?: "",
            newZipCode = map["newZipCode"] as? String ?: "",
            newCountry = map["newCountry"] as? String ?: "India",
            newLocation = map["newLocation"] as? GeoPoint,
            newInstructions = map["newInstructions"] as? String ?: "",
            changeReason = map["changeReason"] as? String ?: "",
            changedBy = map["changedBy"] as? String ?: "",
            notes = map["notes"] as? String ?: "",
            orderStatusAtChange = map["orderStatusAtChange"] as? String ?: "",
            createdAt = (map["createdAt"] as? Timestamp)?.toDate()
        )
    }
}
