package com.meditrack.app.ui.insights

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.meditrack.app.R
import com.meditrack.app.data.analytics.HealthInsightEngine
import com.meditrack.app.data.analytics.ReminderOptimizer
import com.meditrack.app.data.analytics.RiskScoreEngine
import com.meditrack.app.data.analytics.TrendPredictionEngine
import com.meditrack.app.databinding.ActivityRiskDashboardBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RiskDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRiskDashboardBinding
    private val viewModel: RiskDashboardViewModel by viewModels()

    private lateinit var trendAlertAdapter: TrendAlertAdapter
    private lateinit var suggestionAdapter: HealthSuggestionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRiskDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerViews()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerViews() {
        trendAlertAdapter = TrendAlertAdapter()
        binding.trendsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@RiskDashboardActivity)
            adapter = trendAlertAdapter
            isNestedScrollingEnabled = false
        }

        suggestionAdapter = HealthSuggestionAdapter()
        binding.insightsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@RiskDashboardActivity)
            adapter = suggestionAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }

        viewModel.riskScore.observe(this) { result ->
            updateRiskScoreUI(result)
        }

        viewModel.trendAnalysis.observe(this) { result ->
            updateTrendAlertsUI(result)
        }

        viewModel.suggestions.observe(this) { suggestions ->
            updateSuggestionsUI(suggestions)
        }

        viewModel.reminderSuggestions.observe(this) { result ->
            updateReminderUI(result)
        }
    }

    private fun updateRiskScoreUI(result: RiskScoreEngine.RiskScoreResult) {
        binding.riskScoreValue.text = result.overallScore.toString()
        binding.riskScoreProgress.setProgressCompat(result.overallScore, true)

        val (categoryText, cardColor) = when (result.category) {
            RiskScoreEngine.RiskCategory.LOW -> "Low Risk" to R.color.medicine_green
            RiskScoreEngine.RiskCategory.MODERATE -> "Moderate Risk" to R.color.warning
            RiskScoreEngine.RiskCategory.HIGH -> "High Risk" to R.color.medicine_orange
            RiskScoreEngine.RiskCategory.CRITICAL -> "Critical Risk" to R.color.medicine_red
        }

        binding.riskCategoryText.text = categoryText
        binding.riskBadgeText.text = result.category.name
        binding.riskScoreCard.setCardBackgroundColor(ContextCompat.getColor(this, cardColor))
        binding.riskBadgeText.setTextColor(ContextCompat.getColor(this, cardColor))

        updateFactorsBreakdown(result)
    }

    private fun updateFactorsBreakdown(result: RiskScoreEngine.RiskScoreResult) {
        binding.factorsContainer.removeAllViews()

        for ((name, score) in result.subscores) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, dpToPx(14))
            }

            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val nameText = TextView(this).apply {
                text = name
                setTextColor(ContextCompat.getColor(this@RiskDashboardActivity, R.color.text_primary))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val scoreColor = when {
                score <= 25 -> R.color.medicine_green
                score <= 50 -> R.color.medicine_orange
                else -> R.color.medicine_red
            }

            val scoreText = TextView(this).apply {
                text = "$score/100"
                setTextColor(ContextCompat.getColor(this@RiskDashboardActivity, scoreColor))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            headerRow.addView(nameText)
            headerRow.addView(scoreText)

            val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = score
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(6)
                ).apply { topMargin = dpToPx(6) }
                progressTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this@RiskDashboardActivity, scoreColor)
                )
                progressBackgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this@RiskDashboardActivity, R.color.surface_variant)
                )
            }

            row.addView(headerRow)
            row.addView(progressBar)
            binding.factorsContainer.addView(row)
        }

        for (factor in result.contributingFactors) {
            val factorRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(4), 0, dpToPx(4))
            }

            val indicator = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(4), dpToPx(24)).apply {
                    marginEnd = dpToPx(10)
                }
                val color = when (factor.severity) {
                    RiskScoreEngine.FactorSeverity.DANGER -> R.color.medicine_red
                    RiskScoreEngine.FactorSeverity.WARNING -> R.color.medicine_orange
                    RiskScoreEngine.FactorSeverity.INFO -> R.color.medicine_blue
                }
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this@RiskDashboardActivity, color)
                )
                background = ContextCompat.getDrawable(this@RiskDashboardActivity, R.drawable.bg_rounded_4dp)
            }

            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val titleText = TextView(this).apply {
                text = factor.factor
                setTextColor(ContextCompat.getColor(this@RiskDashboardActivity, R.color.text_primary))
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val descText = TextView(this).apply {
                text = factor.description
                setTextColor(ContextCompat.getColor(this@RiskDashboardActivity, R.color.text_secondary))
                textSize = 12f
            }

            textLayout.addView(titleText)
            textLayout.addView(descText)
            factorRow.addView(indicator)
            factorRow.addView(textLayout)
            binding.factorsContainer.addView(factorRow)
        }
    }

    private fun updateTrendAlertsUI(result: TrendPredictionEngine.TrendAnalysisResult) {
        trendAlertAdapter.submitList(result.alerts)

        val urgentAlerts = result.alerts.filter {
            it.priority == TrendPredictionEngine.TrendPriority.URGENT ||
                    it.priority == TrendPredictionEngine.TrendPriority.HIGH
        }
        if (urgentAlerts.isNotEmpty()) {
            binding.predictiveAlertBanner.visibility = View.VISIBLE
            val names = urgentAlerts.joinToString(", ") { it.vitalType.displayName }
            binding.predictiveAlertText.text =
                "\u26A0 $names showing concerning trends \u2014 consider consulting your doctor."
            binding.dismissAlertBtn.setOnClickListener {
                binding.predictiveAlertBanner.visibility = View.GONE
            }
        } else {
            binding.predictiveAlertBanner.visibility = View.GONE
        }
    }

    private fun updateSuggestionsUI(suggestions: List<HealthInsightEngine.HealthSuggestion>) {
        suggestionAdapter.submitList(suggestions)

        if (suggestions.isEmpty()) {
            binding.noInsightsText.visibility = View.VISIBLE
            binding.insightsRecyclerView.visibility = View.GONE
            binding.insightCountBadge.visibility = View.GONE
        } else {
            binding.noInsightsText.visibility = View.GONE
            binding.insightsRecyclerView.visibility = View.VISIBLE
            binding.insightCountBadge.visibility = View.VISIBLE
            binding.insightCountBadge.text = "${suggestions.size} new"
        }
    }

    private fun updateReminderUI(result: ReminderOptimizer.OptimizationResult?) {
        if (result == null || result.suggestions.isEmpty()) {
            binding.reminderOptCard.visibility = View.GONE
            return
        }

        binding.reminderOptCard.visibility = View.VISIBLE
        binding.reminderSuggestionsContainer.removeAllViews()

        for (suggestion in result.suggestions) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dpToPx(8), 0, dpToPx(8))
            }

            val medName = TextView(this).apply {
                text = suggestion.medicineName
                setTextColor(ContextCompat.getColor(this@RiskDashboardActivity, R.color.text_primary))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val timeChange = TextView(this).apply {
                text = "${fmtTime(suggestion.currentTime)} \u2192 ${fmtTime(suggestion.suggestedTime)}"
                setTextColor(ContextCompat.getColor(this@RiskDashboardActivity, R.color.primary))
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dpToPx(4) }
            }

            val reason = TextView(this).apply {
                text = suggestion.reason
                setTextColor(ContextCompat.getColor(this@RiskDashboardActivity, R.color.text_secondary))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dpToPx(4) }
            }

            row.addView(medName)
            row.addView(timeChange)
            row.addView(reason)
            binding.reminderSuggestionsContainer.addView(row)
        }
    }

    private fun fmtTime(time24: String): String {
        return try {
            val inputFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val outputFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
            val date = inputFormat.parse(time24)
            date?.let { outputFormat.format(it) } ?: time24
        } catch (_: Exception) { time24 }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
