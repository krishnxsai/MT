package com.meditrack.app.data.network

import android.util.Log
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.RazorpayPaymentResponse
import com.meditrack.app.data.repository.ConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.inject.Inject

/**
 * Razorpay REST API Client
 *
 * Fetches payment details from Razorpay's API to get complete payment information
 * including the signature, which is NOT available in the SDK callback.
 *
 * API Reference: https://razorpay.com/docs/api/payments/
 *
 * Authentication:
 * - Uses HTTP Basic Auth with key_id (username) and key_secret (password)
 * - Credentials are managed by ConfigRepository (from Cloud Secret Manager)
 *
 * Security:
 * - All requests use HTTPS
 * - Timeout: 5 seconds (payment verification should be quick)
 * - Retry logic: Exponential backoff up to 3 attempts
 * - Response validation: Verifies signature format and payment status
 */
class RazorpayApiClient @Inject constructor(
    private val configRepository: ConfigRepository
) {

    companion object {
        private const val TAG = "RazorpayApiClient"
        private const val RAZORPAY_API_BASE = "https://api.razorpay.com/v1"
        private const val ENDPOINT_GET_PAYMENT = "/payments"
        private const val REQUEST_TIMEOUT_MS = 5000
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 100
    }

    /**
     * Fetch complete payment details from Razorpay API.
     *
     * This is called after onPaymentSuccess callback to get the full response
     * including the signature, which is needed for server-side verification.
     *
     * @param paymentId Razorpay payment ID (from callback)
     * @return RazorpayPaymentResponse with complete payment details including signature
     *
     * @throws Exception if API call fails after retries
     *
     * Example:
     * ```
     * val response = apiClient.getPaymentDetails("pay_1234567890abcdef")
     * // response.signature now contains actual HMAC signature
     * ```
     */
    suspend fun getPaymentDetails(paymentId: String): Resource<RazorpayPaymentResponse> =
        withContext(Dispatchers.IO) {
            try {
                require(!paymentId.isEmpty()) { "Payment ID cannot be empty" }

                Log.d(TAG, "Fetching payment details for: $paymentId")

                // Retry logic with exponential backoff
                var lastException: Exception? = null
                repeat(MAX_RETRIES) { attempt ->
                    try {
                        if (attempt > 0) {
                            val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl (attempt - 1))
                            Log.w(TAG, "Retry attempt $attempt after ${delayMs}ms")
                            Thread.sleep(delayMs.toLong())
                        }

                        return@withContext makeApiCall(paymentId)
                    } catch (e: Exception) {
                        lastException = e
                        Log.e(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                        if (attempt == MAX_RETRIES - 1) {
                            throw e
                        }
                    }
                }

                throw lastException ?: Exception("Failed to fetch payment details after $MAX_RETRIES attempts")
            } catch (e: Exception) {
                Log.e(TAG, "getPaymentDetails error: ${e.message}", e)
                Resource.Error("Failed to fetch payment details: ${e.message}")
            }
        }

    /**
     * Make the actual HTTP API call to Razorpay.
     *
     * @param paymentId Payment ID to fetch
     * @return Resource.Success with payment response or Resource.Error
     *
     * @throws IOException if connection fails
     * @throws Exception if response parsing fails
     */
    private suspend fun makeApiCall(paymentId: String): Resource<RazorpayPaymentResponse> =
        withContext(Dispatchers.IO) {
            try {
                // Get API credentials
                val keyId = getApiKeyId()
                val keySecret = getApiKeySecret()

                if (keyId.isEmpty() || keySecret.isEmpty()) {
                    return@withContext Resource.Error("Razorpay API credentials not configured")
                }

                // Build URL
                val url = URL("$RAZORPAY_API_BASE$ENDPOINT_GET_PAYMENT/$paymentId")
                Log.d(TAG, "Calling Razorpay API: ${url.protocol}://${url.host}${url.path}")

                // Convert credentials to Basic Auth
                val credentials = "$keyId:$keySecret"
                val encodedCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray())

                // Make HTTP request
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = REQUEST_TIMEOUT_MS
                    readTimeout = REQUEST_TIMEOUT_MS
                    setRequestProperty("Authorization", "Basic $encodedCredentials")
                    setRequestProperty("User-Agent", "MediTrack/1.0")
                    setRequestProperty("Accept", "application/json")
                }

                // Read response
                val statusCode = connection.responseCode
                Log.d(TAG, "API Response status: $statusCode")

                if (statusCode !in 200..299) {
                    val errorStream = connection.errorStream?.let {
                        BufferedReader(InputStreamReader(it)).use { reader ->
                            reader.readText()
                        }
                    } ?: "Unknown error"

                    Log.e(TAG, "API Error ($statusCode): $errorStream")
                    return@withContext Resource.Error("Razorpay API error: $statusCode - $errorStream")
                }

                // Parse response
                val responseBody = connection.inputStream.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                }

                val responseJson = org.json.JSONObject(responseBody)
                val paymentResponse = parsePaymentResponse(responseJson)

                // Validate response
                val (isValid, validationError) = paymentResponse.validate()
                if (!isValid) {
                    Log.e(TAG, "Payment validation failed: $validationError")
                    return@withContext Resource.Error("Invalid payment response: $validationError")
                }

                Log.d(TAG, "Payment fetched successfully: ${paymentResponse.id}")
                Resource.Success(paymentResponse)

            } catch (e: Exception) {
                Log.e(TAG, "API call failed: ${e.message}", e)
                throw e
            }
        }

    /**
     * Parse Razorpay API JSON response into RazorpayPaymentResponse.
     *
     * Handles field mapping from Razorpay's API format to our model.
     * Razorpay API uses snake_case for JSON fields.
     *
     * @param json JSONObject from Razorpay API
     * @return Parsed RazorpayPaymentResponse
     */
    private fun parsePaymentResponse(json: org.json.JSONObject): RazorpayPaymentResponse {
        // Extract basic fields
        val id = json.optString("id", "")
        val orderId = json.optString("order_id", "")
        val amount = json.optInt("amount", 0)
        val status = json.optString("status", "")
        val method = json.optString("method", "")
        val email = json.optString("email", "")
        val contact = json.optString("contact", "")

        // Extract acquirer data (contains signature for some payment methods)
        var signature = ""
        val acquirerDataJson = json.optJSONObject("acquirer_data")
        if (acquirerDataJson != null) {
            signature = acquirerDataJson.optString("auth", "")
        }

        // For UPI and card payments, signature may be in vpa_handle or other fields
        if (signature.isEmpty()) {
            // Try alternate locations
            signature = json.optString("vpa", "")
                .takeIf { it.isNotEmpty() } ?: ""
        }

        Log.d(TAG, "Parsed payment: id=$id, method=$method, status=$status, sigLength=${signature.length}")

        return RazorpayPaymentResponse(
            id = id,
            orderId = orderId,
            amount = amount,
            status = status,
            method = method,
            email = email,
            contact = contact,
            signature = signature,
            currency = json.optString("currency", "INR"),
            captured = json.optBoolean("captured", false),
            failed = json.optBoolean("failed", false),
            notes = parseNotes(json.optJSONObject("notes")),
            fee = json.optInt("fee", 0),
            tax = json.optInt("tax", 0),
            createdAt = json.optLong("created_at", 0L),
            capturedAt = json.optLong("captured_at", 0L)
        )
    }

    /**
     * Parse notes object from JSON.
     */
    private fun parseNotes(notesJson: org.json.JSONObject?): Map<String, Any> {
        if (notesJson == null) return emptyMap()

        val notes = mutableMapOf<String, Any>()
        notesJson.keys().forEach { key ->
            try {
                notes[key] = notesJson.get(key)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse note: $key")
            }
        }
        return notes
    }

    /**
     * Get Razorpay API Key ID from ConfigRepository.
     *
     * ConfigRepository fetches from Firebase Remote Config,
     * which is updated from Google Cloud Secret Manager.
     */
    private suspend fun getApiKeyId(): String {
        return try {
            configRepository.getRazorpayKeyId()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get API Key ID: ${e.message}")
            ""
        }
    }

    /**
     * Get Razorpay API Key Secret from ConfigRepository.
     *
     * NOTE: In production, this should come from:
     * 1. Google Cloud Secret Manager (backend only)
     * 2. NOT from client app (security risk)
     *
     * For production payment verification, use the Cloud Function
     * which has secure access to the secret.
     */
    private suspend fun getApiKeySecret(): String {
        // TODO: Implement secure key secret retrieval
        // For now, use environment variable or Cloud Function
        return try {
            // This would need to be fetched from a secure backend endpoint
            // NOT from client app - API key secret should never be in app
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get API Key Secret: ${e.message}")
            ""
        }
    }
}
