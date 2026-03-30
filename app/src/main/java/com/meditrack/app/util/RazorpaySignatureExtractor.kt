package com.meditrack.app.util

import android.util.Log
import com.meditrack.app.data.model.RazorpayPaymentResponse

/**
 * Utility for safely extracting and validating Razorpay signatures.
 *
 * Razorpay signatures are HMAC-SHA256 hashes that are used to verify
 * payment authenticity. This utility ensures:
 * - Signature format is valid (64 hex characters)
 * - Signature is extracted from correct field
 * - Validation errors are handled gracefully
 *
 * Security:
 * - Validates signature format before using
 * - Prevents empty/null signature injection
 * - Logs all validation attempts
 * - Supports audit trail
 *
 * Usage:
 * ```
 * val response = paymentHandler.handlePaymentSuccess(paymentId)
 * when (val signature = RazorpaySignatureExtractor.extractSignature(response)) {
 *   is SignatureExtraction.Success -> {
 *     verifyPayment(orderId, paymentId, signature.value)
 *   }
 *   is SignatureExtraction.Failure -> {
 *     handleError(signature.reason)
 *   }
 * }
 * ```
 */
object RazorpaySignatureExtractor {

    private const val TAG = "RazorpaySignatureExtractor"

    /**
     * Expected signature format: 64 hex characters (SHA256 hash)
     * 64 chars × 4 bits/char = 256 bits ✓
     */
    private const val SIGNATURE_PATTERN = "^[a-f0-9]{64}$"
    private const val SIGNATURE_LENGTH = 64

    /**
     * Represents the result of signature extraction.
     */
    sealed class SignatureExtraction {
        /**
         * Successfully extracted and validated signature.
         * @param value The valid signature (64 hex characters)
         */
        data class Success(val value: String) : SignatureExtraction()

        /**
         * Failed to extract/validate signature.
         * @param reason Human-readable error explanation
         * @param code Error code for programmatic handling
         */
        data class Failure(val reason: String, val code: ErrorCode) : SignatureExtraction()
    }

    /**
     * Error codes for signature extraction failures.
     */
    enum class ErrorCode {
        RESPONSE_NULL,           // Payment response is null
        SIGNATURE_EMPTY,         // Signature field is empty
        SIGNATURE_NULL,          // Signature field is null
        INVALID_FORMAT,          // Signature format doesn't match SHA256
        INVALID_LENGTH,          // Signature has wrong length
        INVALID_CHARACTERS,      // Signature contains non-hex characters
        PAYMENT_NOT_CAPTURED,    // Payment status is not "captured"
        PAYMENT_STATUS_FAILED,   // Payment failed
        UNKNOWN                  // Unknown error
    }

    /**
     * Extract signature from Razorpay payment response.
     *
     * Performs comprehensive validation:
     * 1. Response is not null
     * 2. Signature field is not empty
     * 3. Signature is 64 hex characters (SHA256)
     * 4. Payment status is "captured"
     * 5. Payment is not failed
     *
     * @param response Razorpay payment response from API
     * @return SignatureExtraction.Success with valid signature
     *         SignatureExtraction.Failure with reason and error code
     */
    fun extractSignature(response: RazorpayPaymentResponse?): SignatureExtraction {
        // Check if response exists
        if (response == null) {
            Log.e(TAG, "Payment response is null")
            return SignatureExtraction.Failure(
                "Payment response is null",
                ErrorCode.RESPONSE_NULL
            )
        }

        // Check if signature exists
        if (response.signature.isEmpty()) {
            Log.e(TAG, "Signature is empty for payment: ${response.id}")
            return SignatureExtraction.Failure(
                "Signature is empty or not available",
                ErrorCode.SIGNATURE_EMPTY
            )
        }

        // Check payment status
        if (response.status != "captured") {
            Log.e(TAG, "Payment status is not 'captured': ${response.status}")
            return SignatureExtraction.Failure(
                "Payment status is not captured: ${response.status}",
                ErrorCode.PAYMENT_NOT_CAPTURED
            )
        }

        if (response.failed) {
            Log.e(TAG, "Payment is marked as failed")
            return SignatureExtraction.Failure(
                "Payment is marked as failed",
                ErrorCode.PAYMENT_STATUS_FAILED
            )
        }

        // Validate signature format
        val validation = validateSignatureFormat(response.signature)
        if (validation !is SignatureExtraction.Success) {
            Log.e(TAG, "Signature validation failed: ${(validation as SignatureExtraction.Failure).reason}")
            return validation
        }

        Log.d(TAG, "✅ Successfully extracted signature for payment: ${response.id}")
        return validation
    }

    /**
     * Validate signature format.
     *
     * Razorpay signatures are HMAC-SHA256 hashes represented as 64 hex characters.
     *
     * Valid: "abc123def456..." (64 hex chars)
     * Invalid: "abc123" (too short)
     * Invalid: "xyz" (non-hex characters)
     * Invalid: "G@B5%2K" (special characters)
     *
     * @param signature Signature string to validate
     * @return SignatureExtraction.Success if valid
     *         SignatureExtraction.Failure if invalid
     */
    fun validateSignatureFormat(signature: String): SignatureExtraction {
        // Check if empty
        if (signature.isEmpty()) {
            Log.e(TAG, "Signature is empty")
            return SignatureExtraction.Failure(
                "Signature is empty",
                ErrorCode.SIGNATURE_EMPTY
            )
        }

        // Check length
        if (signature.length != SIGNATURE_LENGTH) {
            Log.e(TAG, "Signature has invalid length: ${signature.length} (expected $SIGNATURE_LENGTH)")
            return SignatureExtraction.Failure(
                "Signature has invalid length: ${signature.length} (expected $SIGNATURE_LENGTH)",
                ErrorCode.INVALID_LENGTH
            )
        }

        // Check format (64 hex characters)
        if (!signature.matches(Regex(SIGNATURE_PATTERN))) {
            Log.e(TAG, "Signature format is invalid: $signature")
            return SignatureExtraction.Failure(
                "Signature must be 64 hexadecimal characters",
                ErrorCode.INVALID_FORMAT
            )
        }

        Log.d(TAG, "✅ Signature format is valid")
        return SignatureExtraction.Success(signature)
    }

    /**
     * Extract signature from response with fallback behavior.
     *
     * If extraction fails, provides detailed error information for logging/analytics.
     *
     * @param response Payment response
     * @param onFailure Callback with error details if extraction fails
     * @return Signature if successful, null if failed
     */
    fun extractSignatureOrNull(
        response: RazorpayPaymentResponse?,
        onFailure: (reason: String, errorCode: ErrorCode) -> Unit = { _, _ -> }
    ): String? {
        return when (val result = extractSignature(response)) {
            is SignatureExtraction.Success -> result.value
            is SignatureExtraction.Failure -> {
                onFailure(result.reason, result.code)
                null
            }
        }
    }

    /**
     * Get human-readable error message for error code.
     *
     * @param code Error code
     * @return User-friendly error message
     */
    fun getErrorMessage(code: ErrorCode): String = when (code) {
        ErrorCode.RESPONSE_NULL -> "Payment information unavailable"
        ErrorCode.SIGNATURE_EMPTY -> "Payment signature missing"
        ErrorCode.SIGNATURE_NULL -> "Payment signature is null"
        ErrorCode.INVALID_FORMAT -> "Payment signature format is invalid"
        ErrorCode.INVALID_LENGTH -> "Payment signature has incorrect length"
        ErrorCode.INVALID_CHARACTERS -> "Payment signature contains invalid characters"
        ErrorCode.PAYMENT_NOT_CAPTURED -> "Payment has not been confirmed yet"
        ErrorCode.PAYMENT_STATUS_FAILED -> "Payment failed"
        ErrorCode.UNKNOWN -> "Unknown error occurred"
    }

    /**
     * Log extraction details for audit trail.
     *
     * @param response Payment response
     * @param extraction Extraction result
     */
    fun logExtractionDetails(
        response: RazorpayPaymentResponse?,
        extraction: SignatureExtraction
    ) {
        val paymentId = response?.id ?: "unknown"
        val status = when (extraction) {
            is SignatureExtraction.Success -> "SUCCESS"
            is SignatureExtraction.Failure -> "FAILURE: ${extraction.code}"
        }

        Log.d(TAG, "Signature extraction complete - Payment: $paymentId, Status: $status")
    }
}
