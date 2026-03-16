package com.meditrack.app.ui.healthlog

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.analytics.VitalAlertEngine
import com.meditrack.app.data.model.HealthLog
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.HealthLogRepository
import kotlinx.coroutines.flow.collectLatest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HealthLogViewModel @Inject constructor(
    private val repository: HealthLogRepository
) : ViewModel() {

    private val _healthLogs = MutableLiveData<Resource<List<HealthLog>>>()
    val healthLogs: LiveData<Resource<List<HealthLog>>> = _healthLogs

    private val _saveResult = MutableLiveData<Resource<HealthLog>?>()
    val saveResult: LiveData<Resource<HealthLog>?> = _saveResult

    private val _deleteResult = MutableLiveData<Resource<Unit>?>()
    val deleteResult: LiveData<Resource<Unit>?> = _deleteResult

    private val _currentLog = MutableLiveData<Resource<HealthLog>>()
    val currentLog: LiveData<Resource<HealthLog>> = _currentLog

    private val _alertSummary = MutableLiveData<VitalAlertEngine.AlertSummary>()
    val alertSummary: LiveData<VitalAlertEngine.AlertSummary> = _alertSummary

    init {
        loadHealthLogs()
    }

    fun loadHealthLogs() {
        viewModelScope.launch {
            repository.getHealthLogsFlow().collectLatest { result ->
                _healthLogs.postValue(result)
                if (result is Resource.Success && result.data.isNotEmpty()) {
                    _alertSummary.postValue(
                        VitalAlertEngine.evaluateLatestReadings(result.data)
                    )
                }
            }
        }
    }

    fun loadHealthLogsOnce() {
        viewModelScope.launch {
            _healthLogs.postValue(Resource.Loading)
            val result = repository.getHealthLogs()
            _healthLogs.postValue(result)
        }
    }

    fun addHealthLog(healthLog: HealthLog) {
        viewModelScope.launch {
            _saveResult.postValue(Resource.Loading)
            val result = repository.addHealthLog(healthLog)
            _saveResult.postValue(result)
        }
    }

    fun updateHealthLog(healthLog: HealthLog) {
        viewModelScope.launch {
            _saveResult.postValue(Resource.Loading)
            val result = repository.updateHealthLog(healthLog)
            _saveResult.postValue(result)
        }
    }

    fun deleteHealthLog(logId: String) {
        viewModelScope.launch {
            _deleteResult.postValue(Resource.Loading)
            val result = repository.deleteHealthLog(logId)
            _deleteResult.postValue(result)
        }
    }

    fun getHealthLog(logId: String) {
        viewModelScope.launch {
            _currentLog.postValue(Resource.Loading)
            val result = repository.getHealthLog(logId)
            _currentLog.postValue(result)
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    fun clearDeleteResult() {
        _deleteResult.value = null
    }
}

