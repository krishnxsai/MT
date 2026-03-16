package com.meditrack.app.ui.order.unified

import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.model.InventoryItem
import com.meditrack.app.data.model.Pharmacy
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.PharmacyInventoryRepository
import com.meditrack.app.data.repository.PharmacyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.*

/**
 * ViewModel for the pharmacy map screen.
 * Manages pharmacy data, filtering, sorting, and selection.
 * Supports inventory-based filtering (Phase 6).
 */
@HiltViewModel
class PharmacyMapViewModel @Inject constructor(
    private val pharmacyRepository: PharmacyRepository,
    private val inventoryRepository: PharmacyInventoryRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PharmacyMapViewModel"
    }

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
        val distanceText: String,
        /** Available medicines from inventory (populated when filtering by medicines). */
        val availableItems: Map<String, InventoryItem> = emptyMap(),
        /** Whether pharmacy has ALL required medicines in stock. */
        val hasAllMedicines: Boolean = true
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

    /** Medicine names to filter pharmacies by (from patient's order). */
    private val _requiredMedicines = MutableStateFlow<List<String>>(emptyList())
    val requiredMedicines: StateFlow<List<String>> = _requiredMedicines.asStateFlow()

    /** Required quantities per medicine (medicineName -> quantity). */
    private val _requiredQuantities = MutableStateFlow<Map<String, Int>>(emptyMap())

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

    // ==================== INVENTORY FILTERING (Phase 6) ====================

    /**
     * Set the medicines required for the order.
     * Only pharmacies that have ALL these medicines in stock will be shown.
     *
     * @param medicineNames List of medicine names required
     * @param quantities Map of medicine name to required quantity (optional)
     */
    fun setRequiredMedicines(medicineNames: List<String>, quantities: Map<String, Int> = emptyMap()) {
        _requiredMedicines.value = medicineNames
        _requiredQuantities.value = quantities
        // Reload with inventory filtering
        loadPharmaciesWithInventoryCheck()
    }

    /**
     * Load pharmacies and check inventory availability for required medicines.
     */
    private fun loadPharmaciesWithInventoryCheck() {
        val medicines = _requiredMedicines.value
        if (medicines.isEmpty()) {
            // No medicine filter - just load all pharmacies
            loadPharmacies()
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            Log.d(TAG, "Loading pharmacies with inventory check for: $medicines")

            when (val result = pharmacyRepository.getPharmacies()) {
                is Resource.Success -> {
                    rawPharmacies = result.data

                    // Check inventory for each pharmacy
                    val pharmaciesWithInventory = rawPharmacies.mapNotNull { pharmacy ->
                        val inventoryCheck = inventoryRepository.checkMedicineAvailability(
                            pharmacy.id,
                            medicines
                        )

                        when (inventoryCheck) {
                            is Resource.Success -> {
                                val available = inventoryCheck.data
                                val hasAll = available.size == medicines.size

                                // Only include pharmacies that have at least some medicines
                                if (available.isNotEmpty()) {
                                    PharmacyInventoryInfo(
                                        pharmacy = pharmacy,
                                        availableItems = available,
                                        hasAllMedicines = hasAll
                                    )
                                } else {
                                    null // Exclude pharmacies with no matching medicines
                                }
                            }
                            else -> {
                                // On error, include pharmacy but mark as unknown availability
                                PharmacyInventoryInfo(
                                    pharmacy = pharmacy,
                                    availableItems = emptyMap(),
                                    hasAllMedicines = false
                                )
                            }
                        }
                    }

                    // Sort: pharmacies with all medicines first, then partial matches
                    val sorted = pharmaciesWithInventory.sortedByDescending { it.hasAllMedicines }

                    // Store for later sorting
                    inventoryInfoMap.clear()
                    sorted.forEach { inventoryInfoMap[it.pharmacy.id] = it }

                    sortAndFilterPharmacies(_sortType.value)

                    Log.d(TAG, "Found ${sorted.count { it.hasAllMedicines }}/${rawPharmacies.size} pharmacies with all medicines")
                }
                is Resource.Error -> {
                    _error.postValue(result.message)
                }
                is Resource.Loading -> {}
            }
            _isLoading.value = false
        }
    }

    /** Temporary storage for inventory info during filtering. */
    private data class PharmacyInventoryInfo(
        val pharmacy: Pharmacy,
        val availableItems: Map<String, InventoryItem>,
        val hasAllMedicines: Boolean
    )

    private val inventoryInfoMap = mutableMapOf<String, PharmacyInventoryInfo>()

    // ==================== SORTING & FILTERING ====================

    fun setSortType(type: SortType) {
        _sortType.value = type
    }

    private fun sortAndFilterPharmacies(sortType: SortType) {
        val userLoc = _userLocation.value
        val medicines = _requiredMedicines.value

        // Determine which pharmacies to show
        val pharmaciesToShow = if (medicines.isNotEmpty() && inventoryInfoMap.isNotEmpty()) {
            // Filter: only show pharmacies in the inventory map (those with at least some stock)
            rawPharmacies.filter { inventoryInfoMap.containsKey(it.id) }
        } else {
            rawPharmacies
        }

        // Calculate distances
        val withDistance = pharmaciesToShow.map { pharmacy ->
            val distance = if (userLoc != null) {
                calculateDistance(
                    userLoc.latitude, userLoc.longitude,
                    pharmacy.latitude, pharmacy.longitude
                )
            } else {
                Double.MAX_VALUE
            }

            val inventoryInfo = inventoryInfoMap[pharmacy.id]

            PharmacyWithDistance(
                pharmacy = pharmacy,
                distanceKm = distance,
                distanceText = formatDistance(distance),
                availableItems = inventoryInfo?.availableItems ?: emptyMap(),
                hasAllMedicines = inventoryInfo?.hasAllMedicines ?: true
            )
        }

        // Sort: Pharmacies with all medicines first, then by selected sort type
        val sorted = when (sortType) {
            SortType.DISTANCE -> withDistance
                .sortedWith(compareByDescending<PharmacyWithDistance> { it.hasAllMedicines }
                    .thenBy { it.distanceKm })
            SortType.RATING -> withDistance
                .sortedWith(compareByDescending<PharmacyWithDistance> { it.hasAllMedicines }
                    .thenByDescending { it.pharmacy.rating })
            SortType.DELIVERY_TIME -> withDistance
                .sortedWith(compareByDescending<PharmacyWithDistance> { it.hasAllMedicines }
                    .thenBy { it.pharmacy.estimatedDeliveryTime.filter { c -> c.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE })
            SortType.OPEN_NOW -> withDistance
                .filter { it.pharmacy.isActive }
                .sortedWith(compareByDescending<PharmacyWithDistance> { it.hasAllMedicines }
                    .thenBy { it.distanceKm })
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

    /**
     * Get available items for the selected pharmacy.
     * Returns the inventory items that match the required medicines.
     */
    fun getSelectedPharmacyItems(): Map<String, InventoryItem> {
        val selected = _selectedPharmacy.value ?: return emptyMap()
        return inventoryInfoMap[selected.id]?.availableItems ?: emptyMap()
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
        val medicines = _requiredMedicines.value
        if (medicines.isNotEmpty()) {
            loadPharmaciesWithInventoryCheck()
        } else {
            loadPharmacies()
        }
    }
}
