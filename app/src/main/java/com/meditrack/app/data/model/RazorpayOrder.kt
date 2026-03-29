package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a Razorpay payment order.
 * Stores payment-related data for auditing and transaction tracking.
 * Stored in the top-level `payments` Firestore collection.
 */
data class RazorpayOrder(
    @DocumentId
    val id: String = "",

    // ── Razorpay Order Details ─────────────────────────────────────
    val razorpayOrderId: String = "",           // Order ID from Razorpay
    val meditrackOrderId: String = "",          // Reference to RefillOrder ID
    val amount: Int = 0,                        // Amount in paise (e.g., 50000 for ₹500)
    val currency: String = "INR",

    // ── Customer Info ────────────────────────────────────────────────
    val userId: String = "",
    val customerEmail: String = "",
    val customerPhone: String = "",
    val customerName: String = "",

    // ── Payment Status & Details ────────────────────────────────────
    val status: PaymentStatus = PaymentStatus.CREATED,
    val paymentId: String = "",                 // Razorpay payment ID (after success)
    val signatureId: String = "",               // Razorpay signature (for verification)
    val paymentMethod: String = "",             // UPI, CARD, NETBANKING, WALLET

    // ── Error Handling ────────────────────────────────────────────────
    val errorCode: String = "",
    val errorDescription: String = "",
    val errorSource: String = "",               // api, gateway, payment_method

    // ── Tracking ──────────────────────────────────────────────────────
    val attempts: Int = 0,                      // Number of payment attempts
    val receipt: String = "",                   // Order reference (ideally orderId)

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null,
    val completedAt: Date? = null               // When payment was finalized
) {
    constructor() : this(id = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "razorpayOrderId" to razorpayOrderId,
        "meditrackOrderId" to meditrackOrderId,
        "amount" to amount,
        "currency" to currency,
        "userId" to userId,
        "customerEmail" to customerEmail,
        "customerPhone" to customerPhone,
        "customerName" to customerName,
        "status" to status.name,
        "paymentId" to paymentId,
        "signatureId" to signatureId,
        "paymentMethod" to paymentMethod,
        "errorCode" to errorCode,
        "errorDescription" to errorDescription,
        "errorSource" to errorSource,
        "attempts" to attempts,
        "receipt" to receipt,
        "completedAt" to completedAt,
        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): RazorpayOrder = RazorpayOrder(
            id = id,
            razorpayOrderId = map["razorpayOrderId"] as? String ?: "",
            meditrackOrderId = map["meditrackOrderId"] as? String ?: "",
            amount = (map["amount"] as? Number)?.toInt() ?: 0,
            currency = map["currency"] as? String ?: "INR",
            userId = map["userId"] as? String ?: "",
            customerEmail = map["customerEmail"] as? String ?: "",
            customerPhone = map["customerPhone"] as? String ?: "",
            customerName = map["customerName"] as? String ?: "",
            status = try {
                PaymentStatus.valueOf(map["status"] as? String ?: "CREATED")
            } catch (_: Exception) { PaymentStatus.CREATED },
            paymentId = map["paymentId"] as? String ?: "",
            signatureId = map["signatureId"] as? String ?: "",
            paymentMethod = map["paymentMethod"] as? String ?: "",
            errorCode = map["errorCode"] as? String ?: "",
            errorDescription = map["errorDescription"] as? String ?: "",
            errorSource = map["errorSource"] as? String ?: "",
            attempts = (map["attempts"] as? Number)?.toInt() ?: 0,
            receipt = map["receipt"] as? String ?: "",
            createdAt = (map["createdAt"] as? Timestamp)?.toDate(),
            updatedAt = (map["updatedAt"] as? Timestamp)?.toDate(),
            completedAt = (map["completedAt"] as? Timestamp)?.toDate()
        )
    }
}

/**
 * Payment status lifecycle:
 * CREATED → INITIATED → (AUTHORIZED) → CAPTURED
 *        ↓
 *        FAILED (with error details)
 *        ↓
 *        REFUNDED
 */
enum class PaymentStatus {
    CREATED,        // Order created in Razorpay, waiting for payment
    INITIATED,      // Payment initiated by user
    AUTHORIZED,     // Payment authorized (for some methods)
    CAPTURED,       // Payment captured (success)
    FAILED,         // Payment failed
    REFUNDED,       // Payment refunded
    PENDING         // Awaiting verification
}

enum class PaymentMethod {
    CARD,           // Debit/Credit card
    UPI,            // UPI payment
    NETBANKING,     // Net banking
    WALLET,         // Digital wallet (PayPal, etc.)
    EMI,            // EMI payment
    UNKNOWN         // Unknown method
}
