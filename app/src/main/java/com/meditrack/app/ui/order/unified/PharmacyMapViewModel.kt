package com.meditrack.app.ui.order.unified

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.model.Pharmacy
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.PharmacyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.*

/**
 * ViewModel for the pharmacy map screen.
 * Manages pharmacy data, filtering, sorting, and selection.
 */
@HiltViewModel
class PharmacyMapViewModel @Inject constructor(
    private val pharmacyRepository: PharmacyRepository
) : ViewModel() {

    // ==================== TYPES ====================

    enum class SortType {
        DISTANCE,
        RATING,
        DELIVERY_TIME,
        OPEN_NOW
    }

    data class PharmacyWithDistance(
        val pharmacy: Pharmacy,
        val distanceKm: Double,
        val distanceText: String
    )

    // ==================== STATE ====================

    private val _pharmacies = MutableStateFlow<List<PharmacyWithDistance>>(emptyList())
    val pharmacies: StateFlow<List<PharmacyWithDistance>> = _pharmacies.asStateFlow()

    private val _selectedPharmacy = MutableStateFlow<Pharmacy?>(null)
    val selectedPharmacy: StateFlow<Pharmacy?> = _selectedPharmacy.asStateFlow()

    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation: StateFlow<Location?> = _userLocation.asStateFlow()

    private val _sortType = MutableStateFlow(SortType.DISTANCE)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Raw pharmacy data before sorting/filtering
    private var rawPharmacies: List<Pharmacy> = emptyList()

    // ==================== INITIALIZATION ====================

    init {
        loadPharmacies()

        // Re-sort when sort type or location changes
        viewModelScope.launch {
            combine(_sortType, _userLocation) { sort, location ->
                Pair(sort, location)
            }.collectLatest { (sortType, _) ->
                sortAndFilterPharmacies(sortType)
            }
        }
    }

    private fun loadPharmacies() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = pharmacyRepository.getPharmacies()) {
                is Resource.Success -> {
                    rawPharmacies = result.data
                    sortAndFilterPharmacies(_sortType.value)
                }
                is Resource.Error -> {
                    _error.postValue(result.message)
                }
                is Resource.Loading -> {
                    // Ignore
                }
            }
            _isLoading.value = false
        }
    }

    // ==================== SORTING & FILTERING ====================

    fun setSortType(type: SortType) {
        _sortType.value = type
    }

    private fun sortAndFilterPharmacies(sortType: SortType) {
        val userLoc = _userLocation.value

        // Calculate distances
        val withDistance = rawPharmacies.map { pharmacy ->
            val distance = if (userLoc != null) {
                calculateDistance(
                    userLoc.latitude, userLoc.longitude,
                    pharmacy.latitude, pharmacy.longitude
                )
            } else {
                Double.MAX_VALUE
            }

            PharmacyWithDistance(
                pharmacy = pharmacy,
                distanceKm = distance,
                distanceText = formatDistance(distance)
            )
        }

        // Sort based on type
        val sorted = when (sortType) {
            SortType.DISTANCE -> withDistance.sortedBy { it.distanceKm }
            SortType.RATING -> withDistance.sortedByDescending { it.pharmacy.rating }
            SortType.DELIVERY_TIME -> withDistance.sortedBy {
                it.pharmacy.estimatedDeliveryTime.filter { c -> c.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
            }
            SortType.OPEN_NOW -> withDistance
                .filter { it.pharmacy.isActive }
                .sortedBy { it.distanceKm }
        }

        _pharmacies.value = sorted
    }

    // ==================== LOCATION ====================

    fun updateUserLocation(location: Location) {
        _userLocation.value = location
    }

    // ==================== SELECTION ====================

    fun selectPharmacy(pharmacy: Pharmacy) {
        _selectedPharmacy.value = pharmacy
    }

    fun selectPharmacyById(pharmacyId: String) {
        val pharmacy = rawPharmacies.find { it.id == pharmacyId }
        _selectedPharmacy.value = pharmacy
    }

    fun clearSelection() {
        _selectedPharmacy.value = null
    }

    // ==================== HELPERS ====================

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun formatDistance(distanceKm: Double): String {
        return when {
            distanceKm == Double.MAX_VALUE -> "Unknown"
            distanceKm < 1.0 -> "${(distanceKm * 1000).toInt()} m"
            else -> String.format("%.1f km", distanceKm)
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun refresh() {
        loadPharmacies()
    }
}
