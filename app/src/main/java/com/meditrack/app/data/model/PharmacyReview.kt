package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Customer review and rating for a pharmacy.
 * Stored in `pharmacies/{pharmacyId}/reviews` subcollection.
 *
 * Enables quality feedback and reputation management.
 */
data class PharmacyReview(
    @DocumentId
    val id: String = "",
    val pharmacyId: String = "",
    val userId: String = "",
    val userName: String = "",

    // ── Rating & Review ────────────────────────────────────────
    /** Overall rating (1-5 stars) */
    val rating: Float = 0f,
    /** Customer review text */
    val reviewText: String = "",
    /** Category ratings */
    val deliveryRating: Float = 0f,        // Speed & On-time delivery
    val qualityRating: Float = 0f,         // Product quality & packaging
    val customerServiceRating: Float = 0f, // Support & communication
    val priceRating: Float = 0f,           // Value for money

    // ── Order reference ────────────────────────────────────────
    val orderId: String = "",
    val orderAmount: Double = 0.0,
    val orderDate: Date? = null,

    // ── Moderation ──────────────────────────────────────────────
    /** PENDING, APPROVED, REJECTED */
    val status: ReviewStatus = ReviewStatus.PENDING,
    /** Reason if rejected (e.g., offensive content, spam) */
    val rejectionReason: String = "",
    /** Moderator user ID who approved/rejected */
    val moderatedBy: String = "",
    /** When the review was moderated */
    val moderatedAt: Date? = null,

    // ── Engagement ──────────────────────────────────────────────
    /** Number of users who found this helpful */
    val helpfulCount: Int = 0,
    /** Pharmacy's response to the review */
    val pharmacyResponse: String = "",
    /** When pharmacy responded */
    val responseAt: Date? = null,

    // ── Media ───────────────────────────────────────────────────
    /** URLs to image attachments */
    val imageUrls: List<String> = emptyList(),

    // ── Flags ───────────────────────────────────────────────────
    /** Whether this is a verified purchase */
    val isVerifiedPurchase: Boolean = true,
    /** User has flagged this as inappropriate */
    val isFlagged: Boolean = false,

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    constructor() : this(id = "")

    /** Calculate average rating from category ratings */
    val averageRating: Float
        get() {
            val ratings = listOf(deliveryRating, qualityRating, customerServiceRating, priceRating)
            return if (ratings.any { it > 0 }) ratings.average().toFloat() else rating
        }

    fun toMap(): Map<String, Any?> = mapOf(
        "pharmacyId" to pharmacyId,
        "userId" to userId,
        "userName" to userName,
        "rating" to rating,
        "reviewText" to reviewText,
        "deliveryRating" to deliveryRating,
        "qualityRating" to qualityRating,
        "customerServiceRating" to customerServiceRating,
        "priceRating" to priceRating,
        "orderId" to orderId,
        "orderAmount" to orderAmount,
        "orderDate" to orderDate,
        "status" to status.name,
        "rejectionReason" to rejectionReason,
        "moderatedBy" to moderatedBy,
        "moderatedAt" to moderatedAt,
        "helpfulCount" to helpfulCount,
        "pharmacyResponse" to pharmacyResponse,
        "responseAt" to responseAt,
        "imageUrls" to imageUrls,
        "isVerifiedPurchase" to isVerifiedPurchase,
        "isFlagged" to isFlagged,
        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): PharmacyReview = PharmacyReview(
            id = id,
            pharmacyId = map["pharmacyId"] as? String ?: "",
            userId = map["userId"] as? String ?: "",
            userName = map["userName"] as? String ?: "",
            rating = (map["rating"] as? Number)?.toFloat() ?: 0f,
            reviewText = map["reviewText"] as? String ?: "",
            deliveryRating = (map["deliveryRating"] as? Number)?.toFloat() ?: 0f,
            qualityRating = (map["qualityRating"] as? Number)?.toFloat() ?: 0f,
            customerServiceRating = (map["customerServiceRating"] as? Number)?.toFloat() ?: 0f,
            priceRating = (map["priceRating"] as? Number)?.toFloat() ?: 0f,
            orderId = map["orderId"] as? String ?: "",
            orderAmount = (map["orderAmount"] as? Number)?.toDouble() ?: 0.0,
            orderDate = (map["orderDate"] as? Timestamp)?.toDate(),
            status = try {
                ReviewStatus.valueOf(map["status"] as? String ?: "PENDING")
            } catch (_: Exception) { ReviewStatus.PENDING },
            rejectionReason = map["rejectionReason"] as? String ?: "",
            moderatedBy = map["moderatedBy"] as? String ?: "",
            moderatedAt = (map["moderatedAt"] as? Timestamp)?.toDate(),
            helpfulCount = (map["helpfulCount"] as? Number)?.toInt() ?: 0,
            pharmacyResponse = map["pharmacyResponse"] as? String ?: "",
            responseAt = (map["responseAt"] as? Timestamp)?.toDate(),
            imageUrls = (map["imageUrls"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            isVerifiedPurchase = map["isVerifiedPurchase"] as? Boolean ?: true,
            isFlagged = map["isFlagged"] as? Boolean ?: false,
            createdAt = (map["createdAt"] as? Timestamp)?.toDate(),
            updatedAt = (map["updatedAt"] as? Timestamp)?.toDate()
        )
    }
}

enum class ReviewStatus {
    PENDING,    // Awaiting moderation
    APPROVED,   // Published and visible
    REJECTED    // Hidden from public view
}
