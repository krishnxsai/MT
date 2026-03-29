package com.meditrack.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Insurance coverage information for a patient.
 * Stored in `users/{userId}/insurance` subcollection.
 *
 * Enables insurance claim tracking and reimbursement management.
 * (Phase 5 feature)
 */
data class Insurance(
    @DocumentId
    val id: String = "",
    val userId: String = "",

    // ── Insurance provider ──────────────────────────────────────
    val providerName: String = "",
    val providerContactNumber: String = "",
    val providerWebsite: String = "",

    // ── Policy details ──────────────────────────────────────────
    val policyNumber: String = "",
    val policyHolderName: String = "",
    val policyHolderRelation: String = "", // Self, Spouse, Parent, Child, etc.
    val planName: String = "",
    val planType: String = "", // Individual, Family, Group, etc.

    // ── Coverage ────────────────────────────────────────────────
    val summaryLimit: Double = 0.0, // Total annual coverage
    val cashlessHospitals: List<String> = emptyList(), // Network hospital IDs
    val deductible: Double = 0.0,   // Amount user pays before coverage starts
    val copayPercentage: Int = 0,   // User's share in percentage (e.g., 20%)

    // ── Validity ────────────────────────────────────────────────
    val policyStartDate: Date? = null,
    val policyEndDate: Date? = null,
    val renewalDate: Date? = null,
    val isActive: Boolean = true,

    // ── Document storage ────────────────────────────────────────
    /** URL to policy document PDF */
    val policyDocumentUrl: String = "",
    /** URL to insurance card image */
    val insuranceCardImageUrl: String = "",

    // ── Claims ──────────────────────────────────────────────────
    /** Total amount claimed so far this year */
    val totalClaimedAmount: Double = 0.0,

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    constructor() : this(id = "")

    /** Remaining coverage available */
    val balanceCoverage: Double
        get() = (summaryLimit - totalClaimedAmount).coerceAtLeast(0.0)

    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "providerName" to providerName,
        "providerContactNumber" to providerContactNumber,
        "providerWebsite" to providerWebsite,
        "policyNumber" to policyNumber,
        "policyHolderName" to policyHolderName,
        "policyHolderRelation" to policyHolderRelation,
        "planName" to planName,
        "planType" to planType,
        "summaryLimit" to summaryLimit,
        "cashlessHospitals" to cashlessHospitals,
        "deductible" to deductible,
        "copayPercentage" to copayPercentage,
        "policyStartDate" to policyStartDate,
        "policyEndDate" to policyEndDate,
        "renewalDate" to renewalDate,
        "isActive" to isActive,
        "policyDocumentUrl" to policyDocumentUrl,
        "insuranceCardImageUrl" to insuranceCardImageUrl,
        "totalClaimedAmount" to totalClaimedAmount,
        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Insurance = Insurance(
            id = id,
            userId = map["userId"] as? String ?: "",
            providerName = map["providerName"] as? String ?: "",
            providerContactNumber = map["providerContactNumber"] as? String ?: "",
            providerWebsite = map["providerWebsite"] as? String ?: "",
            policyNumber = map["policyNumber"] as? String ?: "",
            policyHolderName = map["policyHolderName"] as? String ?: "",
            policyHolderRelation = map["policyHolderRelation"] as? String ?: "",
            planName = map["planName"] as? String ?: "",
            planType = map["planType"] as? String ?: "",
            summaryLimit = (map["summaryLimit"] as? Number)?.toDouble() ?: 0.0,
            cashlessHospitals = (map["cashlessHospitals"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            deductible = (map["deductible"] as? Number)?.toDouble() ?: 0.0,
            copayPercentage = (map["copayPercentage"] as? Number)?.toInt() ?: 0,
            policyStartDate = (map["policyStartDate"] as? Timestamp)?.toDate(),
            policyEndDate = (map["policyEndDate"] as? Timestamp)?.toDate(),
            renewalDate = (map["renewalDate"] as? Timestamp)?.toDate(),
            isActive = map["isActive"] as? Boolean ?: true,
            policyDocumentUrl = map["policyDocumentUrl"] as? String ?: "",
            insuranceCardImageUrl = map["insuranceCardImageUrl"] as? String ?: "",
            totalClaimedAmount = (map["totalClaimedAmount"] as? Number)?.toDouble() ?: 0.0,
            createdAt = (map["createdAt"] as? Timestamp)?.toDate(),
            updatedAt = (map["updatedAt"] as? Timestamp)?.toDate()
        )
    }
}
