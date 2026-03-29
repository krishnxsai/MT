package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a cancellation request for an order.
 * Stored in `cancellationRequests/{requestId}` collection.
 *
 * Lifecycle: PENDING → APPROVED/REJECTED → COMPLETED/REFUNDED
 */
data class CancellationRequest(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val orderId: String = "",
    val orderStatus: String = "",  // Order status at time of cancellation request
    val reason: String = "",

    // ── Approval workflow ──────────────────────
    val requestStatus: CancellationStatus = CancellationStatus.PENDING,
    val approvedBy: String = "",   // Pharmacy/admin user ID who approved
    val approvalNote: String = "",
    val approvedAt: Date? = null,

    // ── Refund details ─────────────────────────
    val refundAmount: Double = 0.0,
    val refundStatus: RefundStatus = RefundStatus.PENDING,
    val razorpayPaymentId: String = "",  // Original payment ID
    val razorpayRefundId: String = "",   // Refund ID from Razorpay
    val refundIdempotencyKey: String = "", // SHA256(orderId) for idempotency
    val refundProcessedAt: Date? = null,
    /** Reason for refund failure (if refundStatus == FAILED) */
    val refundFailureReason: String = "",

    // ── Inventory restoration ──────────────────
    val inventoryRestored: Boolean = false,
    val restoredAt: Date? = null,

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    constructor() : this(id = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "orderId" to orderId,
        "orderStatus" to orderStatus,
        "reason" to reason,
        "requestStatus" to requestStatus.name,
        "approvedBy" to approvedBy,
        "approvalNote" to approvalNote,
        "approvedAt" to approvedAt,
        "refundAmount" to refundAmount,
        "refundStatus" to refundStatus.name,
        "razorpayPaymentId" to razorpayPaymentId,
        "razorpayRefundId" to razorpayRefundId,
        "refundIdempotencyKey" to refundIdempotencyKey,
        "refundProcessedAt" to refundProcessedAt,
        "refundFailureReason" to refundFailureReason,
        "inventoryRestored" to inventoryRestored,
        "restoredAt" to restoredAt,
        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): CancellationRequest = CancellationRequest(
            id = id,
            userId = map["userId"] as? String ?: "",
            orderId = map["orderId"] as? String ?: "",
            orderStatus = map["orderStatus"] as? String ?: "",
            reason = map["reason"] as? String ?: "",
            requestStatus = try {
                CancellationStatus.valueOf(map["requestStatus"] as? String ?: "PENDING")
            } catch (_: Exception) { CancellationStatus.PENDING },
            approvedBy = map["approvedBy"] as? String ?: "",
            approvalNote = map["approvalNote"] as? String ?: "",
            approvedAt = (map["approvedAt"] as? Timestamp)?.toDate(),
            refundAmount = (map["refundAmount"] as? Number)?.toDouble() ?: 0.0,
            refundStatus = try {
                RefundStatus.valueOf(map["refundStatus"] as? String ?: "PENDING")
            } catch (_: Exception) { RefundStatus.PENDING },
            razorpayPaymentId = map["razorpayPaymentId"] as? String ?: "",
            razorpayRefundId = map["razorpayRefundId"] as? String ?: "",
            refundIdempotencyKey = map["refundIdempotencyKey"] as? String ?: "",
            refundProcessedAt = (map["refundProcessedAt"] as? Timestamp)?.toDate(),
            refundFailureReason = map["refundFailureReason"] as? String ?: "",
            inventoryRestored = map["inventoryRestored"] as? Boolean ?: false,
            restoredAt = (map["restoredAt"] as? Timestamp)?.toDate(),
            createdAt = (map["createdAt"] as? Timestamp)?.toDate(),
            updatedAt = (map["updatedAt"] as? Timestamp)?.toDate()
        )
    }
}

/**
 * Status of the cancellation request.
 */
enum class CancellationStatus {
    PENDING,        // Awaiting approval (if order SHIPPED+)
    APPROVED,       // Pharmacy/admin approved cancellation
    REJECTED,       // Cancellation request denied
    COMPLETED       // Cancellation finalized, order marked CANCELLED
}

/**
 * Status of the refund process.
 */
enum class RefundStatus {
    PENDING,        // Waiting to process
    PROCESSING,     // Refund in progress with Razorpay
    COMPLETED,      // Refund successfully processed
    FAILED,         // Refund failed (error from Razorpay)
    SKIPPED         // No refund needed (order was PENDING/CONFIRMED only)
}
