package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import java.util.Date

/**
 * Complete payment response from Razorpay API.
 *
 * This is fetched from Razorpay's /payments/{id} endpoint after successful payment.
 * Contains all data needed for verification including the actual signature.
 *
 * API Reference: https://razorpay.com/docs/api/payments/
 */
data class RazorpayPaymentResponse(
    // ── Payment Identifiers ─────────────────────────────────────────
    val id: String = "",                      // Razorpay payment ID (e.g., "pay_123...")
    val orderId: String = "",                 // Razorpay order ID it belongs to
    val receiptId: String = "",               // Receipt/reference from our system

    // ── Amount ──────────────────────────────────────────────────────
    val amount: Int = 0,                      // Amount in paise
    val currency: String = "INR",
    val amountRefunded: Int = 0,              // If partially refunded

    // ── Payment Method ──────────────────────────────────────────────
    val method: String = "",                  // upi, card, netbanking, wallet, etc.
    val description: String = "",             // Payment description

    // ── Signature (CRITICAL) ───────────────────────────────────────
    val acquirerData: AcquirerData? = null,   // Signature in acquirer response
    val signature: String = "",               // Server-verified signature

    // ── Customer & Contact ──────────────────────────────────────────
    val email: String = "",
    val contact: String = "",                 // Phone number

    // ── Status ──────────────────────────────────────────────────────
    val status: String = "captured",          // captured, authorized, failed
    val captured: Boolean = false,            // Whether amount is captured
    val failed: Boolean = false,

    // ── Card/Bank Details (if applicable) ───────────────────────────
    val card: Card? = null,                   // Card payment details
    val bank: String = "",                    // Bank payment details
    val wallet: String = "",                  // Wallet payment details
    val vpa: String = "",                     // UPI VPA (e.g., user@upi)

    // ── Metadata & Notes ────────────────────────────────────────────
    val notes: Map<String, Any> = emptyMap(),
    val internationalNotes: Map<String, Any> = emptyMap(),

    // ── Fees & Charges ─────────────────────────────────────────────
    val fee: Int = 0,                         // Fee charged by Razorpay (paise)
    val tax: Int = 0,                         // Tax on fee (paise)

    // ── Refund Details ──────────────────────────────────────────────
    val refundStatus: String = "",            // none, partial, full
    val refundCount: Int = 0,
    val refundedAmount: Int = 0,              // Total refunded (paise)

    // ── Timestamps ──────────────────────────────────────────────────
    val createdAt: Long = 0,                  // Unix timestamp when payment created
    val capturedAt: Long = 0,                 // Unix timestamp when captured
    val acquireAttemptCount: Int = 0,

    // ── VRN (Validation Reference Number) ───────────────────────────
    val vrn: String = "",                     // Razorpay's internal reference

    // ── Verification Status ─────────────────────────────────────────
    val verified: Boolean = false,            // Server verified?
    val verifiedAt: Date? = null              // When verified
) {
    constructor() : this(id = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "orderId" to orderId,
        "receiptId" to receiptId,
        "amount" to amount,
        "currency" to currency,
        "amountRefunded" to amountRefunded,
        "method" to method,
        "description" to description,
        "signature" to signature,
        "email" to email,
        "contact" to contact,
        "status" to status,
        "captured" to captured,
        "failed" to failed,
        "notes" to notes,
        "fee" to fee,
        "tax" to tax,
        "refundStatus" to refundStatus,
        "refundCount" to refundCount,
        "createdAt" to createdAt,
        "capturedAt" to capturedAt,
        "verifiedAt" to verifiedAt
    )

    /**
     * Validates that this response is legitimate and complete.
     *
     * @return Pair<Boolean, String> - (isValid, errorMessage)
     */
    fun validate(): Pair<Boolean, String> {
        if (id.isEmpty()) return false to "Payment ID is empty"
        if (orderId.isEmpty()) return false to "Order ID is empty"
        if (amount <= 0) return false to "Amount is invalid"
        if (signature.isEmpty()) return false to "Signature is empty"
        if (status != "captured") return false to "Payment status is not captured: $status"

        // Signature should be 64 hex characters (SHA256)
        if (!signature.matches(Regex("^[a-f0-9]{64}$"))) {
            return false to "Signature format is invalid: $signature"
        }

        return true to "Valid"
    }

    companion object {
        private fun toStringAnyNullableMap(value: Any?): Map<String, Any?> {
            val rawMap = value as? Map<*, *> ?: return emptyMap()
            return rawMap.entries.mapNotNull { entry ->
                val key = entry.key as? String ?: return@mapNotNull null
                key to entry.value
            }.toMap()
        }

        private fun toStringAnyMap(value: Any?): Map<String, Any> {
            val rawMap = value as? Map<*, *> ?: return emptyMap()
            return rawMap.entries.mapNotNull { entry ->
                val key = entry.key as? String ?: return@mapNotNull null
                val mapValue = entry.value ?: return@mapNotNull null
                key to mapValue
            }.toMap()
        }

        /**
         * Parse Razorpay API JSON response.
         *
         * Handles field mapping from Razorpay's API response format.
         */
        fun fromMap(map: Map<String, Any?>): RazorpayPaymentResponse {
            val acquirerData = toStringAnyNullableMap(map["acquirer_data"])
            val notes = toStringAnyMap(map["notes"])

            return RazorpayPaymentResponse(
                id = map["id"] as? String ?: "",
                orderId = map["order_id"] as? String ?: "",
                receiptId = map["receipt"] as? String ?: "",
                amount = (map["amount"] as? Number)?.toInt() ?: 0,
                currency = map["currency"] as? String ?: "INR",
                amountRefunded = (map["amount_refunded"] as? Number)?.toInt() ?: 0,
                method = map["method"] as? String ?: "",
                description = map["description"] as? String ?: "",
                signature = acquirerData["auth"] as? String ?: "",
                email = map["email"] as? String ?: "",
                contact = map["contact"] as? String ?: "",
                status = map["status"] as? String ?: "captured",
                captured = map["captured"] as? Boolean ?: false,
                failed = map["failed"] as? Boolean ?: false,
                notes = notes,
                fee = (map["fee"] as? Number)?.toInt() ?: 0,
                tax = (map["tax"] as? Number)?.toInt() ?: 0,
                refundStatus = map["refund_status"] as? String ?: "",
                refundCount = (map["refund_count"] as? Number)?.toInt() ?: 0,
                createdAt = (map["created_at"] as? Number)?.toLong() ?: 0L,
                capturedAt = (map["captured_at"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

/**
 * Card payment details (if payment method is card).
 */
data class Card(
    val id: String = "",
    val entity: String = "card",
    val name: String = "",
    val last4: String = "",
    val network: String = "",               // Visa, Mastercard, etc.
    val type: String = "",                  // credit or debit
    val issuer: String = "",
    val international: Boolean = false,
    val emi: Boolean = false
)

/**
 * Acquirer data containing payment authentication details.
 */
data class AcquirerData(
    val auth: String = "",                  // Authorization code
    val rrn: String = "",                   // Retrieval Reference Number
    val cavv: String = "",                  // Cardholder Authentication Verification Value
    val xid: String = ""                    // Transaction ID from 3D Secure
)
