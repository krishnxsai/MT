package com.meditrack.app.ui.pharmacy

import androidx.lifecycle.*
import com.meditrack.app.data.model.OrderTransaction
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.PharmacyRepository
import kotlinx.coroutines.flow.collectLatest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for pharmacy transaction history.
 */
@HiltViewModel
class TransactionHistoryViewModel @Inject constructor(
    private val repository: PharmacyRepository
) : ViewModel() {

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

