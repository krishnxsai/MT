package com.example.meditrack.ui.pharmacy

import androidx.lifecycle.*
import com.example.meditrack.data.model.OrderTransaction
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.repository.PharmacyRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel for pharmacy transaction history.
 */
class TransactionHistoryViewModel : ViewModel() {

    private val repository = PharmacyRepository()

    private var _pharmacyId = ""

    private val _transactions = MutableLiveData<Resource<List<OrderTransaction>>>()
    val transactions: LiveData<Resource<List<OrderTransaction>>> = _transactions

    private val _revenueSummary = MutableLiveData<Map<String, Double>>()
    val revenueSummary: LiveData<Map<String, Double>> = _revenueSummary

    /**
     * Initialize with a pharmacy ID.
     */
    fun init(pharmacyId: String) {
        if (_pharmacyId == pharmacyId) return
        _pharmacyId = pharmacyId
        loadTransactions()
        loadRevenueSummary()
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            repository.getPharmacyTransactionsFlow(_pharmacyId).collectLatest { result ->
                _transactions.value = result
            }
        }
    }

    private fun loadRevenueSummary() {
        viewModelScope.launch {
            when (val result = repository.getRevenueStats(_pharmacyId)) {
                is Resource.Success -> _revenueSummary.value = result.data
                is Resource.Error -> { /* Revenue summary will just be empty */ }
                is Resource.Loading -> {}
            }
        }
    }

    fun refresh() {
        loadRevenueSummary()
    }
}

