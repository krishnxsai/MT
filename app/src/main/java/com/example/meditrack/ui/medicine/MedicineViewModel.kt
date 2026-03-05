package com.example.meditrack.ui.medicine

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meditrack.data.model.Medicine
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.repository.MedicineRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MedicineViewModel : ViewModel() {

    private val repository = MedicineRepository()

    private val _medicines = MutableLiveData<Resource<List<Medicine>>>()
    val medicines: LiveData<Resource<List<Medicine>>> = _medicines

    private val _saveMedicineResult = MutableLiveData<Resource<Medicine>?>()
    val saveMedicineResult: LiveData<Resource<Medicine>?> = _saveMedicineResult

    private val _deleteMedicineResult = MutableLiveData<Resource<Unit>?>()
    val deleteMedicineResult: LiveData<Resource<Unit>?> = _deleteMedicineResult

    private val _toggleResult = MutableLiveData<Resource<Unit>>()
    val toggleResult: LiveData<Resource<Unit>> = _toggleResult

    private val _currentMedicine = MutableLiveData<Resource<Medicine>>()
    val currentMedicine: LiveData<Resource<Medicine>> = _currentMedicine

    init {
        loadMedicines()
    }

    fun loadMedicines() {
        viewModelScope.launch {
            repository.getMedicinesFlow().collectLatest { result ->
                _medicines.postValue(result)
            }
        }
    }

    fun loadMedicinesOnce() {
        viewModelScope.launch {
            _medicines.postValue(Resource.Loading)
            val result = repository.getMedicines()
            _medicines.postValue(result)
        }
    }

    fun addMedicine(medicine: Medicine) {
        viewModelScope.launch {
            _saveMedicineResult.postValue(Resource.Loading)
            val result = repository.addMedicine(medicine)
            _saveMedicineResult.postValue(result)
        }
    }

    fun updateMedicine(medicine: Medicine) {
        viewModelScope.launch {
            _saveMedicineResult.postValue(Resource.Loading)
            val result = repository.updateMedicine(medicine)
            _saveMedicineResult.postValue(result)
        }
    }

    fun deleteMedicine(medicineId: String) {
        viewModelScope.launch {
            _deleteMedicineResult.postValue(Resource.Loading)
            val result = repository.deleteMedicine(medicineId)
            _deleteMedicineResult.postValue(result)
        }
    }

    fun toggleMedicineActive(medicineId: String, isActive: Boolean) {
        // Optimistic update: Update local list immediately for responsive UI
        val currentList = (_medicines.value as? Resource.Success)?.data
        if (currentList != null) {
            val updatedList = currentList.map { medicine ->
                if (medicine.id == medicineId) {
                    medicine.copy(isActive = isActive)
                } else {
                    medicine
                }
            }
            _medicines.postValue(Resource.Success(updatedList))
        }

        // Then update Firebase
        viewModelScope.launch {
            val result = repository.toggleMedicineActive(medicineId, isActive)
            _toggleResult.postValue(result)
            // If failed, reload to restore correct state
            if (result is Resource.Error) {
                loadMedicinesOnce()
            }
        }
    }

    fun getMedicine(medicineId: String) {
        viewModelScope.launch {
            _currentMedicine.postValue(Resource.Loading)
            val result = repository.getMedicine(medicineId)
            _currentMedicine.postValue(result)
        }
    }

    fun updateAlarmIds(medicineId: String, alarmIds: List<Int>) {
        viewModelScope.launch {
            repository.updateAlarmIds(medicineId, alarmIds)
        }
    }

    fun clearSaveResult() {
        _saveMedicineResult.value = null
    }

    fun clearDeleteResult() {
        _deleteMedicineResult.value = null
    }
}

