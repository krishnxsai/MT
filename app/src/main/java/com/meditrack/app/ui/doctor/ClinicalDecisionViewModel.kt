package com.meditrack.app.ui.doctor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.model.*
import com.meditrack.app.data.repository.ClinicalDecisionRepository
import com.meditrack.app.data.repository.DoctorRepository
import kotlinx.coroutines.flow.collectLatest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClinicalDecisionViewModel @Inject constructor(
    private val clinicalRepository: ClinicalDecisionRepository,
    private val doctorRepository: DoctorRepository
) : ViewModel() {

    // Save result
    private val _saveResult = MutableLiveData<Resource<ClinicalDecision>>()
    val saveResult: LiveData<Resource<ClinicalDecision>> = _saveResult

    // Clinical decisions list
    private val _clinicalDecisions = MutableLiveData<Resource<List<ClinicalDecision>>>()
    val clinicalDecisions: LiveData<Resource<List<ClinicalDecision>>> = _clinicalDecisions

    // Patient health logs for analysis
    private val _patientLogs = MutableLiveData<Resource<List<HealthLog>>>()
    val patientLogs: LiveData<Resource<List<HealthLog>>> = _patientLogs

    // Vital analysis result
    private val _vitalAnalysis = MutableLiveData<VitalAnalysis?>()
    val vitalAnalysis: LiveData<VitalAnalysis?> = _vitalAnalysis

    // Delete result
    private val _deleteResult = MutableLiveData<Resource<Unit>?>()
    val deleteResult: LiveData<Resource<Unit>?> = _deleteResult

    // ==================== Clinical Decision Operations ====================

    fun createClinicalDecision(decision: ClinicalDecision) {
        viewModelScope.launch {
            _saveResult.postValue(Resource.Loading)
            val result = clinicalRepository.createClinicalDecision(decision)
            _saveResult.postValue(result)
        }
    }

    fun updateClinicalDecision(decision: ClinicalDecision) {
        viewModelScope.launch {
            _saveResult.postValue(Resource.Loading)
            val result = clinicalRepository.updateClinicalDecision(decision)
            _saveResult.postValue(result)
        }
    }

    fun deleteClinicalDecision(decisionId: String) {
        viewModelScope.launch {
            _deleteResult.postValue(Resource.Loading)
            val result = clinicalRepository.deleteClinicalDecision(decisionId)
            _deleteResult.postValue(result)
        }
    }

    fun clearDeleteResult() {
        _deleteResult.value = null
    }

    // ==================== Load Clinical Decisions ====================

    fun loadPatientClinicalDecisions(patientId: String) {
        viewModelScope.launch {
            clinicalRepository.getPatientClinicalDecisionsFlow(patientId).collectLatest { result ->
                _clinicalDecisions.postValue(result)
            }
        }
    }

    fun loadPatientDecisionsByType(patientId: String, type: ClinicalDecisionType) {
        viewModelScope.launch {
            clinicalRepository.getPatientDecisionsByType(patientId, type).collectLatest { result ->
                _clinicalDecisions.postValue(result)
            }
        }
    }

    // ==================== Patient Data for Analysis ====================

    fun loadPatientDataForAnalysis(patientId: String) {
        viewModelScope.launch {
            doctorRepository.getPatientHealthLogsFlow(patientId).collectLatest { result ->
                _patientLogs.postValue(result)
            }
        }
    }

    fun analyzePatientVitals(patientId: String) {
        viewModelScope.launch {
            val logsResult = _patientLogs.value
            if (logsResult is Resource.Success) {
                val analysis = clinicalRepository.analyzePatientVitals(patientId, logsResult.data)
                _vitalAnalysis.postValue(analysis)
            } else {
                // Load logs first then analyze
                val logs = doctorRepository.getPatientHealthLogs(patientId)
                if (logs is Resource.Success) {
                    val analysis = clinicalRepository.analyzePatientVitals(patientId, logs.data)
                    _vitalAnalysis.postValue(analysis)
                }
            }
        }
    }
}

