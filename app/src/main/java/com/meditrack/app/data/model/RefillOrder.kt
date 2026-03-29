package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a medicine refill order.
 * Stored in the top-level `orders` Firestore collection.
 *
 * Lifecycle: PENDING → CONFIRMED → PREPARING → READY → SHIPPED → DELIVERED
 *                    └→ CANCELLED
 *
 * Supports both single-item orders (legacy) and multi-item orders.
 */
data class RefillOrder(
    @DocumentId
    val id: String = "",
    val userId: String = "",

    // ── Single medicine reference (legacy/backward compat) ────────
    val medicineId: String = "",
    val medicineName: String = "",
    val medicineDosage: String = "",
    val medicineUnit: String = "",

    // ── Order details ────────────────────────────────────────────
    val quantity: Int = 0,
    val status: OrderStatus = OrderStatus.PENDING,
    val notes: String = "",

    /** Optional prescription reference for controlled medications. */
    val prescriptionId: String = "",

    // ── Pharmacy info ────────────────────────────────────────────
    val pharmacyName: String = "",
    val pharmacyId: String = "",

    // ── Payment link (GAP 1 FIX) ─────────────────────────────────
    /** Direct link to payment in payments collection */
    val paymentId: String = "",

    // ── Multi-item support (NEW) ─────────────────────────────────
    /** List of items in this order. Empty for legacy single-item orders. */
    val items: List<OrderItem> = emptyList(),

    // ── Pricing (NEW) ────────────────────────────────────────────
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val discount: Double = 0.0,
    val totalAmount: Double = 0.0,

    // ── Delivery info (NEW) ──────────────────────────────────────
    val deliveryAddress: DeliveryAddress? = null,
    val deliveryType: DeliveryType = DeliveryType.DELIVERY,
    val estimatedDeliveryMinutes: Int = 0,
    val deliveryWindow: DeliveryWindow = DeliveryWindow.flexible(),
    val preferredDeliveryDate: Date? = null,

    // ── Real-time tracking (NEW) ─────────────────────────────────
    /** Delivery person's current location */
    val currentLocation: GeoPoint? = null,
    val lastLocationUpdate: Date? = null,

    /** Reference to active tracking document in deliveryTracking collection */
    val deliveryTrackingId: String = "",

    /** Denormalized pharmacy location for map display */
    val pharmacyLocation: GeoPoint? = null,

    // ── Status change tracking ───────────────────────────────────
    val statusHistory: List<OrderStatusEntry> = emptyList(),
    val cancelReason: String = "",

    // ── Timestamps ───────────────────────────────────────────────
    val estimatedDelivery: Date? = null,
    val deliveredAt: Date? = null,

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    constructor() : this(id = "")

    /** True if this is a multi-item order */
    val isMultiItemOrder: Boolean
        get() = items.isNotEmpty()

    /** Get total item count (works for both single and multi-item orders) */
    val itemCount: Int
        get() = if (isMultiItemOrder) items.size else 1

    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "patientId" to userId,
        "medicineId" to medicineId,
        "medicineName" to medicineName,
        "medicineDosage" to medicineDosage,
        "medicineUnit" to medicineUnit,
        "quantity" to quantity,
        "status" to status.name,
        "notes" to notes,
        "prescriptionId" to prescriptionId,
        "pharmacyName" to pharmacyName,
        "pharmacyId" to pharmacyId,
        "paymentId" to paymentId,
        "items" to items.map { it.toMap() },
        "subtotal" to subtotal,
        "deliveryFee" to deliveryFee,
        "discount" to discount,
        "totalAmount" to totalAmount,
        "deliveryAddress" to deliveryAddress?.toMap(),
        "deliveryLocation" to deliveryAddress?.toLocationMap(),
        "deliveryType" to deliveryType.name,
        "estimatedDeliveryMinutes" to estimatedDeliveryMinutes,
        "estimatedDeliveryTime" to estimatedDeliveryMinutes,
        "deliveryWindow" to deliveryWindow.toMap(),
        "preferredDeliveryDate" to preferredDeliveryDate,
        "currentLocation" to currentLocation,
        "lastLocationUpdate" to lastLocationUpdate,
        "statusHistory" to statusHistory.map { it.toMap() },
        "cancelReason" to cancelReason,
        "estimatedDelivery" to estimatedDelivery,
        "deliveredAt" to deliveredAt,
        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): RefillOrder {
            @Suppress("UNCHECKED_CAST")
            val historyList = (map["statusHistory"] as? List<Map<String, Any?>>)?.map {
                OrderStatusEntry.fromMap(it)
            } ?: emptyList()

            @Suppress("UNCHECKED_CAST")
            val itemsList = (map["items"] as? List<Map<String, Any?>>)?.map {
                OrderItem.fromMap(it)
            } ?: emptyList()

            @Suppress("UNCHECKED_CAST")
            val deliveryLocationMap = map["deliveryLocation"] as? Map<String, Any?>

            @Suppress("UNCHECKED_CAST")
            val deliveryDetailsMap = map["deliveryAddressDetails"] as? Map<String, Any?>

            @Suppress("UNCHECKED_CAST")
            val deliveryAddr = when {
                deliveryDetailsMap != null -> DeliveryAddress.fromMap(deliveryDetailsMap)
                map["deliveryAddress"] is Map<*, *> -> {
                    DeliveryAddress.fromMap(map["deliveryAddress"] as Map<String, Any?>)
                }
                map["deliveryAddress"] is String -> {
                    DeliveryAddress(
                        fullAddress = map["deliveryAddress"] as String,
                        latitude = (deliveryLocationMap?.get("lat") as? Number)?.toDouble() ?: 0.0,
                        longitude = (deliveryLocationMap?.get("lng") as? Number)?.toDouble() ?: 0.0
                    )
                }
                else -> null
            }

            return RefillOrder(
                id = id,
                userId = map["userId"] as? String ?: map["patientId"] as? String ?: "",
                medicineId = map["medicineId"] as? String ?: "",
                medicineName = map["medicineName"] as? String ?: "",
                medicineDosage = map["medicineDosage"] as? String ?: "",
                medicineUnit = map["medicineUnit"] as? String ?: "",
                quantity = (map["quantity"] as? Number)?.toInt() ?: 0,
                status = try {
                    OrderStatus.valueOf(map["status"] as? String ?: "PENDING")
                } catch (_: Exception) { OrderStatus.PENDING },
                notes = map["notes"] as? String ?: "",
                prescriptionId = map["prescriptionId"] as? String ?: "",
                pharmacyName = map["pharmacyName"] as? String ?: "",
                pharmacyId = map["pharmacyId"] as? String ?: "",
                items = itemsList,
                subtotal = (map["subtotal"] as? Number)?.toDouble() ?: 0.0,
                deliveryFee = (map["deliveryFee"] as? Number)?.toDouble() ?: 0.0,
                discount = (map["discount"] as? Number)?.toDouble() ?: 0.0,
                totalAmount = (map["totalAmount"] as? Number)?.toDouble() ?: 0.0,
                deliveryAddress = deliveryAddr,
                deliveryType = try {
                    DeliveryType.valueOf(map["deliveryType"] as? String ?: "DELIVERY")
                } catch (_: Exception) { DeliveryType.DELIVERY },
                estimatedDeliveryMinutes =
                    (map["estimatedDeliveryMinutes"] as? Number)?.toInt()
                        ?: (map["estimatedDeliveryTime"] as? Number)?.toInt()
                        ?: 0,
                deliveryWindow = @Suppress("UNCHECKED_CAST") ((map["deliveryWindow"] as? Map<String, Any?>)?.let {
                    DeliveryWindow.fromMap(it)
                } ?: DeliveryWindow.flexible()),
                preferredDeliveryDate = (map["preferredDeliveryDate"] as? Timestamp)?.toDate(),
                currentLocation = map["currentLocation"] as? GeoPoint,
                lastLocationUpdate = (map["lastLocationUpdate"] as? Timestamp)?.toDate(),
                statusHistory = historyList,
                cancelReason = map["cancelReason"] as? String ?: "",
                estimatedDelivery = (map["estimatedDelivery"] as? Timestamp)?.toDate(),
                deliveredAt = (map["deliveredAt"] as? Timestamp)?.toDate(),
                createdAt = (map["createdAt"] as? Timestamp)?.toDate(),
                updatedAt = (map["updatedAt"] as? Timestamp)?.toDate()
            )
        }
    }
}

// ── Display helper extensions ─────────────────────────────────────────

fun RefillOrder.getDisplayStatus(): String = status.displayName()

fun RefillOrder.getStatusIcon(): Int = status.getStatusIcon()

fun RefillOrder.getStatusColor(): Int = status.getStatusColor()

/**
 * Single entry in the order status change trail.
 */
data class OrderStatusEntry(
    val status: String = "",
    val changedAt: Date? = null,
    val note: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "status" to status,
        "changedAt" to changedAt,
        "note" to note
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): OrderStatusEntry = OrderStatusEntry(
            status = map["status"] as? String ?: "",
            changedAt = (map["changedAt"] as? Timestamp)?.toDate(),
            note = map["note"] as? String ?: ""
        )
    }
}

/**
 * Represents a single item in a multi-item order.
 */
data class OrderItem(
    val medicineId: String = "",
    val medicineName: String = "",
    val medicineDosage: String = "",
    val quantity: Int = 0,
    val unitPrice: Double = 0.0,
    val totalPrice: Double = 0.0,
    @get:PropertyName("prescriptionRequired") @set:PropertyName("prescriptionRequired")
    var prescriptionRequired: Boolean = false,
    val prescriptionId: String? = null,
    @get:PropertyName("prescriptionVerified") @set:PropertyName("prescriptionVerified")
    var prescriptionVerified: Boolean = false
) {
    constructor() : this(medicineId = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "medicineId" to medicineId,
        "medicineName" to medicineName,
        "medicineDosage" to medicineDosage,
        "quantity" to quantity,
        "unitPrice" to unitPrice,
        "totalPrice" to totalPrice,
        "prescriptionRequired" to prescriptionRequired,
        "prescriptionId" to prescriptionId,
        "prescriptionVerified" to prescriptionVerified
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): OrderItem = OrderItem(
            medicineId = map["medicineId"] as? String ?: "",
            medicineName = map["medicineName"] as? String ?: "",
            medicineDosage = map["medicineDosage"] as? String ?: "",
            quantity = (map["quantity"] as? Number)?.toInt() ?: 0,
            unitPrice = (map["unitPrice"] as? Number)?.toDouble() ?: 0.0,
            totalPrice = (map["totalPrice"] as? Number)?.toDouble() ?: 0.0,
            prescriptionRequired = map["prescriptionRequired"] as? Boolean ?: false,
            prescriptionId = map["prescriptionId"] as? String,
            prescriptionVerified = map["prescriptionVerified"] as? Boolean ?: false
        )
    }
}

/**
 * Delivery address for an order.
 */
data class DeliveryAddress(
    val label: String = "",         // "Home", "Work", etc.
    val fullAddress: String = "",
    val landmark: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val contactPhone: String = ""
) {
    constructor() : this(label = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "label" to label,
        "fullAddress" to fullAddress,
        "landmark" to landmark,
        "latitude" to latitude,
        "longitude" to longitude,
        "contactPhone" to contactPhone
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): DeliveryAddress = DeliveryAddress(
            label = map["label"] as? String ?: "",
            fullAddress = map["fullAddress"] as? String ?: "",
            landmark = map["landmark"] as? String ?: "",
            latitude = (map["latitude"] as? Number)?.toDouble() ?: 0.0,
            longitude = (map["longitude"] as? Number)?.toDouble() ?: 0.0,
            contactPhone = map["contactPhone"] as? String ?: ""
        )
    }

    fun toLocationMap(): Map<String, Double>? {
        return if (latitude == 0.0 && longitude == 0.0) {
            null
        } else {
            mapOf(
                "lat" to latitude,
                "lng" to longitude
            )
        }
    }
}
