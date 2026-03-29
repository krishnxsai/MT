package com.meditrack.app.ui.order.cancellation

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.model.CancellationRequest
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for order cancellation flow.
 * Handles requesting and tracking cancellation requests.
 */
@HiltViewModel
class OrderCancellationViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {

    companion object {
        private const val TAG = "OrderCancellationVM"
    }

    // UI State
    private val _cancellationRequests = MutableLiveData<List<CancellationRequest>>(emptyList())
    val cancellationRequests: LiveData<List<CancellationRequest>> = _cancellationRequests

    private val _selectedRequest = MutableLiveData<CancellationRequest?>(null)
    val selectedRequest: LiveData<CancellationRequest?> = _selectedRequest

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private val _successMessage = MutableLiveData<String?>(null)
    val successMessage: LiveData<String?> = _successMessage

    init {
        loadCancellationRequests()
    }

    /**
     * Load all cancellation requests for current user.
     */
    fun loadCancellationRequests() {
        viewModelScope.launch {
            orderRepository.getCancellationRequestsFlow().collect { result ->
                when (result) {
                    is Resource.Success -> {
                        Log.d(TAG, "Cancellation requests loaded: ${result.data.size}")
                        _cancellationRequests.value = result.data
                    }
                    is Resource.Error -> {
                        Log.e(TAG, "Failed to load cancellation requests: ${result.message}")
                        _errorMessage.value = result.message
                    }
                    is Resource.Loading -> {
                        _loading.value = true
                    }
                }
            }
        }
    }

    /**
     * Request order cancellation.
     */
    fun requestCancellation(orderId: String, reason: String) {
        viewModelScope.launch {
            _loading.value = true
            _errorMessage.value = null

            val result = orderRepository.requestCancellation(orderId, reason)
            when (result) {
                is Resource.Success -> {
                    Log.d(TAG, "Cancellation request created: ${result.data}")
                    _successMessage.value = "Cancellation request submitted successfully"
                    loadCancellationRequests()
                }
                is Resource.Error -> {
                    Log.e(TAG, "Failed to request cancellation: ${result.message}")
                    _errorMessage.value = result.message
                }
                is Resource.Loading -> {}
            }
            _loading.value = false
        }
    }

    /**
     * Get details of a specific cancellation request.
     */
    fun getCancellationRequest(requestId: String) {
        viewModelScope.launch {
            _loading.value = true
            val result = orderRepository.getCancellationRequest(requestId)
            when (result) {
                is Resource.Success -> {
                    Log.d(TAG, "Cancellation request loaded: ${result.data.id}")
                    _selectedRequest.value = result.data
                }
                is Resource.Error -> {
                    Log.e(TAG, "Failed to load cancellation request: ${result.message}")
                    _errorMessage.value = result.message
                }
                is Resource.Loading -> {}
            }
            _loading.value = false
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Clear success message.
     */
    fun clearSuccess() {
        _successMessage.value = null
    }
}
