package com.meditrack.app.ui.payment

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.OrderRepository
import com.meditrack.app.data.repository.RazorpayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for payment processing via Razorpay.
 * Handles order creation, payment verification, and error handling.
 */
@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val razorpayRepository: RazorpayRepository,
    private val orderRepository: OrderRepository,
    private val configRepository: com.meditrack.app.data.repository.ConfigRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PaymentViewModel"
    }

    // UI state
    private val _paymentState = MutableLiveData<PaymentUIState>(PaymentUIState.Idle)
    val paymentState: LiveData<PaymentUIState> = _paymentState

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private val _paymentSuccess = MutableLiveData<String?>(null)  // Returns payment ID
    val paymentSuccess: LiveData<String?> = _paymentSuccess

    /**
     * Create a Razorpay payment order before showing payment UI.
     *
     * @param amount Amount in rupees
     * @param meditrackOrderId RefillOrder ID
     * @param userEmail User's email
     * @param userPhone User's phone
     * @param userName User's name
     */
    fun createPaymentOrder(
        amount: Double,
        meditrackOrderId: String,
        userEmail: String,
        userPhone: String,
        userName: String
    ) {
        viewModelScope.launch {
            _paymentState.value = PaymentUIState.CreatingOrder
            _errorMessage.value = null

            try {
                val result = razorpayRepository.createOrder(
                    amount = amount,
                    meditrackOrderId = meditrackOrderId,
                    customerEmail = userEmail,
                    customerPhone = userPhone,
                    customerName = userName
                )

                when (result) {
                    is Resource.Success -> {
                        val order = result.data
                        Log.d(TAG, "Payment order created: ${order.id}")
                        _paymentState.value = PaymentUIState.OrderCreated(order)
                    }
                    is Resource.Error -> {
                        Log.e(TAG, "Failed to create order: ${result.message}")
                        _errorMessage.value = result.message
                        _paymentState.value = PaymentUIState.Error(result.message)
                    }
                    is Resource.Loading -> {
                        _paymentState.value = PaymentUIState.CreatingOrder
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception creating order: ${e.message}")
                _errorMessage.value = "Failed to create payment order"
                _paymentState.value = PaymentUIState.Error("Failed to create payment order")
            }
        }
    }

    /**
     * Handle successful payment from Razorpay.
     * Verifies the signature and updates order status.
     *
     * @param razorpayOrderId Razorpay order ID
     * @param razorpayPaymentId Razorpay payment ID
     * @param razorpaySignature Razorpay signature for verification
     * @param meditrackOrderId Our order ID
     * @param paymentMethod Payment method used (UPI, CARD, etc.)
     */
    fun handlePaymentSuccess(
        razorpayOrderId: String,
        razorpayPaymentId: String,
        razorpaySignature: String,
        meditrackOrderId: String,
        paymentMethod: String = "UPI"
    ) {
        viewModelScope.launch {
            _paymentState.value = PaymentUIState.VerifyingPayment
            _errorMessage.value = null

            try {
                // Step 1: Verify payment signature
                val verifyResult = razorpayRepository.verifyPayment(
                    orderId = razorpayOrderId,
                    paymentId = razorpayPaymentId,
                    signature = razorpaySignature,
                    meditrackOrderId = meditrackOrderId
                )

                if (verifyResult !is Resource.Success || !verifyResult.data) {
                    Log.w(TAG, "Payment signature verification failed")
                    _errorMessage.value = "Payment verification failed. Please contact support."
                    _paymentState.value = PaymentUIState.VerificationFailed
                    return@launch
                }

                Log.d(TAG, "Payment signature verified successfully")

                // Step 2: Record payment success in Firestore
                val recordResult = razorpayRepository.recordPaymentSuccess(
                    paymentId = razorpayPaymentId,
                    orderId = razorpayOrderId,
                    signature = razorpaySignature,
                    meditrackOrderId = meditrackOrderId,
                    paymentMethod = paymentMethod
                )

                when (recordResult) {
                    is Resource.Success -> {
                        Log.d(TAG, "Payment recorded successfully")

                        // Step 3: Update order status to CONFIRMED
                        val updateResult = orderRepository.updateOrderStatus(
                            orderId = meditrackOrderId,
                            newStatus = com.meditrack.app.data.model.OrderStatus.CONFIRMED,
                            note = "Payment confirmed via $paymentMethod"
                        )

                        when (updateResult) {
                            is Resource.Success -> {
                                Log.d(TAG, "Order status updated to CONFIRMED")
                                _paymentState.value = PaymentUIState.PaymentSuccess
                                _paymentSuccess.value = razorpayPaymentId
                            }
                            is Resource.Error -> {
                                Log.e(TAG, "Failed to update order: ${updateResult.message}")
                                _errorMessage.value = "Order update failed: ${updateResult.message}"
                                _paymentState.value = PaymentUIState.Error(updateResult.message)
                            }
                            else -> {}
                        }
                    }
                    is Resource.Error -> {
                        Log.e(TAG, "Failed to record payment: ${recordResult.message}")
                        _errorMessage.value = recordResult.message
                        _paymentState.value = PaymentUIState.Error(recordResult.message)
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception handling payment success: ${e.message}")
                _errorMessage.value = "Failed to process payment confirmation"
                _paymentState.value = PaymentUIState.Error("Failed to process payment confirmation")
            }
        }
    }

    /**
     * Handle payment failure from Razorpay.
     */
    fun handlePaymentFailure(
        razorpayOrderId: String,
        meditrackOrderId: String,
        errorCode: String,
        errorDescription: String,
        errorSource: String = "payment_method"
    ) {
        viewModelScope.launch {
            _paymentState.value = PaymentUIState.RecordingFailure
            _errorMessage.value = null

            try {
                razorpayRepository.recordPaymentFailure(
                    orderId = razorpayOrderId,
                    meditrackOrderId = meditrackOrderId,
                    errorCode = errorCode,
                    errorDescription = errorDescription,
                    errorSource = errorSource
                )

                Log.w(TAG, "Payment failed: $errorCode - $errorDescription")
                _errorMessage.value = errorDescription
                _paymentState.value = PaymentUIState.PaymentFailed(errorDescription)
            } catch (e: Exception) {
                Log.e(TAG, "Exception recording payment failure: ${e.message}")
                _paymentState.value = PaymentUIState.PaymentFailed("Payment failed: ${e.message}")
            }
        }
    }

    /**
     * Clear error messages.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Retry payment after failure.
     */
    fun retryPayment(
        amount: Double,
        meditrackOrderId: String,
        userEmail: String,
        userPhone: String,
        userName: String
    ) {
        clearError()
        createPaymentOrder(amount, meditrackOrderId, userEmail, userPhone, userName)
    }
}

/**
 * UI state for payment flow.
 */
sealed class PaymentUIState {
    data object Idle : PaymentUIState()
    data object CreatingOrder : PaymentUIState()
    data class OrderCreated(val order: com.meditrack.app.data.model.RazorpayOrder) : PaymentUIState()
    data object VerifyingPayment : PaymentUIState()
    data object VerificationFailed : PaymentUIState()
    data object RecordingFailure : PaymentUIState()
    data object PaymentSuccess : PaymentUIState()
    data class PaymentFailed(val reason: String) : PaymentUIState()
    data class Error(val message: String) : PaymentUIState()
}
