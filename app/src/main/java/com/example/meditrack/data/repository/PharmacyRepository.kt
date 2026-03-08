package com.example.meditrack.data.repository

import android.util.Log
import com.example.meditrack.data.model.*
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
            .orderBy("rating", Query.Direction.DESCENDING)
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
                .orderBy("rating", Query.Direction.DESCENDING)
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
    // Seed Sample Pharmacies (one-time)
    // ══════════════════════════════════════════════════════════════

    /**
     * Seeds sample pharmacy data if the collection is empty.
     * Called on first launch so the pharmacy picker has data.
     */
    suspend fun seedPharmaciesIfEmpty() = withContext(Dispatchers.IO) {
        try {
            val existing = pharmaciesCol.limit(1).get().await()
            if (!existing.isEmpty) {
                Log.d(TAG, "Pharmacies already seeded, skipping")
                return@withContext
            }

            Log.d(TAG, "Seeding sample pharmacies...")

            val samplePharmacies = listOf(
                mapOf(
                    "name" to "MedPlus Pharmacy",
                    "address" to "123 Health Street",
                    "city" to "Hyderabad",
                    "state" to "Telangana",
                    "zipCode" to "500001",
                    "phone" to "+91 40 1234 5678",
                    "email" to "orders@medplus.in",
                    "latitude" to 17.385,
                    "longitude" to 78.4867,
                    "operatingHours" to "Mon–Sat: 8AM–10PM, Sun: 9AM–6PM",
                    "isDeliveryAvailable" to true,
                    "isActive" to true,
                    "rating" to 4.5,
                    "ratingCount" to 234,
                    "imageUrl" to "",
                    "estimatedDeliveryTime" to "30–60 min",
                    "services" to listOf("Home Delivery", "Prescription Upload", "Generic Alternatives"),
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ),
                mapOf(
                    "name" to "Apollo Pharmacy",
                    "address" to "456 Wellness Road",
                    "city" to "Hyderabad",
                    "state" to "Telangana",
                    "zipCode" to "500034",
                    "phone" to "+91 40 2345 6789",
                    "email" to "care@apollopharmacy.in",
                    "latitude" to 17.4225,
                    "longitude" to 78.5438,
                    "operatingHours" to "Open 24/7",
                    "isDeliveryAvailable" to true,
                    "isActive" to true,
                    "rating" to 4.7,
                    "ratingCount" to 512,
                    "imageUrl" to "",
                    "estimatedDeliveryTime" to "45 min",
                    "services" to listOf("Home Delivery", "24/7 Service", "Prescription Refills", "Health Check-ups"),
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ),
                mapOf(
                    "name" to "Netmeds Store",
                    "address" to "789 Care Avenue",
                    "city" to "Hyderabad",
                    "state" to "Telangana",
                    "zipCode" to "500072",
                    "phone" to "+91 40 3456 7890",
                    "email" to "help@netmeds.com",
                    "latitude" to 17.3616,
                    "longitude" to 78.4747,
                    "operatingHours" to "Mon–Sat: 9AM–9PM",
                    "isDeliveryAvailable" to true,
                    "isActive" to true,
                    "rating" to 4.3,
                    "ratingCount" to 178,
                    "imageUrl" to "",
                    "estimatedDeliveryTime" to "Same day",
                    "services" to listOf("Home Delivery", "Generic Medicines", "Subscription Refills"),
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ),
                mapOf(
                    "name" to "Wellness Forever",
                    "address" to "101 Pharma Lane",
                    "city" to "Hyderabad",
                    "state" to "Telangana",
                    "zipCode" to "500018",
                    "phone" to "+91 40 4567 8901",
                    "email" to "info@wellnessforever.in",
                    "latitude" to 17.4399,
                    "longitude" to 78.4983,
                    "operatingHours" to "Mon–Sun: 8AM–11PM",
                    "isDeliveryAvailable" to false,
                    "isActive" to true,
                    "rating" to 4.1,
                    "ratingCount" to 89,
                    "imageUrl" to "",
                    "estimatedDeliveryTime" to "",
                    "services" to listOf("Walk-in", "Prescription Refills", "Health Products"),
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ),
                mapOf(
                    "name" to "PharmEasy Pickup Point",
                    "address" to "55 Digital Plaza",
                    "city" to "Hyderabad",
                    "state" to "Telangana",
                    "zipCode" to "500081",
                    "phone" to "+91 40 5678 9012",
                    "email" to "support@pharmeasy.in",
                    "latitude" to 17.395,
                    "longitude" to 78.534,
                    "operatingHours" to "Mon–Sat: 10AM–8PM",
                    "isDeliveryAvailable" to true,
                    "isActive" to true,
                    "rating" to 4.4,
                    "ratingCount" to 305,
                    "imageUrl" to "",
                    "estimatedDeliveryTime" to "1–2 hours",
                    "services" to listOf("Home Delivery", "Online Ordering", "Discount on First Order", "Subscription Plans"),
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            )

            val batch = firestore.batch()
            for (pharmacy in samplePharmacies) {
                val docRef = pharmaciesCol.document()
                batch.set(docRef, pharmacy)
            }
            batch.commit().await()
            Log.d(TAG, "Seeded ${samplePharmacies.size} pharmacies successfully")
        } catch (e: Exception) {
            Log.e(TAG, "seedPharmaciesIfEmpty error: ${e.message}")
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

                // Step 4: Validate ownership and status
                if (prescription.patientId != userId) {
                    return@withContext Resource.Success(
                        PrescriptionVerification(
                            isValid = false,
                            reason = "Prescription does not belong to current user",
                            requiresPrescription = true
                        )
                    )
                }

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

                val txn = OrderTransaction(
                    userId = userId,
                    orderId = order.id,
                    medicineId = order.medicineId,
                    medicineName = order.medicineName,
                    type = TransactionType.REFUND,
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

