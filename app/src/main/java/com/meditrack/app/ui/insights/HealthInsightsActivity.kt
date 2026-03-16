package com.meditrack.app.ui.insights

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.meditrack.app.R
import com.meditrack.app.data.analytics.RiskScoreEngine
import com.meditrack.app.data.model.HealthLog
import com.meditrack.app.databinding.ActivityHealthInsightsBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HealthInsightsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHealthInsightsBinding
    private val viewModel: HealthInsightsViewModel by viewModels()

    private lateinit var unifiedAlertAdapter: UnifiedAlertAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHealthInsightsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDateFilter()
        setupAlertsRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupAlertsRecyclerView() {
        unifiedAlertAdapter = UnifiedAlertAdapter()
        binding.riskAlertsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@HealthInsightsActivity)
            adapter = unifiedAlertAdapter
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupDateFilter() {
        binding.dateFilterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val days = when {
                checkedIds.contains(R.id.chip7Days) -> 7
                checkedIds.contains(R.id.chip30Days) -> 30
                checkedIds.contains(R.id.chip90Days) -> 90
                else -> 7
            }
            viewModel.setDateRange(days)
        }
    }

    private fun setupClickListeners() {
        binding.insightRiskScoreCard.setOnClickListener {
            startActivity(Intent(this, RiskDashboardActivity::class.java))
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.healthInsights.observe(this) { insights ->
            // Update Heart Rate Stats
            insights.heartRateStats?.let { stats ->
                binding.heartRateAvg.text = "${stats.average.toInt()}"
            } ?: run {
                binding.heartRateAvg.text = "--"
            }

            // Update Blood Pressure Stats
            if (insights.systolicBPStats != null && insights.diastolicBPStats != null) {
                val systolicAvg = insights.systolicBPStats.average.toInt()
                val diastolicAvg = insights.diastolicBPStats.average.toInt()
                binding.bloodPressureAvg.text = "$systolicAvg/$diastolicAvg"
            } else {
                binding.bloodPressureAvg.text = "--"
            }

            // Update Glucose Stats
            insights.glucoseStats?.let { stats ->
                binding.glucoseAvg.text = "${stats.average.toInt()}"
            } ?: run {
                binding.glucoseAvg.text = "--"
            }

            // Update Weight Stats
            insights.weightStats?.let { stats ->
                binding.weightAvg.text = String.format(Locale.getDefault(), "%.1f", stats.average)
            } ?: run {
                binding.weightAvg.text = "--"
            }
        }

        viewModel.riskScore.observe(this) { riskResult ->
            updateRiskScoreBadge(riskResult)
        }

        viewModel.unifiedAlerts.observe(this) { alerts ->
            if (alerts.isEmpty()) {
                binding.riskAlertsCard.visibility = View.GONE
            } else {
                binding.riskAlertsCard.visibility = View.VISIBLE
                unifiedAlertAdapter.submitList(alerts)
            }
        }

        viewModel.adherenceStats.observe(this) { adherence ->
            val percentage = adherence.adherencePercentage.toInt()
            binding.adherencePercentage.text = "${percentage}%"
            binding.takenCount.text = getString(R.string.taken_count, adherence.taken)
            binding.missedCount.text = getString(R.string.missed_count, adherence.missed)
            binding.pendingCount.text = getString(R.string.pending_count, adherence.pending)

            try {
                binding.adherenceProgress.setProgressCompat(percentage, true)
            } catch (_: Exception) {}
        }

        viewModel.filteredLogs.observe(this) { logs ->
            updateCharts(logs)
        }
    }

    private fun updateRiskScoreBadge(riskResult: RiskScoreEngine.RiskScoreResult) {
        binding.insightRiskScoreCard.visibility = View.VISIBLE
        binding.insightRiskScore.text = riskResult.overallScore.toString()
        binding.insightRiskGauge.setProgressCompat(riskResult.overallScore, true)

        val (label, gaugeColor) = when (riskResult.category) {
            RiskScoreEngine.RiskCategory.LOW -> "Low Risk" to R.color.medicine_green
            RiskScoreEngine.RiskCategory.MODERATE -> "Moderate Risk" to R.color.warning
            RiskScoreEngine.RiskCategory.HIGH -> "High Risk" to R.color.medicine_orange
            RiskScoreEngine.RiskCategory.CRITICAL -> "Critical Risk" to R.color.medicine_red
        }

        binding.insightRiskBadge.text = label
        binding.insightRiskBadge.setTextColor(ContextCompat.getColor(this, gaugeColor))
        binding.insightRiskGauge.setIndicatorColor(ContextCompat.getColor(this, gaugeColor))

        val factorCount = riskResult.contributingFactors.size
        binding.insightRiskSummary.text = when {
            factorCount == 0 -> "All clear — tap for full analysis"
            factorCount == 1 -> "1 risk factor — tap for details"
            else -> "$factorCount risk factors — tap for details"
        }
    }

    private fun updateCharts(logs: List<HealthLog>) {
        setupHeartRateChart(logs)
        setupBloodPressureChart(logs)
        setupGlucoseChart(logs)
    }

    private fun setupHeartRateChart(logs: List<HealthLog>) {
        val filteredLogs = logs.filter { it.heartRate != null }.sortedBy { it.date }
        val entries = filteredLogs.mapIndexed { index, log ->
            Entry(index.toFloat(), log.heartRate!!.toFloat())
        }

        if (entries.isEmpty()) {
            binding.heartRateChart.clear()
            binding.heartRateChart.setNoDataText(getString(R.string.no_data_available))
            binding.heartRateChart.setNoDataTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            return
        }

        ChartStyleHelper.styleLineChart(binding.heartRateChart, this, ChartStyleHelper.ChartType.HEART_RATE)
        val dataSet = ChartStyleHelper.createStyledDataSet(entries, "Heart Rate", ChartStyleHelper.ChartType.HEART_RATE)
        binding.heartRateChart.xAxis.valueFormatter = ChartStyleHelper.createDateFormatter(filteredLogs.map { it.date })
        binding.heartRateChart.marker = ChartMarkerView(this, ChartStyleHelper.ChartType.HEART_RATE, filteredLogs.map { it.date })

        binding.heartRateChart.data = LineData(dataSet)
        ChartStyleHelper.animateChart(binding.heartRateChart)
    }

    private fun setupBloodPressureChart(logs: List<HealthLog>) {
        val filteredLogs = logs.filter { it.bloodPressureSystolic != null || it.bloodPressureDiastolic != null }.sortedBy { it.date }

        val systolicEntries = filteredLogs.mapIndexedNotNull { index, log ->
            log.bloodPressureSystolic?.let { Entry(index.toFloat(), it.toFloat()) }
        }

        val diastolicEntries = filteredLogs.mapIndexedNotNull { index, log ->
            log.bloodPressureDiastolic?.let { Entry(index.toFloat(), it.toFloat()) }
        }

        if (systolicEntries.isEmpty() && diastolicEntries.isEmpty()) {
            binding.bloodPressureChart.clear()
            binding.bloodPressureChart.setNoDataText(getString(R.string.no_data_available))
            binding.bloodPressureChart.setNoDataTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            return
        }

        ChartStyleHelper.styleLineChart(binding.bloodPressureChart, this, ChartStyleHelper.ChartType.BLOOD_PRESSURE)
        val systolicDataSet = ChartStyleHelper.createStyledDataSet(systolicEntries, "Systolic", ChartStyleHelper.ChartType.BLOOD_PRESSURE)
        val diastolicDataSet = ChartStyleHelper.createStyledDataSet(diastolicEntries, "Diastolic", ChartStyleHelper.ChartType.BLOOD_PRESSURE, isSecondary = true)
        binding.bloodPressureChart.xAxis.valueFormatter = ChartStyleHelper.createDateFormatter(filteredLogs.map { it.date })
        binding.bloodPressureChart.marker = ChartMarkerView(this, ChartStyleHelper.ChartType.BLOOD_PRESSURE, filteredLogs.map { it.date })

        binding.bloodPressureChart.data = LineData(systolicDataSet, diastolicDataSet)
        ChartStyleHelper.animateChart(binding.bloodPressureChart)
    }

    private fun setupGlucoseChart(logs: List<HealthLog>) {
        val filteredLogs = logs.filter { it.glucoseLevel != null }.sortedBy { it.date }
        val entries = filteredLogs.mapIndexed { index, log ->
            Entry(index.toFloat(), log.glucoseLevel!!.toFloat())
        }

        if (entries.isEmpty()) {
            binding.glucoseChart.clear()
            binding.glucoseChart.setNoDataText(getString(R.string.no_data_available))
            binding.glucoseChart.setNoDataTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            return
        }

        ChartStyleHelper.styleLineChart(binding.glucoseChart, this, ChartStyleHelper.ChartType.GLUCOSE)
        val dataSet = ChartStyleHelper.createStyledDataSet(entries, "Glucose", ChartStyleHelper.ChartType.GLUCOSE)
        binding.glucoseChart.xAxis.valueFormatter = ChartStyleHelper.createDateFormatter(filteredLogs.map { it.date })
        binding.glucoseChart.marker = ChartMarkerView(this, ChartStyleHelper.ChartType.GLUCOSE, filteredLogs.map { it.date })

        binding.glucoseChart.data = LineData(dataSet)
        ChartStyleHelper.animateChart(binding.glucoseChart)
    }
}
