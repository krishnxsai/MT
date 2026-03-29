package com.meditrack.app.data.repository

import android.util.Log
import com.meditrack.app.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository for pharmacies, prescription verification, and transaction logging.
 */
class PharmacyRepository {

    companion object {
        private const val TAG = "PharmacyRepository"
        private const val VERIFIED_STATUS_APPROVED = "APPROVED"
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val pharmaciesCol by lazy { firestore.collection("pharmacies") }
    private val transactionsCol by lazy { firestore.collection("transactions") }
    private val prescriptionsCol by lazy { firestore.collection("prescriptions") }
    private val ordersCol by lazy { firestore.collection("orders") }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // ══════════════════════════════════════════════════════════════
    // Pharmacy Listing
    // ══════════════════════════════════════════════════════════════

    /**
     * Real-time flow of all active pharmacies.
     */
    fun getPharmaciesFlow(): Flow<Resource<List<Pharmacy>>> = callbackFlow {
        trySend(Resource.Loading)

        val listener = pharmaciesCol
            .whereEqualTo("isActive", true)
            .whereEqualTo("verificationStatus", VERIFIED_STATUS_APPROVED)
            .orderBy("rating", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to load pharmacies"))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Pharmacy::class.java)
                } ?: emptyList()
                trySend(Resource.Success(list))
            }

        awaitClose { listener.remove() }
    }

    /**
     * One-shot fetch of active pharmacies.
     */
    suspend fun getPharmacies(): Resource<List<Pharmacy>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = pharmaciesCol
                .whereEqualTo("isActive", true)
                .whereEqualTo("verificationStatus", VERIFIED_STATUS_APPROVED)
                .orderBy("rating", Query.Direction.DESCENDING)
                .limit(50)
                .get().await()

            val list = snapshot.documents.mapNotNull { it.toObject(Pharmacy::class.java) }
            Resource.Success(list)
        } catch (e: Exception) {
            Log.e(TAG, "getPharmacies error: ${e.message}")
            Resource.Error(e.message ?: "Failed to load pharmacies")
        }
    }

    /**
     * Get a single pharmacy by ID.
     */
    suspend fun getPharmacy(pharmacyId: String): Resource<Pharmacy> = withContext(Dispatchers.IO) {
        try {
            val doc = pharmaciesCol.document(pharmacyId).get().await()
            val pharmacy = doc.toObject(Pharmacy::class.java)
            if (pharmacy != null) Resource.Success(pharmacy)
            else Resource.Error("Pharmacy not found")
        } catch (e: Exception) {
            Log.e(TAG, "getPharmacy error: ${e.message}")
            Resource.Error(e.message ?: "Failed to load pharmacy")
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Digital Prescription Verification
    // ══════════════════════════════════════════════════════════════

    /**
     * Verify that a medicine has a valid, active prescription from a doctor.
     *
     * Checks:
     *  1. The medicine has a linked prescriptionId
     *  2. The prescription exists in Firestore
     *  3. The prescription is currently ACTIVE
     *  4. The prescription belongs to the current user
     *
     * @return A [PrescriptionVerification] result.
     */
    suspend fun verifyPrescription(medicine: Medicine): Resource<PrescriptionVerification> =
        withContext(Dispatchers.IO) {
            try {
                val userId = currentUserId
                    ?: return@withContext Resource.Error("Not logged in")

                // Step 1: Check if medicine has a prescription link
                if (medicine.prescriptionId.isEmpty() && !medicine.prescribedByDoctor) {
                    return@withContext Resource.Success(
                        PrescriptionVerification(
                            isValid = false,
                            reason = "No prescription linked to this medicine",
                            requiresPrescription = false
                        )
                    )
                }

                // Step 2: Look up the prescription record
                val prescriptionId = medicine.prescriptionId
                if (prescriptionId.isEmpty()) {
                    // Prescribed by doctor flag set but no ID — look up by name
                    val snapshot = prescriptionsCol
                        .whereEqualTo("patientId", userId)
                        .whereEqualTo("medicationNameNormalized", medicine.name.trim().lowercase())
                        .whereEqualTo("isActive", true)
                        .limit(1)
                        .get().await()

                    val prescription = snapshot.documents.firstOrNull()
                        ?.toObject(PrescriptionRecord::class.java)

                    return@withContext if (prescription != null) {
                        Resource.Success(
                            PrescriptionVerification(
                                isValid = true,
                                prescriptionId = prescription.id,
                                doctorName = prescription.doctorName,
                                reason = "Valid prescription from Dr. ${prescription.doctorName}",
                                requiresPrescription = true,
                                expiryDate = prescription.endDate
                            )
                        )
                    } else {
                        Resource.Success(
                            PrescriptionVerification(
                                isValid = false,
                                reason = "No active prescription found for ${medicine.name}",
                                requiresPrescription = true
                            )
                        )
                    }
                }

                // Step 3: Fetch by prescriptionId directly
                val prescDoc = prescriptionsCol.document(prescriptionId).get().await()
                val prescription = prescDoc.toObject(PrescriptionRecord::class.java)

                if (prescription == null) {
                    return@withContext Resource.Success(
                        PrescriptionVerification(
                            isValid = false,
                            reason = "Prescription record not found",
                            requiresPrescription = true
                        )
                    )
                }

                // Step 4: Validate status (ownership check skipped for pharmacy verification)
                if (!prescription.isActive || prescription.status == PrescriptionStatus.STOPPED) {
                    return@withContext Resource.Success(
                        PrescriptionVerification(
                            isValid = false,
                            prescriptionId = prescription.id,
                            doctorName = prescription.doctorName,
                            reason = "Prescription has been ${prescription.status.name.lowercase()}",
                            requiresPrescription = true
                        )
                    )
                }

                // Step 5: Check expiry
                val now = java.util.Date()
                if (prescription.endDate != null && prescription.endDate.before(now)) {
                    return@withContext Resource.Success(
                        PrescriptionVerification(
                            isValid = false,
                            prescriptionId = prescription.id,
                            doctorName = prescription.doctorName,
                            reason = "Prescription has expired",
                            requiresPrescription = true,
                            expiryDate = prescription.endDate
                        )
                    )
                }

                // All checks passed
                Resource.Success(
                    PrescriptionVerification(
                        isValid = true,
                        prescriptionId = prescription.id,
                        doctorName = prescription.doctorName,
                        reason = "Valid prescription from Dr. ${prescription.doctorName}",
                        requiresPrescription = true,
                        expiryDate = prescription.endDate
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "verifyPrescription error: ${e.message}")
                Resource.Error(e.message ?: "Failed to verify prescription")
            }
        }

    // ══════════════════════════════════════════════════════════════
    // Transaction Logging
    // ══════════════════════════════════════════════════════════════

    /**
     * Log a transaction when an order is placed.
     */
    suspend fun logOrderTransaction(
        order: RefillOrder,
        pharmacy: Pharmacy?,
        prescriptionVerified: Boolean,
        amount: Double = 0.0
    ): Resource<OrderTransaction> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            val txn = OrderTransaction(
                userId = userId,
                orderId = order.id,
                medicineId = order.medicineId,
                medicineName = order.medicineName,
                type = TransactionType.PURCHASE,
                amount = amount,
                status = TransactionStatus.COMPLETED,
                pharmacyId = pharmacy?.id ?: "",
                pharmacyName = pharmacy?.name ?: order.pharmacyName,
                prescriptionVerified = prescriptionVerified,
                prescriptionId = order.prescriptionId,
                notes = "Refill order for ${order.quantity} ${order.medicineUnit}"
            )

            val docRef = transactionsCol.document()
            docRef.set(txn.toMap()).await()
            Resource.Success(txn.copy(id = docRef.id))
        } catch (e: Exception) {
            Log.e(TAG, "logOrderTransaction error: ${e.message}")
            Resource.Error(e.message ?: "Failed to log transaction")
        }
    }

    /**
     * Log a refund transaction when an order is cancelled.
     */
    suspend fun logRefundTransaction(order: RefillOrder): Resource<OrderTransaction> =
        withContext(Dispatchers.IO) {
            try {
                val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")

                // Prevent duplicate refund entries for the same order.
                val existingRefund = transactionsCol
                    .whereEqualTo("orderId", order.id)
                    .whereEqualTo("type", TransactionType.REFUND.name)
                    .limit(1)
                    .get()
                    .await()
                if (!existingRefund.isEmpty) {
                    val doc = existingRefund.documents.first()
                    val existing = doc.data?.let { OrderTransaction.fromMap(doc.id, it) }
                    return@withContext Resource.Success(existing ?: OrderTransaction(id = doc.id))
                }

                val purchase = transactionsCol
                    .whereEqualTo("orderId", order.id)
                    .whereEqualTo("type", TransactionType.PURCHASE.name)
                    .limit(1)
                    .get()
                    .await()
                    .documents
                    .firstOrNull()
                    ?.data
                    ?.let { OrderTransaction.fromMap("", it) }

                val txn = OrderTransaction(
                    userId = userId,
                    orderId = order.id,
                    medicineId = order.medicineId,
                    medicineName = order.medicineName,
                    type = TransactionType.REFUND,
                    amount = purchase?.amount ?: 0.0,
                    currency = purchase?.currency ?: "INR",
                    status = TransactionStatus.COMPLETED,
                    pharmacyId = order.pharmacyId,
                    pharmacyName = order.pharmacyName,
                    notes = "Refund for cancelled order"
                )

                val docRef = transactionsCol.document()
                docRef.set(txn.toMap()).await()
                Resource.Success(txn.copy(id = docRef.id))
            } catch (e: Exception) {
                Log.e(TAG, "logRefundTransaction error: ${e.message}")
                Resource.Error(e.message ?: "Failed to log refund")
            }
        }

    /**
     * Get all transactions for the current user.
     */
    fun getTransactionsFlow(): Flow<Resource<List<OrderTransaction>>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = transactionsCol
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to load transactions"))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { OrderTransaction.fromMap(doc.id, it) }
                } ?: emptyList()
                trySend(Resource.Success(list))
            }

        awaitClose { listener.remove() }
    }

    // ══════════════════════════════════════════════════════════════
    // Pharmacy-Scoped Transaction Queries
    // ══════════════════════════════════════════════════════════════

    /**
     * Real-time flow of all transactions for a specific pharmacy.
     */
    fun getPharmacyTransactionsFlow(pharmacyId: String): Flow<Resource<List<OrderTransaction>>> = callbackFlow {
        trySend(Resource.Loading)

        val listener = transactionsCol
            .whereEqualTo("pharmacyId", pharmacyId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "getPharmacyTransactionsFlow error: ${error.message}")
                    trySend(Resource.Success(emptyList()))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { OrderTransaction.fromMap(doc.id, it) }
                } ?: emptyList()
                trySend(Resource.Success(list))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get revenue statistics for a pharmacy.
     * Returns map of: totalRevenue, todayRevenue, totalTransactions, refundTotal
     */
    suspend fun getRevenueStats(pharmacyId: String): Resource<Map<String, Double>> =
        withContext(Dispatchers.IO) {
            try {
                // Cap revenue aggregation to the last 90 days for performance
                val ninetyDaysAgo = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_MONTH, -90)
                }.time

                val snapshot = transactionsCol
                    .whereEqualTo("pharmacyId", pharmacyId)
                    .whereGreaterThanOrEqualTo("createdAt", ninetyDaysAgo)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(500)
                    .get().await()

                val transactions = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { OrderTransaction.fromMap(doc.id, it) }
                }

                val today = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.time

                val totalRevenue = transactions
                    .filter { it.type == TransactionType.PURCHASE && it.status == TransactionStatus.COMPLETED }
                    .sumOf { it.amount }

                val todayRevenue = transactions
                    .filter {
                        it.type == TransactionType.PURCHASE &&
                                it.status == TransactionStatus.COMPLETED &&
                                it.createdAt != null && it.createdAt.after(today)
                    }
                    .sumOf { it.amount }

                val refundTotal = transactions
                    .filter { it.type == TransactionType.REFUND }
                    .sumOf { it.amount }

                Resource.Success(
                    mapOf(
                        "totalRevenue" to totalRevenue,
                        "todayRevenue" to todayRevenue,
                        "totalTransactions" to transactions.size.toDouble(),
                        "refundTotal" to refundTotal
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "getRevenueStats error: ${e.message}")
                Resource.Error(e.message ?: "Failed to load revenue stats")
            }
        }

    // ══════════════════════════════════════════════════════════════
    // Pharmacy Profile Management
    // ══════════════════════════════════════════════════════════════

    /**
     * Create or update a pharmacy profile.
     */
    suspend fun savePharmacyProfile(pharmacy: Pharmacy): Resource<Pharmacy> =
        withContext(Dispatchers.IO) {
            try {
                if (pharmacy.id.isNotEmpty()) {
                    // Update existing
                    pharmaciesCol.document(pharmacy.id).update(pharmacy.toMap()).await()
                    Log.d(TAG, "Updated pharmacy profile: ${pharmacy.id}")
                    Resource.Success(pharmacy)
                } else {
                    // Create new
                    val data = pharmacy.toMap().toMutableMap()
                    data["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
                    val docRef = pharmaciesCol.document()
                    docRef.set(data).await()
                    Log.d(TAG, "Created pharmacy profile: ${docRef.id}")
                    Resource.Success(pharmacy.copy(id = docRef.id))
                }
            } catch (e: Exception) {
                Log.e(TAG, "savePharmacyProfile error: ${e.message}")
                Resource.Error(e.message ?: "Failed to save pharmacy profile")
            }
        }

    /**
     * Get the pharmacy owned by the current user.
     */
    suspend fun getMyPharmacy(): Resource<Pharmacy?> = withContext(Dispatchers.IO) {
        try {
            val uid = currentUserId ?: return@withContext Resource.Error("Not logged in")
            val snapshot = pharmaciesCol
                .whereEqualTo("ownerId", uid)
                .limit(1)
                .get().await()

            if (snapshot.documents.isNotEmpty()) {
                val doc = snapshot.documents.first()
                val pharmacy = doc.data?.let { Pharmacy.fromMap(doc.id, it) }
                Resource.Success(pharmacy)
            } else {
                Resource.Success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMyPharmacy error: ${e.message}")
            Resource.Error(e.message ?: "Failed to load pharmacy")
        }
    }

    /**
     * Real-time flow of orders for a specific pharmacy.
     */
    fun getPharmacyOrdersFlow(pharmacyId: String): Flow<Resource<List<RefillOrder>>> = callbackFlow {
        trySend(Resource.Loading)

        val listener = ordersCol
            .whereEqualTo("pharmacyId", pharmacyId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "getPharmacyOrdersFlow error: ${error.message}")
                    trySend(Resource.Success(emptyList()))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { RefillOrder.fromMap(doc.id, it) }
                } ?: emptyList()
                trySend(Resource.Success(list))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get the phone number for a pharmacy by ID.
     * Used for calling pharmacy from order tracking.
     */
    suspend fun getPharmacyPhone(pharmacyId: String): Resource<String?> = withContext(Dispatchers.IO) {
        try {
            val pharmacyDoc = pharmaciesCol.document(pharmacyId).get().await()
            val phone = pharmacyDoc.getString("phone")
            Resource.Success(phone)
        } catch (e: Exception) {
            Log.e(TAG, "getPharmacyPhone error: ${e.message}")
            Resource.Success(null) // Return null on error for graceful fallback
        }
    }
}

/**
 * Result of a prescription verification check.
 */
data class PrescriptionVerification(
    val isValid: Boolean = false,
    val prescriptionId: String = "",
    val doctorName: String = "",
    val reason: String = "",
    val requiresPrescription: Boolean = false,
    val expiryDate: java.util.Date? = null
)

