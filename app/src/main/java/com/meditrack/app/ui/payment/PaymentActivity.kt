package com.meditrack.app.ui.payment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.meditrack.app.R
import com.meditrack.app.databinding.ActivityPaymentBinding
import com.meditrack.app.data.repository.ConfigRepository
import com.meditrack.app.util.RazorpayKeyValidator
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
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
class PaymentActivity : AppCompatActivity(), PaymentResultWithDataListener {

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
    lateinit var configRepository: ConfigRepository

    private val mainScope = MainScope()
    private var checkoutKeyResolution: Deferred<ConfigRepository.RazorpayKeyResolution>? = null
    private var resolvedCheckoutKeyId: String? = null
    private var resolvedCheckoutKeySource: ConfigRepository.RazorpayKeySource =
        ConfigRepository.RazorpayKeySource.NONE

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

        // Resolve checkout key in parallel so order creation does not wait on network fetch.
        primeCheckoutKeyResolution()

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
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing Razorpay: ${e.message}", e)
            showErrorState("Payment initialization failed. Please try again.\n${e.message}")
        }
    }

    private fun primeCheckoutKeyResolution() {
        checkoutKeyResolution = mainScope.async {
            val keyResolution = configRepository.resolveCheckoutKey(readResourceFallbackKey())
            resolvedCheckoutKeyId = keyResolution.key
            resolvedCheckoutKeySource = keyResolution.source

            when (keyResolution.source) {
                ConfigRepository.RazorpayKeySource.REMOTE_CONFIG -> {
                    Log.d(TAG, "✅ Razorpay key resolved from Remote Config")
                }
                ConfigRepository.RazorpayKeySource.RESOURCE_FALLBACK -> {
                    Log.w(TAG, "⚠️ Razorpay key fallback to build-time resource")
                }
                ConfigRepository.RazorpayKeySource.NONE -> {
                    Log.e(TAG, "❌ Razorpay key resolution failed: ${keyResolution.reason}")
                }
            }

            keyResolution
        }
    }

    private fun readResourceFallbackKey(): String? {
        return try {
            resources.getString(R.string.razorpay_key_id).trim().ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read Razorpay fallback key from resources: ${e.message}")
            null
        }
    }

    private suspend fun resolveCheckoutKey(): ConfigRepository.RazorpayKeyResolution {
        val primed = try {
            checkoutKeyResolution?.await()
        } catch (e: Exception) {
            Log.e(TAG, "Primed key resolution failed: ${e.message}")
            null
        }

        val resolved = primed ?: configRepository.resolveCheckoutKey(readResourceFallbackKey())
        resolvedCheckoutKeyId = resolved.key
        resolvedCheckoutKeySource = resolved.source
        return resolved
    }

    private fun isDebuggableBuild(): Boolean {
        return (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
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
        mainScope.launch {
            try {
                // Validate order before checkout
                if (order.razorpayOrderId.isEmpty()) {
                    Log.e(TAG, "❌ Order ID is empty!")
                    showErrorState("Payment order creation failed. Order ID is missing.")
                    return@launch
                }

                Log.d(TAG, "ℹ️ Starting Razorpay checkout...")
                Log.d(TAG, "   Order ID: ${order.razorpayOrderId}")
                Log.d(TAG, "   Amount: ₹${order.amount / 100.0}")  // Convert paise to rupees
                Log.d(TAG, "   Email: $userEmail")

                val keyResolution = resolveCheckoutKey()
                val razorpayKeyId = keyResolution.key

                if (razorpayKeyId.isNullOrEmpty()) {
                    Log.e(TAG, "❌ Razorpay API key unresolved: ${keyResolution.reason}")
                    showErrorState("Payment system not properly configured. Please contact support.")
                    return@launch
                }

                if (!RazorpayKeyValidator.isValidKeyFormat(razorpayKeyId) ||
                    RazorpayKeyValidator.isPlaceholderKey(razorpayKeyId)) {
                    Log.e(TAG, "❌ Razorpay key failed validation checks")
                    showErrorState("Payment key configuration is invalid. Please contact support.")
                    return@launch
                }

                if (!isDebuggableBuild() && RazorpayKeyValidator.isTestKey(razorpayKeyId)) {
                    Log.e(TAG, "❌ Test key detected in release build")
                    showErrorState("Payment configuration mismatch. Please contact support.")
                    return@launch
                }

                Log.d(TAG, "✅ Using Razorpay key source: ${keyResolution.source}")
                Log.d(TAG, "✅ Using Razorpay key: ${razorpayKeyId.take(10)}...")

                val checkout = Checkout()
                val options = JSONObject().apply {
                    put("key", razorpayKeyId)
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
                checkout.open(this@PaymentActivity, options)

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
    }

    /**
     * Razorpay success callback with full payment metadata.
     *
     * Uses callback-provided orderId/paymentId/signature directly instead of making
     * client-side Razorpay API calls that require secret credentials in the app.
     */
    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        val callbackPaymentId = paymentData?.paymentId?.takeIf { it.isNotBlank() } ?: razorpayPaymentId
        val callbackOrderId = paymentData?.orderId?.takeIf { it.isNotBlank() } ?: razorpayOrderId
        val callbackSignature = paymentData?.signature?.takeIf { it.isNotBlank() }

        Log.d(
            TAG,
            "Payment success callback: paymentId=$callbackPaymentId, orderId=$callbackOrderId"
        )

        if (callbackPaymentId.isNullOrEmpty()) {
            Log.e(TAG, "Payment callback missing payment ID")
            showErrorState("Payment completed but payment ID is missing")
            return
        }

        if (callbackSignature.isNullOrEmpty()) {
            val errorMsg = "Payment callback missing verification signature"
            Log.e(TAG, errorMsg)
            showErrorState(errorMsg)
            viewModel.handlePaymentFailure(
                razorpayOrderId = callbackOrderId,
                meditrackOrderId = meditrackOrderId,
                errorCode = "SIGNATURE_MISSING",
                errorDescription = errorMsg,
                errorSource = "callback"
            )
            return
        }

        if (callbackOrderId != razorpayOrderId) {
            Log.w(
                TAG,
                "Callback orderId differs from local orderId: local=$razorpayOrderId, callback=$callbackOrderId"
            )
        }

        showProgressState("Completing payment verification...")
        viewModel.handlePaymentSuccess(
            razorpayOrderId = callbackOrderId,
            razorpayPaymentId = callbackPaymentId,
            razorpaySignature = callbackSignature,
            meditrackOrderId = meditrackOrderId,
            paymentMethod = "UPI"
        )
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
    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        val callbackOrderId = paymentData?.orderId?.takeIf { it.isNotBlank() }
            ?: razorpayOrderId.ifEmpty { "order_error_${System.currentTimeMillis()}" }

        Log.e(
            TAG,
            "Payment error callback: code=$code, response=$response, callbackOrderId=$callbackOrderId"
        )

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
                    razorpayOrderId = callbackOrderId,
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
                    razorpayOrderId = callbackOrderId,
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
                    razorpayOrderId = callbackOrderId,
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
                    razorpayOrderId = callbackOrderId,
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
                    razorpayOrderId = callbackOrderId,
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
