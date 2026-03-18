package com.meditrack.app.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import com.meditrack.app.data.analytics.InsightEngine
import com.meditrack.app.data.analytics.RiskScoreEngine
import com.meditrack.app.data.model.HealthLog
import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.analytics.HealthAnalytics
import com.meditrack.app.data.repository.MedicineIntakeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeDashboardViewModel @Inject constructor(
    private val intakeRepository: MedicineIntakeRepository
) : ViewModel() {

    data class RiskCardState(
        val overallScore: Int,
        val category: RiskScoreEngine.RiskCategory,
        val factorCount: Int
    )

    private val _riskCardState = MutableLiveData<RiskCardState?>()
    val riskCardState: LiveData<RiskCardState?> = _riskCardState

    private var riskScoreJob: Job? = null

    private val _adherenceStats = MutableLiveData<HealthAnalytics.AdherenceStats?>()
    val adherenceStats: LiveData<HealthAnalytics.AdherenceStats?> = _adherenceStats

    fun computeRiskScore(logs: List<HealthLog>, medicines: List<Medicine>) {
        riskScoreJob?.cancel()
        riskScoreJob = viewModelScope.launch {
            try {
                // Use the same 7-day adherence window as RiskDashboardViewModel to ensure
                // the compact card and full dashboard show a consistent risk score.
                val result = intakeRepository.getAdherencePercentage(7)
                // Match RiskDashboardViewModel: convert -1 (no data) to 0
                val adherencePercentage = when (result) {
                    is Resource.Success -> if (result.data >= 0) result.data else 0f
                    else -> 0f
                }
                val insight = InsightEngine.computeFullInsight(
                    logs = logs,
                    medicines = medicines,
                    adherencePercentage = adherencePercentage
                )
                _riskCardState.value = RiskCardState(
                    overallScore = insight.riskScore.overallScore,
                    category = insight.riskScore.category,
                    factorCount = insight.riskScore.contributingFactors.size
                )
            } catch (_: Exception) {
                _riskCardState.value = null
            }
        }
    }

    fun loadAdherenceStats(medicines: List<Medicine>) {
        viewModelScope.launch {
            try {
                val result = intakeRepository.getTodayAdherenceStats(medicines)
                _adherenceStats.value = (result as? Resource.Success)?.data
            } catch (_: Exception) {
                _adherenceStats.value = null
            }
        }
    }
}
