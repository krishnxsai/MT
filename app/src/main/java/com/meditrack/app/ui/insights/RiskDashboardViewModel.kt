package com.meditrack.app.ui.insights

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.analytics.HealthInsightEngine
import com.meditrack.app.data.analytics.InsightEngine
import com.meditrack.app.data.analytics.ReminderOptimizer
import com.meditrack.app.data.analytics.RiskScoreEngine
import com.meditrack.app.data.analytics.TrendPredictionEngine
import com.meditrack.app.data.analytics.HealthAnalytics
import com.meditrack.app.data.model.HealthLog
import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.RiskScore
import com.meditrack.app.data.repository.HealthLogRepository
import com.meditrack.app.data.repository.MedicineIntakeRepository
import com.meditrack.app.data.repository.MedicineRepository
import com.meditrack.app.data.repository.RiskScoreRepository
import com.meditrack.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.collectLatest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RiskDashboardViewModel @Inject constructor(
    private val healthLogRepository: HealthLogRepository,
    private val medicineRepository: MedicineRepository,
    private val intakeRepository: MedicineIntakeRepository,
    private val riskScoreRepository: RiskScoreRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    // ── Risk Score ──────────────────────────────────────────────
    private val _riskScore = MutableLiveData<RiskScoreEngine.RiskScoreResult>()
    val riskScore: LiveData<RiskScoreEngine.RiskScoreResult> = _riskScore

    // ── Trend Analysis ──────────────────────────────────────────
    private val _trendAnalysis = MutableLiveData<TrendPredictionEngine.TrendAnalysisResult>()
    val trendAnalysis: LiveData<TrendPredictionEngine.TrendAnalysisResult> = _trendAnalysis

    // ── AI Suggestions ──────────────────────────────────────────
    private val _suggestions = MutableLiveData<List<HealthInsightEngine.HealthSuggestion>>()
    val suggestions: LiveData<List<HealthInsightEngine.HealthSuggestion>> = _suggestions

    // ── Reminder Optimization ───────────────────────────────────
    private val _reminderSuggestions = MutableLiveData<ReminderOptimizer.OptimizationResult?>()
    val reminderSuggestions: LiveData<ReminderOptimizer.OptimizationResult?> = _reminderSuggestions

    // ── Loading state ───────────────────────────────────────────
    private val _isLoading = MutableLiveData(true)
    val isLoading: LiveData<Boolean> = _isLoading

    // ── Error state ─────────────────────────────────────────────
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // ── Internal data ───────────────────────────────────────────
    private var healthLogs: List<HealthLog> = emptyList()
    private var medicines: List<Medicine> = emptyList()
    private var adherencePercentage: Float = -1f

    // ── Persistence debounce ────────────────────────────────────
    private var lastPersistedCategory: RiskScoreEngine.RiskCategory? = null
    private var lastPersistedTime: Long = 0L
    private val persistDebounceMs = 6 * 60 * 60 * 1000L // 6 hours

    init {
        loadAllData()
    }

    fun loadAllData() {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            // Load health logs
            launch {
                healthLogRepository.getHealthLogsFlow().collectLatest { result ->
                    when (result) {
                        is Resource.Success -> {
                            healthLogs = result.data
                            recalculateAll()
                        }
                        is Resource.Error -> {
                            _error.postValue(result.message)
                        }
                        is Resource.Loading -> {}
                    }
                }
            }

            // Load medicines
            launch {
                medicineRepository.getMedicinesFlow().collectLatest { result ->
                    when (result) {
                        is Resource.Success -> {
                            medicines = result.data
                            recalculateAll()
                        }
                        is Resource.Error -> {
                            _error.postValue(result.message)
                        }
                        is Resource.Loading -> {}
                    }
                }
            }

            // Load adherence data
            launch {
                val result = intakeRepository.getAdherencePercentage(7)
                adherencePercentage = (result as? Resource.Success)?.data ?: -1f
                recalculateAll()
            }

            // Load reminder optimization
            launch {
                try {
                    val result = intakeRepository.getIntakeRecords(30)
                    val intakeRecords = (result as? Resource.Success)?.data ?: emptyList()
                    if (medicines.isNotEmpty()) {
                        val optimization = ReminderOptimizer.optimizeReminders(medicines, intakeRecords)
                        _reminderSuggestions.postValue(optimization)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun recalculateAll() {
        if (healthLogs.isEmpty() && medicines.isEmpty()) {
            _isLoading.postValue(false)
            return
        }

        viewModelScope.launch {
            try {
                val effectiveAdherence = if (adherencePercentage >= 0) {
                    adherencePercentage
                } else {
                    val stats = HealthAnalytics.calculateTodayAdherence(medicines)
                    stats.adherencePercentage
                }

                // Use InsightEngine facade for all analytics
                val insight = InsightEngine.computeFullInsight(
                    logs = healthLogs,
                    medicines = medicines,
                    adherencePercentage = effectiveAdherence
                )

                _riskScore.postValue(insight.riskScore)
                _trendAnalysis.postValue(insight.trendAnalysis)
                _suggestions.postValue(insight.suggestions)

                // Persist risk score with debounce
                persistRiskScoreIfNeeded(insight.riskScore)

                // Reminder optimization (if we have medicines loaded now)
                if (medicines.isNotEmpty()) {
                    try {
                        val intakeResult = intakeRepository.getIntakeRecords(30)
                        val intakeRecords = (intakeResult as? Resource.Success)?.data ?: emptyList()
                        val optimization = if (intakeRecords.isNotEmpty()) {
                            ReminderOptimizer.optimizeReminders(medicines, intakeRecords)
                        } else {
                            val schedulesSuggestions = ReminderOptimizer.analyzeSchedulePatterns(medicines)
                            ReminderOptimizer.OptimizationResult(
                                suggestions = schedulesSuggestions,
                                overallAdherenceRate = effectiveAdherence,
                                bestTimeSlot = null,
                                worstTimeSlot = null,
                                analysis = emptyList()
                            )
                        }
                        _reminderSuggestions.postValue(optimization)
                    } catch (_: Exception) {}
                }

                _isLoading.postValue(false)
            } catch (e: Exception) {
                _error.postValue(e.message ?: "Analysis failed")
                _isLoading.postValue(false)
            }
        }
    }

    private suspend fun persistRiskScoreIfNeeded(result: RiskScoreEngine.RiskScoreResult) {
        val now = System.currentTimeMillis()
        val categoryChanged = lastPersistedCategory != null && lastPersistedCategory != result.category
        val debounceExpired = (now - lastPersistedTime) >= persistDebounceMs

        if (categoryChanged || debounceExpired) {
            val userId = authRepository.currentUser?.uid ?: return
            val riskScore = RiskScore.fromResult(userId, result)
            riskScoreRepository.saveRiskScore(riskScore)
            lastPersistedCategory = result.category
            lastPersistedTime = now
        }
    }
}
