package com.meditrack.app.ui.insights

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.analytics.HealthAnalytics
import com.meditrack.app.data.analytics.InsightEngine
import com.meditrack.app.data.analytics.RiskScoreEngine
import com.meditrack.app.data.analytics.UnifiedAlertPrioritizer
import com.meditrack.app.data.model.HealthLog
import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.HealthLogRepository
import com.meditrack.app.data.repository.MedicineIntakeRepository
import com.meditrack.app.data.repository.MedicineRepository
import kotlinx.coroutines.flow.collectLatest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HealthInsightsViewModel @Inject constructor(
    private val healthLogRepository: HealthLogRepository,
    private val medicineRepository: MedicineRepository,
    private val intakeRepository: MedicineIntakeRepository
) : ViewModel() {

    // ── Health Insights (vital stats) ───────────────────────────
    private val _healthInsights = MutableLiveData<HealthAnalytics.HealthInsights>()
    val healthInsights: LiveData<HealthAnalytics.HealthInsights> = _healthInsights

    // ── Risk Score ──────────────────────────────────────────────
    private val _riskScore = MutableLiveData<RiskScoreEngine.RiskScoreResult>()
    val riskScore: LiveData<RiskScoreEngine.RiskScoreResult> = _riskScore

    // ── Unified Alerts ──────────────────────────────────────────
    private val _unifiedAlerts = MutableLiveData<List<UnifiedAlertPrioritizer.UnifiedAlert>>()
    val unifiedAlerts: LiveData<List<UnifiedAlertPrioritizer.UnifiedAlert>> = _unifiedAlerts

    // ── Adherence Stats ─────────────────────────────────────────
    private val _adherenceStats = MutableLiveData<HealthAnalytics.AdherenceStats>()
    val adherenceStats: LiveData<HealthAnalytics.AdherenceStats> = _adherenceStats

    // ── Filtered logs for charts ────────────────────────────────
    private val _filteredLogs = MutableLiveData<List<HealthLog>>()
    val filteredLogs: LiveData<List<HealthLog>> = _filteredLogs

    // ── Loading / Error ─────────────────────────────────────────
    private val _isLoading = MutableLiveData(true)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // ── Internal state ──────────────────────────────────────────
    private var allLogs: List<HealthLog> = emptyList()
    private var allMedicines: List<Medicine> = emptyList()
    private var adherencePercentage: Float = -1f

    private val _selectedDays = MutableLiveData(7)

    init {
        loadAllData()
    }

    fun setDateRange(days: Int) {
        _selectedDays.value = days
        recalculate()
    }

    private fun loadAllData() {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            launch {
                healthLogRepository.getHealthLogsFlow().collectLatest { result ->
                    when (result) {
                        is Resource.Success -> {
                            allLogs = result.data
                            recalculate()
                        }
                        is Resource.Error -> {
                            _error.postValue(result.message)
                            _isLoading.postValue(false)
                        }
                        is Resource.Loading -> {}
                    }
                }
            }

            launch {
                medicineRepository.getMedicinesFlow().collectLatest { result ->
                    when (result) {
                        is Resource.Success -> {
                            allMedicines = result.data
                            loadAdherence()
                        }
                        is Resource.Error -> {
                            _error.postValue(result.message)
                        }
                        is Resource.Loading -> {}
                    }
                }
            }
        }
    }

    private fun loadAdherence() {
        viewModelScope.launch {
            try {
                val result = intakeRepository.getTodayAdherenceStats(allMedicines)
                val stats = (result as? Resource.Success)?.data ?: return@launch
                _adherenceStats.postValue(stats)
                adherencePercentage = stats.adherencePercentage
                recalculate()
            } catch (_: Exception) {}
        }
    }

    private fun recalculate() {
        val days = _selectedDays.value ?: 7

        viewModelScope.launch {
            try {
                val filteredLogs = HealthAnalytics.getLogsForLastDays(allLogs, days)
                _filteredLogs.postValue(filteredLogs)

                // Basic vital stats
                val insights = HealthAnalytics.calculateAllInsights(filteredLogs)
                _healthInsights.postValue(insights)

                // Full insight engine for risk + alerts
                val effectiveAdherence = if (adherencePercentage >= 0f) {
                    adherencePercentage
                } else {
                    val stats = HealthAnalytics.calculateTodayAdherence(allMedicines)
                    stats.adherencePercentage
                }

                val fullInsight = InsightEngine.computeFullInsight(
                    logs = allLogs,
                    medicines = allMedicines,
                    adherencePercentage = effectiveAdherence
                )

                _riskScore.postValue(fullInsight.riskScore)
                _unifiedAlerts.postValue(fullInsight.unifiedAlerts)

                _isLoading.postValue(false)
            } catch (e: Exception) {
                _error.postValue(e.message)
                _isLoading.postValue(false)
            }
        }
    }
}
