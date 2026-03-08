package com.example.meditrack.ui.insights

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meditrack.data.analytics.HealthInsightEngine
import com.example.meditrack.data.analytics.ReminderOptimizer
import com.example.meditrack.data.analytics.RiskScoreEngine
import com.example.meditrack.data.analytics.TrendPredictionEngine
import com.example.meditrack.data.analytics.HealthAnalytics
import com.example.meditrack.data.model.HealthLog
import com.example.meditrack.data.model.Medicine
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.repository.HealthLogRepository
import com.example.meditrack.data.repository.MedicineIntakeRepository
import com.example.meditrack.data.repository.MedicineRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RiskDashboardViewModel : ViewModel() {

    private val healthLogRepository = HealthLogRepository()
    private val medicineRepository = MedicineRepository()
    private val intakeRepository = MedicineIntakeRepository()

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
                adherencePercentage = intakeRepository.getAdherencePercentage(7)
                recalculateAll()
            }

            // Load reminder optimization
            launch {
                try {
                    val intakeRecords = intakeRepository.getIntakeRecords(30)
                    if (medicines.isNotEmpty()) {
                        val optimization = ReminderOptimizer.optimizeReminders(medicines, intakeRecords)
                        _reminderSuggestions.postValue(optimization)
                    } else {
                        // Will be recalculated when medicines load
                    }
                } catch (e: Exception) {
                    // Non-critical, ignore
                }
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
                // Get last 30 days of logs for analysis
                val recentLogs = HealthAnalytics.getLogsForLastDays(healthLogs, 30)

                // Use schedule-based adherence as fallback
                val effectiveAdherence = if (adherencePercentage >= 0) {
                    adherencePercentage
                } else {
                    val stats = HealthAnalytics.calculateTodayAdherence(medicines)
                    stats.adherencePercentage
                }

                // 1. Risk Score
                val riskResult = RiskScoreEngine.calculateRiskScore(
                    logs = recentLogs,
                    medicines = medicines,
                    adherencePercentage = effectiveAdherence
                )
                _riskScore.postValue(riskResult)

                // 2. Trend Analysis
                val trendResult = TrendPredictionEngine.analyzeAllTrends(recentLogs)
                _trendAnalysis.postValue(trendResult)

                // 3. AI Suggestions
                val insightSuggestions = HealthInsightEngine.generateInsights(
                    riskScore = riskResult,
                    trendAnalysis = trendResult,
                    logs = recentLogs,
                    medicines = medicines,
                    adherencePercentage = effectiveAdherence
                )
                _suggestions.postValue(insightSuggestions)

                // 4. Reminder optimization (if we have medicines loaded now)
                if (medicines.isNotEmpty()) {
                    try {
                        val intakeRecords = intakeRepository.getIntakeRecords(30)
                        val optimization = if (intakeRecords.isNotEmpty()) {
                            ReminderOptimizer.optimizeReminders(medicines, intakeRecords)
                        } else {
                            // Use schedule-based analysis as fallback
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
}

