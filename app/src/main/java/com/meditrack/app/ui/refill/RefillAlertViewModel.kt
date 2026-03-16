package com.meditrack.app.ui.refill

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.analytics.RefillDetectionEngine
import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.RefillAlert
import com.meditrack.app.data.model.RefillUrgency
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.MedicineRepository
import com.meditrack.app.data.repository.RefillAlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing refill alerts and low-stock medicine detection.
 * Combines medicine data with alert management for a unified low-stock experience.
 */
@HiltViewModel
class RefillAlertViewModel @Inject constructor(
    private val medicineRepository: MedicineRepository,
    private val alertRepository: RefillAlertRepository
) : ViewModel() {

    // ==================== UI STATE ====================

    /**
     * Represents a medicine with its calculated refill information.
     */
    data class MedicineWithAlert(
        val medicine: Medicine,
        val urgency: RefillUrgency,
        val daysRemaining: Int,
        val stockPercentage: Int,
        val statusText: String,
        val alert: RefillAlert?,
        val isInCart: Boolean = false
    )

    /**
     * Navigation events for the UI to observe.
     */
    sealed class NavigationEvent {
        data class ToOrderScreen(val medicine: Medicine) : NavigationEvent()
        data class ToUnifiedOrder(val preSelectedMedicines: List<Medicine>) : NavigationEvent()
    }

    // ==================== STATE FLOWS ====================

    private val _lowStockMedicines = MutableStateFlow<List<MedicineWithAlert>>(emptyList())
    val lowStockMedicines: StateFlow<List<MedicineWithAlert>> = _lowStockMedicines.asStateFlow()

    private val _alertCount = MutableStateFlow(0)
    val alertCount: StateFlow<Int> = _alertCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    // For LiveData-based observation (compatibility with existing patterns)
    private val _lowStockMedicinesLiveData = MutableLiveData<Resource<List<MedicineWithAlert>>>()
    val lowStockMedicinesLiveData: LiveData<Resource<List<MedicineWithAlert>>> = _lowStockMedicinesLiveData

    // ==================== INITIALIZATION ====================

    init {
        loadLowStockMedicines()
    }

    /**
     * Load low-stock medicines and generate/update alerts.
     */
    fun loadLowStockMedicines() {
        viewModelScope.launch {
            _isLoading.value = true
            _lowStockMedicinesLiveData.postValue(Resource.Loading)

            medicineRepository.getMedicinesFlow().collectLatest { result ->
                when (result) {
                    is Resource.Success -> {
                        val medicines = result.data
                        val lowStockItems = processLowStockMedicines(medicines)

                        _lowStockMedicines.value = lowStockItems
                        _alertCount.value = lowStockItems.count { it.alert?.isDismissed != true }
                        _lowStockMedicinesLiveData.postValue(Resource.Success(lowStockItems))

                        // Generate alerts for new low-stock items
                        generateMissingAlerts(lowStockItems)
                    }
                    is Resource.Error -> {
                        _error.postValue(result.message)
                        _lowStockMedicinesLiveData.postValue(Resource.Error(result.message ?: "Unknown error"))
                    }
                    is Resource.Loading -> {
                        // Already handled
                    }
                }
                _isLoading.value = false
            }
        }
    }

    /**
     * Load low-stock medicines once (non-realtime).
     */
    fun loadLowStockMedicinesOnce() {
        viewModelScope.launch {
            _isLoading.value = true
            _lowStockMedicinesLiveData.postValue(Resource.Loading)

            when (val result = medicineRepository.getMedicines()) {
                is Resource.Success -> {
                    val lowStockItems = processLowStockMedicines(result.data)
                    _lowStockMedicines.value = lowStockItems
                    _alertCount.value = lowStockItems.count { it.alert?.isDismissed != true }
                    _lowStockMedicinesLiveData.postValue(Resource.Success(lowStockItems))
                }
                is Resource.Error -> {
                    _error.postValue(result.message)
                    _lowStockMedicinesLiveData.postValue(Resource.Error(result.message ?: "Unknown error"))
                }
                is Resource.Loading -> {
                    // Already handled
                }
            }
            _isLoading.value = false
        }
    }

    /**
     * Process medicines and extract low-stock items with their alert info.
     */
    private suspend fun processLowStockMedicines(medicines: List<Medicine>): List<MedicineWithAlert> {
        return medicines
            .filter { it.isActive && it.isRefillTrackingEnabled }
            .mapNotNull { medicine ->
                val refillInfo = RefillDetectionEngine.analyzeMedicine(medicine)
                if (refillInfo.urgency == RefillUrgency.NONE) {
                    null
                } else {
                    val alert = alertRepository.getActiveAlert(medicine.id)
                    MedicineWithAlert(
                        medicine = medicine,
                        urgency = refillInfo.urgency,
                        daysRemaining = refillInfo.daysRemaining,
                        stockPercentage = refillInfo.stockPercentage,
                        statusText = refillInfo.statusText,
                        alert = alert
                    )
                }
            }
            .sortedByDescending { it.urgency.ordinal }
    }

    /**
     * Generate alerts for medicines that don't have active alerts yet.
     */
    private suspend fun generateMissingAlerts(items: List<MedicineWithAlert>) {
        items.filter { it.alert == null }
            .forEach { item ->
                alertRepository.createOrUpdateAlert(
                    medicineId = item.medicine.id,
                    medicineName = item.medicine.name,
                    medicineDosage = item.medicine.dosage,
                    currentQuantity = item.medicine.currentQuantity,
                    estimatedDaysLeft = item.daysRemaining,
                    urgencyLevel = item.urgency
                )
            }
    }

    // ==================== USER ACTIONS ====================

    /**
     * Dismiss an alert for a medicine.
     */
    fun dismissAlert(medicineId: String) {
        viewModelScope.launch {
            when (val result = alertRepository.dismissAlert(medicineId)) {
                is Resource.Success -> {
                    // Update local state
                    _lowStockMedicines.value = _lowStockMedicines.value.map { item ->
                        if (item.medicine.id == medicineId) {
                            item.copy(alert = item.alert?.copy(isDismissed = true))
                        } else {
                            item
                        }
                    }
                    _alertCount.value = _lowStockMedicines.value.count { it.alert?.isDismissed != true }
                }
                is Resource.Error -> {
                    _error.postValue(result.message)
                }
                is Resource.Loading -> {
                    // Ignore
                }
            }
        }
    }

    /**
     * Handle order button click for a medicine.
     */
    fun onOrderClicked(medicine: Medicine) {
        viewModelScope.launch {
            _navigationEvent.emit(NavigationEvent.ToOrderScreen(medicine))
        }
    }

    /**
     * Handle order all button click.
     */
    fun onOrderAllClicked() {
        viewModelScope.launch {
            val medicines = _lowStockMedicines.value.map { it.medicine }
            _navigationEvent.emit(NavigationEvent.ToUnifiedOrder(medicines))
        }
    }

    /**
     * Mark an alert as actioned after order is placed.
     */
    fun markOrderPlaced(medicineId: String, orderId: String) {
        viewModelScope.launch {
            alertRepository.markOrderPlaced(medicineId, orderId)
            // Refresh the list
            loadLowStockMedicinesOnce()
        }
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _error.value = null
    }

    // ==================== HELPERS ====================

    /**
     * Get urgent medicines count (out of stock or will run out in 2 days).
     */
    fun getUrgentCount(): Int {
        return _lowStockMedicines.value.count {
            it.urgency == RefillUrgency.OUT_OF_STOCK || it.urgency == RefillUrgency.URGENT
        }
    }

    /**
     * Check if there are any low-stock medicines.
     */
    fun hasLowStockMedicines(): Boolean {
        return _lowStockMedicines.value.isNotEmpty()
    }

    /**
     * Get summary text for dashboard display.
     */
    fun getLowStockSummary(): String {
        val count = _lowStockMedicines.value.size
        val urgentCount = getUrgentCount()

        return when {
            count == 0 -> "All medicines stocked"
            urgentCount > 0 -> "$urgentCount medicine${if (urgentCount > 1) "s" else ""} need urgent refill"
            else -> "$count medicine${if (count > 1) "s" else ""} running low"
        }
    }
}
