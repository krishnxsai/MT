package com.example.meditrack.ui.recommendations

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meditrack.data.model.ClinicalDecision
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.repository.ClinicalDecisionRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel for patient-side Doctor Recommendations view.
 * Provides access to prescriptions, follow-ups, alerts, and recommendations
 * that the doctor has made public to the patient.
 */
class DoctorRecommendationsViewModel : ViewModel() {

    private val repository = ClinicalDecisionRepository()

    // All recommendations
    private val _recommendations = MutableLiveData<Resource<List<ClinicalDecision>>>()
    val recommendations: LiveData<Resource<List<ClinicalDecision>>> = _recommendations

    // Critical alerts
    private val _criticalAlerts = MutableLiveData<Resource<List<ClinicalDecision>>>()
    val criticalAlerts: LiveData<Resource<List<ClinicalDecision>>> = _criticalAlerts

    // Upcoming follow-ups
    private val _followUps = MutableLiveData<Resource<List<ClinicalDecision>>>()
    val followUps: LiveData<Resource<List<ClinicalDecision>>> = _followUps

    // Active prescriptions
    private val _prescriptions = MutableLiveData<Resource<List<ClinicalDecision>>>()
    val prescriptions: LiveData<Resource<List<ClinicalDecision>>> = _prescriptions

    // Acknowledge result
    private val _acknowledgeResult = MutableLiveData<Resource<Unit>?>()
    val acknowledgeResult: LiveData<Resource<Unit>?> = _acknowledgeResult

    init {
        loadAllRecommendations()
    }

    fun loadAllRecommendations() {
        viewModelScope.launch {
            repository.getMyDoctorRecommendationsFlow().collectLatest { result ->
                _recommendations.postValue(result)
            }
        }
    }

    fun loadCriticalAlerts() {
        viewModelScope.launch {
            repository.getMyCriticalAlertsFlow().collectLatest { result ->
                _criticalAlerts.postValue(result)
            }
        }
    }

    fun loadUpcomingFollowUps() {
        viewModelScope.launch {
            repository.getMyUpcomingFollowUpsFlow().collectLatest { result ->
                _followUps.postValue(result)
            }
        }
    }

    fun loadActivePrescriptions() {
        viewModelScope.launch {
            repository.getMyActivePrescriptionsFlow().collectLatest { result ->
                _prescriptions.postValue(result)
            }
        }
    }

    fun acknowledgeRecommendation(decisionId: String) {
        viewModelScope.launch {
            _acknowledgeResult.postValue(Resource.Loading)
            val result = repository.acknowledgeRecommendation(decisionId)
            _acknowledgeResult.postValue(result)
        }
    }

    fun clearAcknowledgeResult() {
        _acknowledgeResult.value = null
    }

    fun refreshAll() {
        loadAllRecommendations()
        loadCriticalAlerts()
        loadUpcomingFollowUps()
        loadActivePrescriptions()
    }
}

