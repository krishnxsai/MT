package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a financial transaction for a refill order.
 * Stored in the top-level `transactions` Firestore collection.
 *
 * Each order may have one or more transactions
 * (e.g., initial charge, refund on cancellation).
 */
data class OrderTransaction(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val orderId: String = "",
    val medicineId: String = "",
    val medicineName: String = "",

    // ── Financial ────────────────────────────────────────────────
    val type: TransactionType = TransactionType.PURCHASE,
    val amount: Double = 0.0,
    val currency: String = "INR",
    val status: TransactionStatus = TransactionStatus.COMPLETED,

    // ── Pharmacy ─────────────────────────────────────────────────
    val pharmacyId: String = "",
    val pharmacyName: String = "",

    // ── Prescription verification ────────────────────────────────
    /** Whether a valid prescription was verified for this transaction. */
    val prescriptionVerified: Boolean = false,
    val prescriptionId: String = "",

    // ── Razorpay Payment Fields ───────────────────────────────────
    val razorpayOrderId: String = "",           // Razorpay order ID
    val razorpayPaymentId: String = "",         // Razorpay payment ID (after success)
    val paymentMethod: String = "UPI",          // CARD, UPI, NETBANKING, etc.
    val transactionRef: String = "",            // Razorpay transaction reference

    val notes: String = "",

    @ServerTimestamp
    val createdAt: Date? = null
) {
    constructor() : this(id = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "orderId" to orderId,
        "medicineId" to medicineId,
        "medicineName" to medicineName,
        "type" to type.name,
        "amount" to amount,
        "currency" to currency,
        "status" to status.name,
        "pharmacyId" to pharmacyId,
        "pharmacyName" to pharmacyName,
        "prescriptionVerified" to prescriptionVerified,
        "prescriptionId" to prescriptionId,
        "razorpayOrderId" to razorpayOrderId,
        "razorpayPaymentId" to razorpayPaymentId,
        "paymentMethod" to paymentMethod,
        "transactionRef" to transactionRef,
        "notes" to notes,
        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): OrderTransaction = OrderTransaction(
            id = id,
            userId = map["userId"] as? String ?: "",
            orderId = map["orderId"] as? String ?: "",
            medicineId = map["medicineId"] as? String ?: "",
            medicineName = map["medicineName"] as? String ?: "",
            type = try {
                TransactionType.valueOf(map["type"] as? String ?: "PURCHASE")
            } catch (_: Exception) { TransactionType.PURCHASE },
            amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
            currency = map["currency"] as? String ?: "INR",
            status = try {
                TransactionStatus.valueOf(map["status"] as? String ?: "COMPLETED")
            } catch (_: Exception) { TransactionStatus.COMPLETED },
            pharmacyId = map["pharmacyId"] as? String ?: "",
            pharmacyName = map["pharmacyName"] as? String ?: "",
            prescriptionVerified = map["prescriptionVerified"] as? Boolean ?: false,
            prescriptionId = map["prescriptionId"] as? String ?: "",
            razorpayOrderId = map["razorpayOrderId"] as? String ?: "",
            razorpayPaymentId = map["razorpayPaymentId"] as? String ?: "",
            paymentMethod = map["paymentMethod"] as? String ?: "UPI",
            transactionRef = map["transactionRef"] as? String ?: "",
            notes = map["notes"] as? String ?: "",
            createdAt = (map["createdAt"] as? Timestamp)?.toDate()
        )
    }
}

enum class TransactionType {
    PURCHASE,
    REFUND,
    INSURANCE_CLAIM
}

enum class TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
}

