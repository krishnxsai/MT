package com.example.meditrack.ui.insights

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meditrack.R
import com.example.meditrack.data.analytics.HealthAnalytics
import com.example.meditrack.data.model.HealthLog
import com.example.meditrack.data.model.Medicine
import com.example.meditrack.data.model.Resource
import com.example.meditrack.databinding.ActivityHealthInsightsBinding
import com.example.meditrack.ui.healthlog.HealthLogViewModel
import com.example.meditrack.ui.medicine.MedicineViewModel
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import java.util.Locale

class HealthInsightsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHealthInsightsBinding
    private val healthLogViewModel: HealthLogViewModel by viewModels()
    private val medicineViewModel: MedicineViewModel by viewModels()

    private lateinit var riskAlertAdapter: RiskAlertAdapter

    private var allLogs: List<HealthLog> = emptyList()
    private var allMedicines: List<Medicine> = emptyList()
    private var selectedDays: Int = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHealthInsightsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDateFilter()
        setupRiskAlertsRecyclerView()
        observeViewModels()
    }

    private fun setupRiskAlertsRecyclerView() {
        riskAlertAdapter = RiskAlertAdapter()
        binding.riskAlertsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@HealthInsightsActivity)
            adapter = riskAlertAdapter
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupDateFilter() {
        binding.dateFilterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedDays = when {
                checkedIds.contains(R.id.chip7Days) -> 7
                checkedIds.contains(R.id.chip30Days) -> 30
                checkedIds.contains(R.id.chip90Days) -> 90
                else -> 7
            }
            updateInsights()
        }
    }

    private fun observeViewModels() {
        healthLogViewModel.healthLogs.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    allLogs = result.data
                    updateInsights()
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        medicineViewModel.medicines.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    allMedicines = result.data
                    updateAdherence()
                }
                else -> {}
            }
        }
    }

    private fun updateAdherence() {
        val adherence = HealthAnalytics.calculateTodayAdherence(allMedicines)
        val percentage = adherence.adherencePercentage.toInt()
        binding.adherencePercentage.text = "${percentage}%"
        binding.takenCount.text = getString(R.string.taken_count, adherence.taken)
        binding.pendingCount.text = getString(R.string.pending_count, adherence.pending)

        try {
            binding.adherenceProgress.setProgressCompat(percentage, true)
        } catch (e: Exception) {
            // Fallback if progress indicator not in layout
        }
    }

    private fun updateInsights() {
        val filteredLogs = HealthAnalytics.getLogsForLastDays(allLogs, selectedDays)
        val insights = HealthAnalytics.calculateAllInsights(filteredLogs)

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

        // Update Risk Alerts
        updateRiskAlerts(insights.riskAlerts)

        // Update Charts
        updateCharts(filteredLogs)
    }

    private fun updateRiskAlerts(alerts: List<HealthAnalytics.RiskAlert>) {
        if (alerts.isEmpty()) {
            binding.riskAlertsCard.visibility = View.GONE
        } else {
            binding.riskAlertsCard.visibility = View.VISIBLE
            riskAlertAdapter.submitList(alerts)
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

        // Delegate to ChartStyleHelper for consistent, medical-grade styling
        ChartStyleHelper.styleLineChart(binding.heartRateChart, this, ChartStyleHelper.ChartType.HEART_RATE)
        val dataSet = ChartStyleHelper.createStyledDataSet(entries, "Heart Rate", ChartStyleHelper.ChartType.HEART_RATE)
        binding.heartRateChart.xAxis.valueFormatter = ChartStyleHelper.createDateFormatter(filteredLogs.map { it.date })

        // Attach marker for on-tap value display
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
