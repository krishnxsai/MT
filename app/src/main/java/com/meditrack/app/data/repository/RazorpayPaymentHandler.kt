package com.meditrack.app.data.repository

import android.util.Log
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.RazorpayPaymentResponse
import com.meditrack.app.data.network.RazorpayApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Razorpay payment callbacks and response fetching.
 *
 * This handler bridges the gap between the limited Razorpay SDK callback
 * (which only provides paymentId) and the complete payment response needed
 * for verification (which includes the signature).
 *
 * Flow:
 * 1. onPaymentSuccess(paymentId) called by Razorpay SDK
 * 2. Handler stores paymentId temporarily
 * 3. RazorpayApiClient fetches full payment details from API
 * 4. Handler validates and returns complete RazorpayPaymentResponse
 * 5. complete response (with signature) sent to backend for verification
 *
 * Thread-Safe:
 * - Uses ConcurrentHashMap for thread-safe state management
 * - All operations are coroutine-safe
 * - Supports concurrent payment processing
 */
@Singleton
class RazorpayPaymentHandler @Inject constructor(
    private val apiClient: RazorpayApiClient
) {

    private companion object {
        private const val TAG = "RazorpayPaymentHandler"
        private const val RESPONSE_CACHE_DURATION_MS = 5 * 60 * 1000  // 5 minutes
    }

    /**
     * Cache of fetched payment responses.
     * Key: paymentId, Value: <response, timestamp>
     *
     * Prevents redundant API calls if the same payment is processed multiple times.
     */
    private val responseCache = ConcurrentHashMap<String, CachedResponse>()

    /**
     * Pending payment IDs waiting for response fetch.
     * Key: paymentId, Value: <status, errorMessage>
     */
    private val pendingPayments = ConcurrentHashMap<String, PaymentStatus>()

    /**
     * Handle successful payment from Razorpay SDK callback.
     *
     * Since the SDK callback only provides paymentId, this method:
     * 1. Records the successful payment
     * 2. Fetches full payment details from Razorpay API
     * 3. Validates the response
     * 4. Returns complete payment response with signature
     *
     * @param paymentId Razorpay payment ID from callback
     * @return Resource.Success with complete payment response (including signature)
     *         Resource.Error if fetch fails
     *
     * Example:
     * ```
     * val result = handler.handlePaymentSuccess("pay_1234567890")
     * when (result) {
     *   is Resource.Success -> {
     *     val signature = result.data.signature  // ✅ Now we have signature!
     *     // Send to backend for verification
     *   }
     *   is Resource.Error -> handleError(result.message)
     * }
     * ```
     */
    suspend fun handlePaymentSuccess(paymentId: String): Resource<RazorpayPaymentResponse> =
        withContext(Dispatchers.IO) {
            try {
                require(!paymentId.isEmpty()) { "Payment ID cannot be empty" }

                Log.d(TAG, "Handling payment success: $paymentId")

                // Check cache first (prevent redundant API calls)
                getCachedResponse(paymentId)?.let { cached ->
                    Log.d(TAG, "Using cached response for: $paymentId")
                    return@withContext Resource.Success(cached)
                }

                // Mark as pending
                pendingPayments[paymentId] = PaymentStatus.FETCHING

                // Fetch from Razorpay API
                Log.d(TAG, "Fetching payment details from Razorpay API: $paymentId")
                val apiResult = apiClient.getPaymentDetails(paymentId)

                when (apiResult) {
                    is Resource.Success -> {
                        val response = apiResult.data
                        Log.d(TAG, "Payment fetched successfully: $paymentId")

                        // Validate response
                        val (isValid, error) = response.validate()
                        if (!isValid) {
                            Log.e(TAG, "Payment validation failed: $error")
                            pendingPayments[paymentId] = PaymentStatus.INVALID
                            return@withContext Resource.Error("Payment validation failed: $error")
                        }

                        // Cache the response
                        cacheResponse(paymentId, response)

                        // Mark as completed
                        pendingPayments[paymentId] = PaymentStatus.COMPLETED

                        Log.d(TAG, "Payment processed successfully with signature: ${response.signature.take(16)}...")
                        Resource.Success(response)
                    }

                    is Resource.Error -> {
                        Log.e(TAG, "API Error: ${apiResult.message}")
                        pendingPayments[paymentId] = PaymentStatus.FAILED
                        Resource.Error(apiResult.message)
                    }

                    is Resource.Loading -> {
                        pendingPayments[paymentId] = PaymentStatus.FETCHING
                        Resource.Error("Still fetching payment details")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling payment success: ${e.message}", e)
                pendingPayments[paymentId] = PaymentStatus.FAILED
                Resource.Error("Error handling payment: ${e.message}")
            }
        }

    /**
     * Handle payment error from Razorpay SDK callback.
     *
     * @param paymentId Razorpay payment ID (may be empty for early errors)
     * @param errorCode Error code from Razorpay
     * @param errorMessage Error message from Razorpay
     *
     * Example:
     * ```
     * handler.handlePaymentError(paymentId, 101, "OTP Validation Failed")
     * ```
     */
    suspend fun handlePaymentError(
        paymentId: String,
        errorCode: Int,
        errorMessage: String
    ) = withContext(Dispatchers.IO) {
        Log.e(TAG, "Payment error - ID: $paymentId, Code: $errorCode, Message: $errorMessage")
        pendingPayments[paymentId] = PaymentStatus.FAILED
        // Error details will be handled by caller
    }

    /**
     * Check if a payment is still being processed.
     *
     * @param paymentId Payment ID to check
     * @return true if payment is currently being fetched
     */
    fun isPending(paymentId: String): Boolean {
        val status = pendingPayments[paymentId]
        return status == PaymentStatus.FETCHING
    }

    /**
     * Clear cached responses older than RESPONSE_CACHE_DURATION_MS.
     *
     * Called periodically to prevent memory leaks.
     * Can be triggered by:
     * - Application lifecycle events
     * - Periodic background tasks
     * - Manual calls if needed
     */
    fun clearStaleCache() {
        val now = System.currentTimeMillis()
        val it = responseCache.iterator()

        while (it.hasNext()) {
            val (paymentId, cached) = it.next()
            if ((now - cached.timestamp) > RESPONSE_CACHE_DURATION_MS) {
                Log.d(TAG, "Evicting stale cache for: $paymentId")
                it.remove()
            }
        }
    }

    /**
     * Clear all cached data (useful for testing or forced refresh).
     */
    fun clearAllCache() {
        responseCache.clear()
        pendingPayments.clear()
        Log.d(TAG, "Cleared all cache")
    }

    /**
     * Get cache statistics for monitoring.
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            cachedResponses = responseCache.size,
            pendingPayments = pendingPayments.size,
            totalCacheSize = responseCache.values.sumOf { it.response.toMap().toString().length }
        )
    }

    // ─────────────── Private Helper Methods ───────────────────────────────

    /**
     * Get cached response if available and not expired.
     */
    private fun getCachedResponse(paymentId: String): RazorpayPaymentResponse? {
        val cached = responseCache[paymentId] ?: return null

        val ageMs = System.currentTimeMillis() - cached.timestamp
        if (ageMs > RESPONSE_CACHE_DURATION_MS) {
            responseCache.remove(paymentId)
            Log.d(TAG, "Cache expired for: $paymentId (age: ${ageMs}ms)")
            return null
        }

        return cached.response
    }

    /**
     * Cache a payment response with timestamp.
     */
    private fun cacheResponse(paymentId: String, response: RazorpayPaymentResponse) {
        responseCache[paymentId] = CachedResponse(
            response = response,
            timestamp = System.currentTimeMillis()
        )
        Log.d(TAG, "Cached response for: $paymentId")
    }

    // ─────────────── Data Classes ─────────────────────────────────────────

    /**
     * Cached payment response with timestamp.
     */
    private data class CachedResponse(
        val response: RazorpayPaymentResponse,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Payment processing status.
     */
    private enum class PaymentStatus {
        FETCHING,    // Currently fetching from API
        COMPLETED,   // Successfully fetched
        INVALID,     // Failed validation
        FAILED       // API call failed
    }

    /**
     * Cache statistics for monitoring.
     */
    data class CacheStats(
        val cachedResponses: Int,
        val pendingPayments: Int,
        val totalCacheSize: Int
    )
}
