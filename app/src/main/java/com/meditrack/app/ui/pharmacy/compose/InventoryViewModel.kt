package com.meditrack.app.ui.pharmacy.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.model.InventoryItem
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.PharmacyInventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val inventoryRepository: PharmacyInventoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private var inventoryJob: Job? = null

    fun bindPharmacy(pharmacyId: String) {
        if (pharmacyId.isBlank()) {
            inventoryJob?.cancel()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    pharmacyId = "",
                    items = emptyList(),
                    filteredItems = emptyList(),
                    errorMessage = null
                )
            }
            return
        }

        if (_uiState.value.pharmacyId == pharmacyId && inventoryJob != null) return

        _uiState.update {
            it.copy(
                pharmacyId = pharmacyId,
                isLoading = true,
                errorMessage = null
            )
        }

        startInventoryListener(pharmacyId)
    }

    fun refresh() {
        val pharmacyId = _uiState.value.pharmacyId
        if (pharmacyId.isBlank()) return
        startInventoryListener(pharmacyId)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFiltersAndSort()
    }

    fun onSortOptionSelected(option: InventorySortOption) {
        _uiState.update { it.copy(sortOption = option) }
        applyFiltersAndSort()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun addMedicine(draft: InventoryMedicineDraft) {
        val pharmacyId = _uiState.value.pharmacyId
        if (pharmacyId.isBlank()) return

        val item = InventoryItem(
            pharmacyId = pharmacyId,
            medicineName = draft.medicineName.trim(),
            medicineNameNormalized = draft.medicineName.trim().lowercase(Locale.ROOT),
            dosage = draft.dosage.trim(),
            genericName = draft.genericName.trim(),
            manufacturer = draft.manufacturer.trim(),
            stockQuantity = draft.stockQuantity.coerceAtLeast(0),
            unitPrice = draft.unitPrice.coerceAtLeast(0.0),
            lowStockThreshold = draft.lowStockThreshold.coerceAtLeast(0),
            unit = draft.unit.trim().ifBlank { "tablets" }
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = inventoryRepository.addItem(item)) {
                is Resource.Success -> emitMessage("Medicine added")
                is Resource.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                is Resource.Loading -> Unit
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    fun updateMedicine(draft: InventoryMedicineDraft) {
        if (draft.id.isBlank()) return

        val pharmacyId = _uiState.value.pharmacyId
        if (pharmacyId.isBlank()) return

        val item = InventoryItem(
            id = draft.id,
            pharmacyId = pharmacyId,
            medicineName = draft.medicineName.trim(),
            medicineNameNormalized = draft.medicineName.trim().lowercase(Locale.ROOT),
            dosage = draft.dosage.trim(),
            genericName = draft.genericName.trim(),
            manufacturer = draft.manufacturer.trim(),
            stockQuantity = draft.stockQuantity.coerceAtLeast(0),
            unitPrice = draft.unitPrice.coerceAtLeast(0.0),
            lowStockThreshold = draft.lowStockThreshold.coerceAtLeast(0),
            unit = draft.unit.trim().ifBlank { "tablets" }
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = inventoryRepository.updateItem(item)) {
                is Resource.Success -> emitMessage("Medicine updated")
                is Resource.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                is Resource.Loading -> Unit
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    fun deleteMedicine(itemId: String) {
        if (itemId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = inventoryRepository.deleteItem(itemId)) {
                is Resource.Success -> emitMessage("Medicine deleted")
                is Resource.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                is Resource.Loading -> Unit
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    fun adjustStock(itemId: String, delta: Int) {
        if (itemId.isBlank() || delta == 0) return

        val current = _uiState.value.items.firstOrNull { it.id == itemId } ?: return
        val nextQuantity = (current.stockQuantity + delta).coerceAtLeast(0)

        viewModelScope.launch {
            when (val result = inventoryRepository.updateStockQuantity(itemId, nextQuantity)) {
                is Resource.Success -> Unit
                is Resource.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun startInventoryListener(pharmacyId: String) {
        inventoryJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        inventoryJob = viewModelScope.launch {
            inventoryRepository.getInventoryFlow(pharmacyId).collectLatest { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    is Resource.Success -> {
                        val mapped = result.data.map { item ->
                            InventoryMedicine(
                                id = item.id,
                                medicineName = item.medicineName,
                                dosage = item.dosage,
                                genericName = item.genericName,
                                manufacturer = item.manufacturer,
                                unitPrice = item.unitPrice,
                                stockQuantity = item.stockQuantity,
                                lowStockThreshold = item.lowStockThreshold,
                                unit = item.unit
                            )
                        }

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                items = mapped,
                                errorMessage = null
                            )
                        }
                        applyFiltersAndSort(mapped)
                    }
                    is Resource.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                items = emptyList(),
                                filteredItems = emptyList(),
                                errorMessage = result.message
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyFiltersAndSort(source: List<InventoryMedicine> = _uiState.value.items) {
        val state = _uiState.value
        val query = state.searchQuery.trim().lowercase(Locale.ROOT)

        val filtered = if (query.isBlank()) {
            source
        } else {
            source.filter { medicine ->
                medicine.medicineName.lowercase(Locale.ROOT).contains(query) ||
                    medicine.dosage.lowercase(Locale.ROOT).contains(query) ||
                    medicine.genericName.lowercase(Locale.ROOT).contains(query)
            }
        }

        val sorted = when (state.sortOption) {
            InventorySortOption.LOW_STOCK_FIRST -> filtered.sortedWith(
                compareBy<InventoryMedicine> { it.stockStatus }
                    .thenBy { it.stockQuantity }
                    .thenBy { it.medicineName.lowercase(Locale.ROOT) }
            )
            InventorySortOption.NAME -> filtered.sortedBy { it.medicineName.lowercase(Locale.ROOT) }
            InventorySortOption.STOCK_ASC -> filtered.sortedBy { it.stockQuantity }
            InventorySortOption.STOCK_DESC -> filtered.sortedByDescending { it.stockQuantity }
            InventorySortOption.PRICE_ASC -> filtered.sortedBy { it.unitPrice }
            InventorySortOption.PRICE_DESC -> filtered.sortedByDescending { it.unitPrice }
        }

        _uiState.update { it.copy(filteredItems = sorted) }
    }

    private suspend fun emitMessage(message: String) {
        _events.emit(message)
    }

    override fun onCleared() {
        super.onCleared()
        inventoryJob?.cancel()
    }
}
