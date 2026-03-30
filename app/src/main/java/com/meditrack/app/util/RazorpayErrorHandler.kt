package com.meditrack.app.util

import android.util.Log

/**
 * Comprehensive error handling for Razorpay payments.
 *
 * Differentiates between:
 * - User cancellation
 * - Network errors (retry-able)
 * - Payment failures (non-retry-able)
 * - SDK/validation errors
 *
 * Reference: https://razorpay.com/docs/payments/smart-routing/
 */
object RazorpayErrorHandler {

    private const val TAG = "RazorpayErrorHandler"

    /**
     * Categorizes payment errors into actionable types.
     */
    sealed class PaymentError {
        abstract val userMessage: String
        abstract val errorCode: String
        abstract val shouldRetry: Boolean
        abstract val logLevel: LogLevel

        /** User cancelled payment (not an error, expected outcome) */
        data class UserCancelled(
            val rawErrorMessage: String = ""
        ) : PaymentError() {
            override val userMessage = "Payment cancelled by user"
            override val errorCode = "USER_CANCELLED"
            override val shouldRetry = false
            override val logLevel = LogLevel.INFO
        }

        /** Network/temporary error (should retry with backoff) */
        data class NetworkError(
            val reason: String = "Network error"
        ) : PaymentError() {
            override val userMessage = "Network error. Please check your connection and try again."
            override val errorCode = "NETWORK_ERROR"
            override val shouldRetry = true
            override val logLevel = LogLevel.WARN
        }

        /** Authentication/OTP failure (user can retry) */
        data class AuthenticationFailed(
            val reason: String = "Authentication failed"
        ) : PaymentError() {
            override val userMessage = "$reason. Please try again with correct details."
            override val errorCode = "AUTH_FAILED"
            override val shouldRetry = true
            override val logLevel = LogLevel.WARN
        }

        /** Payment declined by bank/gateway (non-retry-able) */
        data class PaymentDeclined(
            val reason: String = "Payment declined"
        ) : PaymentError() {
            override val userMessage = "$reason. Please use another payment method or contact your bank."
            override val errorCode = "PAYMENT_DECLINED"
            override val shouldRetry = false
            override val logLevel = LogLevel.WARN
        }

        /** Razorpay SDK/validation error */
        data class SdkError(
            val reason: String = "Payment system error"
        ) : PaymentError() {
            override val userMessage = "$reason. Please contact support if this persists."
            override val errorCode = "SDK_ERROR"
            override val shouldRetry = false
            override val logLevel = LogLevel.ERROR
        }

        /** Generic payment failure */
        data class PaymentFailed(
            val reason: String = "Payment failed"
        ) : PaymentError() {
            override val userMessage = "$reason. Please try again."
            override val errorCode = "PAYMENT_FAILED"
            override val shouldRetry = true
            override val logLevel = LogLevel.ERROR
        }
    }

    enum class LogLevel {
        INFO, WARN, ERROR
    }

    /**
     * Parse Razorpay error code and response to categorized error.
     *
     * Razorpay Error Codes:
     * - 0: User cancelled
     * - 1: Connection error
     * - 2: Timeout
     * - 100-109: OTP validation errors
     * - 200-299: Network errors
     * - 500-599: Server errors
     *
     * @param code Error code from Razorpay callback
     * @param response Error message from Razorpay
     *
     * @return Categorized PaymentError
     */
    fun parseError(code: Int, response: String?): PaymentError {
        Log.d(TAG, "Parsing error - Code: $code, Response: $response")

        return when (code) {
            // User cancellation
            0 -> PaymentError.UserCancelled(response ?: "")

            // Connection/Network errors (temporary)
            1, 2, 10, 20 -> {
                val reason = when (code) {
                    1 -> "Connection lost"
                    2 -> "Request timeout"
                    10 -> "Network unavailable"
                    20 -> "Server timeout"
                    else -> "Network error"
                }
                PaymentError.NetworkError(reason)
            }

            // OTP/Authentication errors (user can retry)
            in 100..109 -> {
                val reason = when (code) {
                    101 -> "OTP validation failed"
                    102 -> "Invalid OTP"
                    103 -> "OTP expired"
                    else -> "Authentication failed - ${response ?: "OTP error"}"
                }
                PaymentError.AuthenticationFailed(reason)
            }

            // Payment method errors
            in 110..119 -> {
                val reason = when (code) {
                    111 -> "Invalid card"
                    112 -> "Card expired"
                    113 -> "Insufficient funds"
                    114 -> "Payment limit exceeded"
                    else -> "Payment method error - ${response ?: "Invalid method"}"
                }
                PaymentError.PaymentDeclined(reason)
            }

            // Bank/Gateway declined (non-retry-able)
            in 120..199 -> {
                PaymentError.PaymentDeclined("Bank declined - ${response ?: "Payment declined"}")
            }

            // Network/Server errors (temporary)
            in 200..299 -> {
                PaymentError.NetworkError("Server error - ${response ?: "Please retry"}")
            }

            // SDK/API errors
            in 300..399 -> {
                PaymentError.SdkError("API error - ${response ?: "SDK initialization error"}")
            }

            // Server errors (temporary)
            in 500..599 -> {
                PaymentError.NetworkError("Server error - ${response ?: "Please retry"}")
            }

            // Unknown/generic error
            else -> {
                if (response?.contains("timeout", ignoreCase = true) == true ||
                    response?.contains("network", ignoreCase = true) == true
                ) {
                    PaymentError.NetworkError(response)
                } else if (response?.contains("cancel", ignoreCase = true) == true) {
                    PaymentError.UserCancelled(response)
                } else {
                    PaymentError.PaymentFailed(response ?: "Unknown error (code: $code)")
                }
            }
        }
    }

    /**
     * Get retry strategy for an error.
     *
     * @param error PaymentError to analyze
     *
     * @return RetryStrategy with delay and max attempts
     */
    fun getRetryStrategy(error: PaymentError): RetryStrategy {
        return when (error) {
            is PaymentError.NetworkError -> RetryStrategy(
                shouldRetry = true,
                initialDelayMs = 1000,
                maxAttempts = 3,
                backoffMultiplier = 2.0
            )
            is PaymentError.AuthenticationFailed -> RetryStrategy(
                shouldRetry = true,
                initialDelayMs = 2000,
                maxAttempts = 2,  // Limited retries for auth failures
                backoffMultiplier = 1.5
            )
            else -> RetryStrategy(
                shouldRetry = false,
                initialDelayMs = 0,
                maxAttempts = 0,
                backoffMultiplier = 1.0
            )
        }
    }

    /**
     * Compute delay for retry attempt with exponential backoff.
     *
     * @param attempt Current attempt number (0-indexed)
     * @param baseDelayMs Initial delay in milliseconds
     * @param multiplier Exponential backoff multiplier
     * @param maxDelayMs Maximum delay cap
     *
     * @return Delay in milliseconds for this attempt
     */
    fun computeRetryDelay(
        attempt: Int,
        baseDelayMs: Long = 1000,
        multiplier: Double = 2.0,
        maxDelayMs: Long = 30000
    ): Long {
        if (attempt <= 0) return baseDelayMs

        val exponentialDelay = (baseDelayMs * Math.pow(multiplier, attempt.toDouble())).toLong()

        // Add small jitter to prevent thundering herd
        val jitter = (Math.random() * 1000).toLong()

        return minOf(exponentialDelay + jitter, maxDelayMs)
    }

    /**
     * Log error with appropriate level and context.
     */
    fun logError(error: PaymentError, context: String = "Payment") {
        val message = "$context: ${error.errorCode} - ${error.userMessage}"

        when (error.logLevel) {
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.WARN -> Log.w(TAG, message)
            LogLevel.ERROR -> Log.e(TAG, message)
        }
    }

    /**
     * Retry recommendation for payment flow.
     */
    data class RetryStrategy(
        val shouldRetry: Boolean,
        val initialDelayMs: Long,
        val maxAttempts: Int,
        val backoffMultiplier: Double
    )
}
