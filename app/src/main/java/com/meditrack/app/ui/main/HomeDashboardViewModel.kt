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
import kotlinx.coroutines.flow.collectLatest
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
    private var adherenceStatsJob: Job? = null
    private var adherenceObserverKey: String? = null

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

    fun observeAdherenceStats(medicines: List<Medicine>) {
        val activeMedicines = medicines.filter { it.isActive }
        if (activeMedicines.isEmpty()) {
            stopAdherenceObservation()
            _adherenceStats.value = HealthAnalytics.AdherenceStats(
                totalScheduled = 0,
                taken = 0,
                missed = 0,
                pending = 0,
                adherencePercentage = 100f
            )
            return
        }

        val nextKey = buildAdherenceObserverKey(activeMedicines)
        if (adherenceObserverKey == nextKey && adherenceStatsJob?.isActive == true) {
            return
        }

        adherenceObserverKey = nextKey
        adherenceStatsJob?.cancel()
        adherenceStatsJob = viewModelScope.launch {
            intakeRepository.observeTodayAdherenceStats(activeMedicines).collectLatest { result ->
                when (result) {
                    is Resource.Success -> _adherenceStats.postValue(result.data)
                    is Resource.Error -> _adherenceStats.postValue(null)
                    is Resource.Loading -> {
                        // Keep the previous value while loading to avoid flicker.
                    }
                }
            }
        }
    }

    fun stopAdherenceObservation() {
        adherenceStatsJob?.cancel()
        adherenceStatsJob = null
        adherenceObserverKey = null
    }

    private fun buildAdherenceObserverKey(medicines: List<Medicine>): String {
        return medicines
            .sortedBy { it.id }
            .joinToString("|") { medicine ->
                val reminderKey = medicine.reminderTimes.sorted().joinToString(",")
                "${medicine.id}:$reminderKey"
            }
    }

    override fun onCleared() {
        riskScoreJob?.cancel()
        adherenceStatsJob?.cancel()
        super.onCleared()
    }
}
