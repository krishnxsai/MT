package com.meditrack.app.domain.usecase

import com.meditrack.app.data.analytics.InsightEngine
import com.meditrack.app.data.analytics.RiskScoreEngine
import com.meditrack.app.data.model.HealthLog
import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.RiskScore
import com.meditrack.app.data.repository.HealthLogRepository
import com.meditrack.app.data.repository.MedicineIntakeRepository
import com.meditrack.app.data.repository.MedicineRepository
import com.meditrack.app.data.repository.RiskScoreRepository
import javax.inject.Inject

data class RiskDashboardData(
    val riskScore: RiskScore?,
    val comprehensiveInsight: InsightEngine.ComprehensiveInsight?,
    val healthLogs: List<HealthLog>,
    val adherencePercentage: Float
)

class ComputeRiskDashboardUseCase @Inject constructor(
    private val healthLogRepository: HealthLogRepository,
    private val medicineRepository: MedicineRepository,
    private val medicineIntakeRepository: MedicineIntakeRepository,
    private val riskScoreRepository: RiskScoreRepository
) {
    suspend fun execute(): Resource<RiskDashboardData> {
        return try {
            val logsResult = healthLogRepository.getHealthLogs()
            val medsResult = medicineRepository.getMedicines()
            val adherenceResult = medicineIntakeRepository.getAdherencePercentage()

            val logs = (logsResult as? Resource.Success)?.data ?: emptyList()
            val meds = (medsResult as? Resource.Success)?.data ?: emptyList()
            val adherence = (adherenceResult as? Resource.Success)?.data ?: 0f

            val riskResult = RiskScoreEngine.calculateRiskScore(logs, meds, adherence)
            val insight = InsightEngine.computeFullInsight(logs, meds, adherence)

            // Create risk score using the helper method
            val riskScore = RiskScore.fromResult("", riskResult)

            Resource.Success(
                RiskDashboardData(
                    riskScore = riskScore,
                    comprehensiveInsight = insight,
                    healthLogs = logs,
                    adherencePercentage = adherence
                )
            )
        } catch (e: Exception) {
            Resource.Error("Failed to compute risk dashboard: ${e.message}", e)
        }
    }
}
