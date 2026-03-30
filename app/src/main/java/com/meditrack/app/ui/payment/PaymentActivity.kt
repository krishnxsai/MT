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
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject

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
     * The Razorpay SDK reads the API key from AndroidManifest.xml meta-data during initialization.
     * This method verifies the SDK is ready and optionally logs Remote Config key availability.
     *
     * CRITICAL: The manifest meta-data is REQUIRED for SDK to function.
     * Remote Config can be used for monitoring or key rotation strategy in the future.
     */
    private fun initializeRazorpayCheckout() {
        try {
            // Preload SDK - this triggers the SDK to read manifest meta-data
            Checkout.preload(applicationContext)
            Log.d(TAG, "Razorpay SDK preloaded successfully")

            // Optional: Check if Remote Config has key (for monitoring/future key rotation)
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val remoteConfigKey = remoteConfig.getString("razorpay_key_id")

            if (remoteConfigKey.isNotEmpty()) {
                Log.d(TAG, "Remote Config has Razorpay key available")
            } else {
                Log.w(TAG, "Remote Config: razorpay_key_id not configured - using manifest meta-data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Razorpay: ${e.message}", e)
            showErrorState("Payment system initialization failed: ${e.message}")
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
            val checkout = Checkout()

            val options = JSONObject()
            options.put("name", "MediTrack")
            options.put("description", "Medicine Order #$meditrackOrderId")
            options.put("image", R.drawable.ic_launcher_foreground)  // App logo
            options.put("order_id", order.razorpayOrderId)
            options.put("amount", order.amount)  // Amount in paise
            options.put("currency", "INR")
            options.put("email", userEmail)
            options.put("contact", userPhone)
            options.put("method", "upi")  // Can be 'upi', 'card', 'netbanking', etc.
            options.put("timeout", 900)  // 15 minutes

            // Theme
            options.put("theme.color", "#1976D2")  // Material Blue

            Log.d(TAG, "Starting Razorpay checkout for order: ${order.razorpayOrderId}")
            checkout.open(this, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting checkout: ${e.message}", e)
            showErrorState("Failed to open payment checkout: ${e.message}")
        }
    }

    /**
     * Razorpay success callback.
     * Called when payment is successful.
     *
     * NOTE: The Razorpay SDK provides the payment ID here.
     * The full response (including signature) should be obtained from Razorpay's
     * response handler or fetched from their API.
     *
     * For production: Integrate with a callback that provides the complete response
     * including razorpay_signature, or fetch it from Razorpay's payment details API.
     */
    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        Log.d(TAG, "Payment successful callback: $razorpayPaymentId")

        if (razorpayPaymentId.isNullOrEmpty()) {
            Log.e(TAG, "Payment ID is null/empty")
            showErrorState("Payment completed but ID missing")
            return
        }

        // In production: Get the actual razorpay_signature from Razorpay's response
        // For now, this would be fetched from a callback that has access to the full response
        // TODO: Implement proper signature retrieval from Razorpay callback

        // Fetch payment details from Razorpay API to get the signature
        // OR implement a callback handler that captures the full response
        val signature = razorpayPaymentId  // Placeholder - should be actual Razorpay signature

        // Verify payment and record it
        viewModel.handlePaymentSuccess(
            razorpayOrderId = razorpayOrderId,
            razorpayPaymentId = razorpayPaymentId,
            razorpaySignature = signature,
            meditrackOrderId = meditrackOrderId,
            paymentMethod = "UPI"
        )
    }

    /**
     * Razorpay error callback.
     * Called when payment fails.
     */
    override fun onPaymentError(code: Int, response: String?) {
        Log.e(TAG, "Payment error: code=$code, response=$response")

        viewModel.handlePaymentFailure(
            razorpayOrderId = razorpayOrderId.ifEmpty { "order_error_${System.currentTimeMillis()}" },
            meditrackOrderId = meditrackOrderId,
            errorCode = code.toString(),
            errorDescription = response ?: "Payment failed: Unknown error",
            errorSource = "payment_method"
        )
    }
}
