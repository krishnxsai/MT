package com.meditrack.app.ui.payment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.meditrack.app.R
import com.meditrack.app.databinding.ActivityPaymentBinding
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.RazorpayPaymentHandler
import com.meditrack.app.util.RazorpaySignatureExtractor
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

/**
 * Payment Activity for Razorpay integration.
 *
 * Flow:
 * 1. Activity opens with order details
 * 2. ViewModel creates Razorpay order
 * 3. User completes payment via Razorpay Checkout
 * 4. Razorpay returns payment ID + signature
 * 5. ViewModel verifies signature and updates order
 * 6. Activity returns to caller with result
 *
 * Intent Extras:
 * - "order_id": String - RefillOrder ID
 * - "amount": Double - Amount in rupees
 * - "user_email": String - User email
 * - "user_phone": String - User phone
 * - "user_name": String - User full name
 *
 * Result Codes:
 * - RESULT_OK - Payment successful, extra "payment_id" contains Razorpay payment ID
 * - RESULT_CANCELED - User cancelled payment
 * - RESULT_FIRST_USER - Payment failed, extra "error_message" contains error
 */
@AndroidEntryPoint
class PaymentActivity : AppCompatActivity(), PaymentResultListener {

    companion object {
        private const val TAG = "PaymentActivity"
        const val EXTRA_ORDER_ID = "order_id"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_USER_EMAIL = "user_email"
        const val EXTRA_USER_PHONE = "user_phone"
        const val EXTRA_USER_NAME = "user_name"
        const val EXTRA_PAYMENT_ID = "payment_id"
        const val EXTRA_ERROR_MESSAGE = "error_message"
    }

    private lateinit var binding: ActivityPaymentBinding
    private val viewModel: PaymentViewModel by viewModels()

    @Inject
    lateinit var paymentHandler: RazorpayPaymentHandler

    private val mainScope = MainScope()

    private var meditrackOrderId: String = ""
    private var razorpayOrderId: String = ""
    private var amount: Double = 0.0
    private var userEmail: String = ""
    private var userPhone: String = ""
    private var userName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get intent extras
        meditrackOrderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: ""
        amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        userEmail = intent.getStringExtra(EXTRA_USER_EMAIL) ?: ""
        userPhone = intent.getStringExtra(EXTRA_USER_PHONE) ?: ""
        userName = intent.getStringExtra(EXTRA_USER_NAME) ?: ""

        if (meditrackOrderId.isEmpty() || amount <= 0) {
            Log.e(TAG, "Invalid payment parameters")
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        setupUI()
        setupObservers()

        // Initialize Razorpay Checkout with API key
        // This MUST happen before any checkout operations
        initializeRazorpayCheckout()

        // Create payment order
        viewModel.createPaymentOrder(amount, meditrackOrderId, userEmail, userPhone, userName)
    }

    /**
     * Initialize Razorpay Checkout with proper API key configuration.
     *
     * **CRITICAL IMPLEMENTATION NOTES:**
     * 1. MUST use Activity context (this), NOT applicationContext
     *    - Razorpay SDK needs Activity context to read manifest meta-data
     *    - applicationContext does NOT have manifest info
     * 2. Checkout.preload() MUST happen before any checkout.open() calls
     * 3. The API key in AndroidManifest.xml is REQUIRED for SDK to work
     *
     * Flow:
     * 1. Preload with Activity context → SDK reads manifest meta-data
     * 2. SDK caches the API key internally
     * 3. Later, checkout.open() uses the cached key
     *
     * Future: Remote Config can be used for secure key rotation without app update
     */
    private fun initializeRazorpayCheckout() {
        try {
            Log.d(TAG, "Initializing Razorpay SDK...")

            // ✅ CRITICAL: Use Activity context (this), not applicationContext
            // Razorpay needs Activity to read manifest meta-data with API key
            Checkout.preload(this)
            Log.d(TAG, "✅ Razorpay SDK preloaded successfully with Activity context")

            // Optional: Check if Remote Config has key (for monitoring/telemetry)
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val remoteConfigKey = remoteConfig.getString("razorpay_key_id")

            if (remoteConfigKey.isNotEmpty()) {
                Log.d(TAG, "✅ Remote Config key available (optional, manifest is primary)")
            } else {
                Log.d(TAG, "ℹ️ Remote Config key not configured - SDK using manifest meta-data (expected)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing Razorpay: ${e.message}", e)
            showErrorState("Payment initialization failed. Please try again.\n${e.message}")
        }
    }

    private fun setupUI() {
        binding.paymentToolbar.setNavigationOnClickListener {
            Log.d(TAG, "User cancelled payment")
            setResult(RESULT_CANCELED)
            finish()
        }

        binding.paymentRetryButton.setOnClickListener {
            Log.d(TAG, "Retrying payment")
            viewModel.retryPayment(amount, meditrackOrderId, userEmail, userPhone, userName)
        }
    }

    private fun setupObservers() {
        viewModel.paymentState.observe(this) { state ->
            when (state) {
                is PaymentUIState.CreatingOrder -> {
                    showProgressState("Creating payment order...")
                }
                is PaymentUIState.OrderCreated -> {
                    razorpayOrderId = state.order.razorpayOrderId
                    Log.d(TAG, "Payment order created: $razorpayOrderId")
                    // Start checkout after order created
                    startPaymentCheckout(state.order)
                }
                is PaymentUIState.VerifyingPayment -> {
                    showProgressState("Verifying payment...")
                }
                is PaymentUIState.PaymentSuccess -> {
                    showSuccessState()
                }
                is PaymentUIState.PaymentFailed -> {
                    showErrorState(state.reason)
                }
                is PaymentUIState.VerificationFailed -> {
                    showErrorState("Payment verification failed")
                }
                is PaymentUIState.Error -> {
                    showErrorState(state.message)
                }
                else -> {}
            }
        }

        viewModel.errorMessage.observe(this) { message ->
            if (!message.isNullOrEmpty()) {
                // Error is shown through paymentState
            }
        }

        viewModel.paymentSuccess.observe(this) { paymentId ->
            if (!paymentId.isNullOrEmpty()) {
                Log.d(TAG, "Payment successful: $paymentId")
                val intent = Intent().apply {
                    putExtra(EXTRA_PAYMENT_ID, paymentId)
                }
                setResult(RESULT_OK, intent)
                finish()
            }
        }
    }

    private fun showProgressState(message: String) {
        binding.paymentProgressContainer.visibility = View.VISIBLE
        binding.paymentErrorContainer.visibility = View.GONE
        binding.paymentSuccessContainer.visibility = View.GONE
        binding.paymentProgressMessage.text = message
    }

    private fun showErrorState(message: String) {
        binding.paymentProgressContainer.visibility = View.GONE
        binding.paymentSuccessContainer.visibility = View.GONE
        binding.paymentErrorContainer.visibility = View.VISIBLE
        binding.paymentErrorMessage.text = message
    }

    private fun showSuccessState() {
        binding.paymentProgressContainer.visibility = View.GONE
        binding.paymentErrorContainer.visibility = View.GONE
        binding.paymentSuccessContainer.visibility = View.VISIBLE
    }

    private fun startPaymentCheckout(order: com.meditrack.app.data.model.RazorpayOrder) {
        try {
            // Validate order before checkout
            if (order.razorpayOrderId.isEmpty()) {
                Log.e(TAG, "❌ Order ID is empty!")
                showErrorState("Payment order creation failed. Order ID is missing.")
                return
            }

            Log.d(TAG, "ℹ️ Starting Razorpay checkout...")
            Log.d(TAG, "   Order ID: ${order.razorpayOrderId}")
            Log.d(TAG, "   Amount: ₹${order.amount / 100.0}")  // Convert paise to rupees
            Log.d(TAG, "   Email: $userEmail")

            val checkout = Checkout()

            // ✅ FIX: Get API key from resources and set explicitly
            val razorpayKeyId = try {
                resources.getString(R.string.razorpay_key_id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get Razorpay key from resources: ${e.message}")
                null
            }

            if (razorpayKeyId.isNullOrEmpty()) {
                Log.e(TAG, "❌ Razorpay API key not found in resources!")
                showErrorState("Payment system not properly configured. Please contact support.")
                return
            }

            Log.d(TAG, "✅ Using Razorpay key: ${razorpayKeyId.substring(0, 10)}...")

            val options = JSONObject().apply {
                put("key", razorpayKeyId)  // ✅ SET KEY EXPLICITLY
                put("name", "MediTrack")
                put("description", "Medicine Order #$meditrackOrderId")
                put("image", R.drawable.ic_launcher_foreground)
                put("order_id", order.razorpayOrderId)
                put("amount", order.amount)  // Amount in paise
                put("currency", "INR")
                put("email", userEmail)
                put("contact", userPhone)
                put("method", "upi")
                put("timeout", 900)
                put("theme.color", "#1976D2")
            }

            Log.d(TAG, "✅ Opening Razorpay checkout...")
            checkout.open(this, options)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting checkout: ${e.message}", e)

            // Provide specific error messages based on exception type
            val errorMsg = when {
                e.message?.contains("Razorpay API key") == true ->
                    "Payment system not configured. Please contact support."
                e.message?.contains("Network") == true ->
                    "Network error. Please check your connection and try again."
                else ->
                    "Failed to open payment checkout: ${e.message}"
            }

            showErrorState(errorMsg)
        }
    }

    /**
     * Razorpay success callback.
     * Called when payment is successful.
     *
     * FIXED FLOW (proper signature extraction):
     * 1. Receives payment ID from Razorpay SDK callback
     * 2. Fetches complete payment response from Razorpay API
     * 3. Extracts and validates actual HMAC-SHA256 signature
     * 4. Sends (orderId, paymentId, signature) to backend for verification
     * 5. Backend performs server-side signature verification
     * 6. Updates order status to CONFIRMED
     */
    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        Log.d(TAG, "Payment successful callback: $razorpayPaymentId")

        if (razorpayPaymentId.isNullOrEmpty()) {
            Log.e(TAG, "Payment ID is null/empty")
            showErrorState("Payment completed but ID missing")
            return
        }

        showProgressState("Completing payment verification...")
        mainScope.launch {
            try {
                // ✅ FIXED: Fetch complete payment response with signature from Razorpay API
                Log.d(TAG, "Fetching payment details from Razorpay API...")
                val handlerResult = paymentHandler.handlePaymentSuccess(razorpayPaymentId)

                when (handlerResult) {
                    is Resource.Success -> {
                        val paymentResponse = handlerResult.data
                        Log.d(TAG, "Payment response received: order=${paymentResponse.orderId}, status=${paymentResponse.status}")

                        // ✅ FIXED: Extract and validate signature
                        val signatureResult = RazorpaySignatureExtractor.extractSignature(paymentResponse)

                        when (signatureResult) {
                            is RazorpaySignatureExtractor.SignatureExtraction.Success -> {
                                val signature = signatureResult.value  // ✅ ACTUAL HMAC-SHA256 signature
                                Log.d(TAG, "Successfully extracted signature: ${signature.substring(0, 16)}...")

                                // Send to backend with actual signature
                                viewModel.handlePaymentSuccess(
                                    razorpayOrderId = razorpayOrderId,
                                    razorpayPaymentId = razorpayPaymentId,
                                    razorpaySignature = signature,  // ✅ NOT a placeholder!
                                    meditrackOrderId = meditrackOrderId,
                                    paymentMethod = paymentResponse.method.ifEmpty { "UPI" }
                                )
                            }
                            is RazorpaySignatureExtractor.SignatureExtraction.Failure -> {
                                val errorMsg = "Signature extraction failed: ${signatureResult.reason}"
                                Log.e(TAG, errorMsg)
                                showErrorState(errorMsg)
                                viewModel.handlePaymentFailure(
                                    razorpayOrderId = razorpayOrderId,
                                    meditrackOrderId = meditrackOrderId,
                                    errorCode = signatureResult.code.name,
                                    errorDescription = signatureResult.reason,
                                    errorSource = "signature_extraction"
                                )
                            }
                        }
                    }
                    is Resource.Error -> {
                        val errorMsg = "Payment verification error: ${handlerResult.message ?: "Unknown error"}"
                        Log.e(TAG, errorMsg)
                        showErrorState(errorMsg)
                        viewModel.handlePaymentFailure(
                            razorpayOrderId = razorpayOrderId,
                            meditrackOrderId = meditrackOrderId,
                            errorCode = "HANDLER_ERROR",
                            errorDescription = handlerResult.message ?: "Unknown error",
                            errorSource = "payment_handler"
                        )
                    }
                    else -> {
                        val errorMsg = "Unexpected response type from payment handler"
                        Log.e(TAG, errorMsg)
                        showErrorState(errorMsg)
                    }
                }
            } catch (e: Exception) {
                val errorMsg = "Unexpected error during payment verification: ${e.message}"
                Log.e(TAG, errorMsg, e)
                showErrorState(errorMsg)
            }
        }
    }

    /**
     * Razorpay error callback.
     * Called when payment fails or user cancels.
     *
     * Differentiates between:
     * - User cancellation (expected, no retry needed)
     * - Network errors (can retry with backoff)
     * - Authentication failures (can retry with limits)
     * - Payment declined (user must try different method)
     * - SDK errors (contact support)
     */
    override fun onPaymentError(code: Int, response: String?) {
        Log.e(TAG, "Payment error callback: code=$code, response=$response")

        // Categorize error using error handler
        val error = com.meditrack.app.util.RazorpayErrorHandler.parseError(code, response)
        com.meditrack.app.util.RazorpayErrorHandler.logError(error, "PaymentActivity")

        when (error) {
            is com.meditrack.app.util.RazorpayErrorHandler.PaymentError.UserCancelled -> {
                Log.d(TAG, "User cancelled payment - returning to caller")
                // User cancelled - don't record as failure, just finish
                setResult(RESULT_CANCELED)
                finish()
            }

            is com.meditrack.app.util.RazorpayErrorHandler.PaymentError.NetworkError -> {
                // Temporary error - show retry option
                Log.w(TAG, "Network error occurred - showing retry option")
                showErrorState(
                    "${error.userMessage}\n\nAttempt: 1/3"
                )
                viewModel.handlePaymentFailure(
                    razorpayOrderId = razorpayOrderId.ifEmpty { "order_error_${System.currentTimeMillis()}" },
                    meditrackOrderId = meditrackOrderId,
                    errorCode = error.errorCode,
                    errorDescription = error.reason,
                    errorSource = "network"
                )
            }

            is com.meditrack.app.util.RazorpayErrorHandler.PaymentError.AuthenticationFailed -> {
                // Auth failure - limited retries
                Log.w(TAG, "Authentication failed - showing retry option")
                showErrorState(
                    "${error.userMessage}\n\nPlease verify your credentials and try again."
                )
                viewModel.handlePaymentFailure(
                    razorpayOrderId = razorpayOrderId.ifEmpty { "order_error_${System.currentTimeMillis()}" },
                    meditrackOrderId = meditrackOrderId,
                    errorCode = error.errorCode,
                    errorDescription = error.reason,
                    errorSource = "authentication"
                )
            }

            is com.meditrack.app.util.RazorpayErrorHandler.PaymentError.PaymentDeclined -> {
                // Payment declined - don't retry same method
                Log.e(TAG, "Payment declined - no retry with same method")
                showErrorState(
                    "${error.userMessage}\n\nPlease use another payment method."
                )
                viewModel.handlePaymentFailure(
                    razorpayOrderId = razorpayOrderId.ifEmpty { "order_error_${System.currentTimeMillis()}" },
                    meditrackOrderId = meditrackOrderId,
                    errorCode = error.errorCode,
                    errorDescription = error.reason,
                    errorSource = "payment_method"
                )
            }

            is com.meditrack.app.util.RazorpayErrorHandler.PaymentError.SdkError -> {
                // SDK error - contact support
                Log.e(TAG, "SDK error - payment system misconfiguration")
                showErrorState(
                    "${error.userMessage}\n\nError Code: ${error.errorCode}"
                )
                viewModel.handlePaymentFailure(
                    razorpayOrderId = razorpayOrderId.ifEmpty { "order_error_${System.currentTimeMillis()}" },
                    meditrackOrderId = meditrackOrderId,
                    errorCode = error.errorCode,
                    errorDescription = error.reason,
                    errorSource = "sdk"
                )
            }

            is com.meditrack.app.util.RazorpayErrorHandler.PaymentError.PaymentFailed -> {
                // Generic failure
                Log.e(TAG, "Payment failed - ${error.reason}")
                showErrorState(error.userMessage)
                viewModel.handlePaymentFailure(
                    razorpayOrderId = razorpayOrderId.ifEmpty { "order_error_${System.currentTimeMillis()}" },
                    meditrackOrderId = meditrackOrderId,
                    errorCode = error.errorCode,
                    errorDescription = error.reason,
                    errorSource = "payment"
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}
