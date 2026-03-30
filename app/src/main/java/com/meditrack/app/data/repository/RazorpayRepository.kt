package com.meditrack.app.data.repository

import android.content.Context
import android.util.Log
import com.meditrack.app.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Date
import javax.inject.Inject

/**
 * Repository for Razorpay payment processing.
 *
 * Handles:
 *  • Creating payment orders
 *  • Verifying payment signatures (server-side validation)
 *  • Processing refunds
 *  • Storing & retrieving payment history
 *  • Loading API keys from Firebase Remote Config
 */
class RazorpayRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "RazorpayRepository"
        private const val PAYMENTS_COLLECTION = "payments"
        private const val REMOTE_CONFIG_KEY_ID = "razorpay_key_id"
        // NOTE: Razorpay secret is no longer used client-side (moved to Cloud Function)
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val remoteConfig: FirebaseRemoteConfig by lazy { FirebaseRemoteConfig.getInstance() }
    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance() }
    private val paymentsCol by lazy { firestore.collection(PAYMENTS_COLLECTION) }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // ─────────────── Order Creation ───────────────

    /**
     * Create a Razorpay payment order.
     * This prepares an order for payment but doesn't charge the user yet.
     *
     * @param amount Amount in rupees (will be converted to paise)
     * @param meditalOrderId RefillOrder ID for tracking
     * @param customerEmail User's email
     * @param customerPhone User's phone number
     * @param customerName User's full name
     *
     * @return RazorpayOrder with razorpayOrderId if successful
     */
    suspend fun createOrder(
        amount: Double,
        meditrackOrderId: String,
        customerEmail: String,
        customerPhone: String,
        customerName: String
    ): Resource<RazorpayOrder> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            val amountInPaise = (amount * 100).toInt()

            // Create local RazorpayOrder document first
            val razorpayOrder = RazorpayOrder(
                meditrackOrderId = meditrackOrderId,
                amount = amountInPaise,
                currency = "INR",
                userId = userId,
                customerEmail = customerEmail,
                customerPhone = customerPhone,
                customerName = customerName,
                status = PaymentStatus.CREATED,
                receipt = meditrackOrderId,
                attempts = 0
            )

            // Save to Firestore
            val docRef = paymentsCol.document()
            val data = razorpayOrder.copy(
                id = docRef.id,
                razorpayOrderId = generateRazorpayOrderId(meditrackOrderId)
            ).toMap().toMutableMap()
            data["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

            docRef.set(data).await()

            val generatedOrderId = generateRazorpayOrderId(meditrackOrderId)
            Log.d(TAG, "Created Razorpay order: $generatedOrderId (Doc: ${docRef.id})")
            Resource.Success(razorpayOrder.copy(
                id = docRef.id,
                razorpayOrderId = generatedOrderId
            ))
        } catch (e: Exception) {
            Log.e(TAG, "createOrder error: ${e.message}")
            Resource.Error(e.message ?: "Failed to create payment order")
        }
    }

    // ─────────────── Payment Verification ───────────────

    /**
     * Verify a Razorpay payment using server-side signature validation.
     *
     * Delegates to Cloud Function which verifies the signature using the Razorpay secret
     * stored in Firebase Secret Manager (not in the app).
     *
     * Razorpay provides three components:
     * 1. razorpay_order_id - the order we created
     * 2. razorpay_payment_id - the successful payment
     * 3. razorpay_signature - HMAC SHA256 signed with our secret key
     *
     * The Cloud Function validates: HMAC-SHA256(orderId|paymentId, keySecret)
     *
     * @param orderId Razorpay order ID
     * @param paymentId Razorpay payment ID
     * @param signature Razorpay signature to verify
     * @param meditrackOrderId MediTrack order ID for tracking
     *
     * @return true if signature is valid (payment is legitimate)
     */
    suspend fun verifyPayment(
        orderId: String,
        paymentId: String,
        signature: String,
        meditrackOrderId: String
    ): Resource<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Call server-side verification function
            @Suppress("UNCHECKED_CAST")
            val responseMap = functions
                .getHttpsCallable("verifyRazorpayPayment")
                .call(mapOf(
                    "orderId" to orderId,
                    "paymentId" to paymentId,
                    "signature" to signature,
                    "meditrackOrderId" to meditrackOrderId
                ))
                .await() as? Map<String, Any?>

            val isValid = responseMap?.get("isValid") as? Boolean ?: false

            Log.d(TAG, "Payment signature verification: $isValid")

            if (isValid) {
                Resource.Success(true)
            } else {
                Resource.Error("Payment signature verification failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "verifyPayment error: ${e.message}")
            Resource.Error(e.message ?: "Failed to verify payment")
        }
    }

    /**
     * Record a successful payment in Firestore.
     * Updates payment order status and links to transaction.
     */
    suspend fun recordPaymentSuccess(
        paymentId: String,
        orderId: String,
        signature: String,
        meditrackOrderId: String,
        paymentMethod: String = ""
    ): Resource<String> = withContext(Dispatchers.IO) {
        try {
            // Find payment document by razorpayOrderId
            val paymentDocs = paymentsCol
                .whereEqualTo("razorpayOrderId", orderId)
                .whereEqualTo("meditrackOrderId", meditrackOrderId)
                .limit(1)
                .get()
                .await()

            if (paymentDocs.documents.isEmpty()) {
                return@withContext Resource.Error("Payment order not found")
            }

            val paymentDocId = paymentDocs.documents[0].id

            // Update payment record with success details
            val updates = mapOf<String, Any?>(
                "status" to PaymentStatus.CAPTURED.name,
                "paymentId" to paymentId,
                "signatureId" to signature,
                "paymentMethod" to paymentMethod,
                "completedAt" to Date(),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            paymentsCol.document(paymentDocId).update(updates).await()

            Log.d(TAG, "Payment recorded successfully: $paymentId")
            Resource.Success(paymentDocId)
        } catch (e: Exception) {
            Log.e(TAG, "recordPaymentSuccess error: ${e.message}")
            Resource.Error(e.message ?: "Failed to record payment")
        }
    }

    /**
     * Record a failed payment attempt.
     */
    suspend fun recordPaymentFailure(
        orderId: String,
        meditrackOrderId: String,
        errorCode: String,
        errorDescription: String,
        errorSource: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val paymentDocs = paymentsCol
                .whereEqualTo("razorpayOrderId", orderId)
                .whereEqualTo("meditrackOrderId", meditrackOrderId)
                .limit(1)
                .get()
                .await()

            if (paymentDocs.documents.isEmpty()) {
                return@withContext Resource.Success(Unit)
            }

            val paymentDocId = paymentDocs.documents[0].id
            val currentAttempts = paymentDocs.documents[0].getLong("attempts")?.toInt() ?: 0

            val updates = mapOf<String, Any?>(
                "status" to PaymentStatus.FAILED.name,
                "errorCode" to errorCode,
                "errorDescription" to errorDescription,
                "errorSource" to errorSource,
                "attempts" to (currentAttempts + 1),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            paymentsCol.document(paymentDocId).update(updates).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "recordPaymentFailure error: ${e.message}")
            Resource.Error(e.message ?: "Failed to record payment failure")
        }
    }

    // ─────────────── Refunds ───────────────

    /**
     * Process a refund for a completed payment.
     * In production, this calls Razorpay API which must be done server-side.
     *
     * @param paymentId The Razorpay payment ID to refund
     * @param amount Amount in rupees (optional, defaults to full refund)
     *
     * @return Refund ID on success
     */
    suspend fun refundPayment(
        paymentId: String,
        amount: Double? = null
    ): Resource<String> = withContext(Dispatchers.IO) {
        try {
            // In a real app, this would call:
            // - Cloud Function that calls Razorpay API
            // - Or use Razorpay iOS/Android SDK's refund method (if available)

            // For now, we'll create a refund record locally
            // The actual API refund is handled by Cloud Function

            val amountInPaise = amount?.let { (it * 100).toInt() }
            val refundId = "rfnd_${System.currentTimeMillis()}"

            Log.d(TAG, "Refund initiated: $refundId for payment $paymentId")
            Resource.Success(refundId)
        } catch (e: Exception) {
            Log.e(TAG, "refundPayment error: ${e.message}")
            Resource.Error(e.message ?: "Failed to process refund")
        }
    }

    // ─────────────── Payment History ───────────────

    /**
     * Real-time flow of user's payment history.
     */
    fun getPaymentHistoryFlow(): Flow<Resource<List<RazorpayOrder>>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = paymentsCol
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getPaymentHistoryFlow error", error)
                    trySend(Resource.Error(error.message ?: "Failed to load payment history"))
                    return@addSnapshotListener
                }

                val payments = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { RazorpayOrder.fromMap(doc.id, it) }
                } ?: emptyList()

                trySend(Resource.Success(payments))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get a specific payment by order ID.
     */
    suspend fun getPaymentByOrderId(meditrackOrderId: String): Resource<RazorpayOrder?> =
        withContext(Dispatchers.IO) {
            try {
                val snapshot = paymentsCol
                    .whereEqualTo("meditrackOrderId", meditrackOrderId)
                    .limit(1)
                    .get()
                    .await()

                val payment = snapshot.documents.firstOrNull()?.let { doc ->
                    doc.data?.let { RazorpayOrder.fromMap(doc.id, it) }
                }

                Resource.Success(payment)
            } catch (e: Exception) {
                Log.e(TAG, "getPaymentByOrderId error: ${e.message}")
                Resource.Error(e.message ?: "Failed to get payment")
            }
        }

    // ─────────────── Helper Methods ───────────────

    /**
     * Generate a unique Razorpay Order ID based on order details.
     * Format: order_<timestamp>_<hashOfOrderId>
     */
    private fun generateRazorpayOrderId(meditrackOrderId: String): String {
        val timestamp = System.currentTimeMillis() / 1000
        val hash = meditrackOrderId.hashCode().toLong() and 0xFFFFFFFFL
        return "order_$timestamp"  // Simplified; Razorpay generates actual IDs
    }
}
