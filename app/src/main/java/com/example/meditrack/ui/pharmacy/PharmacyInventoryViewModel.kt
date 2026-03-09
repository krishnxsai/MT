package com.example.meditrack.ui.pharmacy

import androidx.lifecycle.*
import com.example.meditrack.data.model.InventoryItem
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.repository.PharmacyInventoryRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel for pharmacy inventory management.
 */
class PharmacyInventoryViewModel : ViewModel() {

    private val repository = PharmacyInventoryRepository()

    private var _pharmacyId = ""

    private val _inventory = MutableLiveData<Resource<List<InventoryItem>>>()
    val inventory: LiveData<Resource<List<InventoryItem>>> = _inventory

    private val _lowStockAlerts = MutableLiveData<Resource<List<InventoryItem>>>()
    val lowStockAlerts: LiveData<Resource<List<InventoryItem>>> = _lowStockAlerts

    private val _searchResults = MutableLiveData<Resource<List<InventoryItem>>>()
    val searchResults: LiveData<Resource<List<InventoryItem>>> = _searchResults

    private val _operationResult = MutableLiveData<Resource<String>>()
    val operationResult: LiveData<Resource<String>> = _operationResult

    /**
     * Initialize with a pharmacy ID and start loading inventory.
     */
    fun init(pharmacyId: String) {
        if (_pharmacyId == pharmacyId) return
        _pharmacyId = pharmacyId
        loadInventory()
        loadLowStockAlerts()
    }

    private fun loadInventory() {
        viewModelScope.launch {
            repository.getInventoryFlow(_pharmacyId).collectLatest { result ->
                _inventory.value = result
            }
        }
    }

    private fun loadLowStockAlerts() {
        viewModelScope.launch {
            repository.getLowStockItemsFlow(_pharmacyId).collectLatest { result ->
                _lowStockAlerts.value = result
            }
        }
    }

    fun addItem(item: InventoryItem) {
        viewModelScope.launch {
            _operationResult.value = Resource.Loading
            when (val result = repository.addItem(item.copy(pharmacyId = _pharmacyId))) {
                is Resource.Success -> _operationResult.value = Resource.Success("Item added successfully")
                is Resource.Error -> _operationResult.value = Resource.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }

    fun updateItem(item: InventoryItem) {
        viewModelScope.launch {
            _operationResult.value = Resource.Loading
            when (val result = repository.updateItem(item)) {
                is Resource.Success -> _operationResult.value = Resource.Success("Item updated successfully")
                is Resource.Error -> _operationResult.value = Resource.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }

    fun updateStock(itemId: String, newQuantity: Int) {
        viewModelScope.launch {
            when (val result = repository.updateStockQuantity(itemId, newQuantity)) {
                is Resource.Success -> _operationResult.value = Resource.Success("Stock updated")
                is Resource.Error -> _operationResult.value = Resource.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            when (val result = repository.deleteItem(itemId)) {
                is Resource.Success -> _operationResult.value = Resource.Success("Item removed")
                is Resource.Error -> _operationResult.value = Resource.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = _inventory.value
            return
        }
        viewModelScope.launch {
            _searchResults.value = Resource.Loading
            _searchResults.value = repository.searchItems(_pharmacyId, query)
        }
    }
}

