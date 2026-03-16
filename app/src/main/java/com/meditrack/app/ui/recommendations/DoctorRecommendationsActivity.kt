package com.meditrack.app.ui.recommendations

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.meditrack.app.R
import com.meditrack.app.data.model.*
import com.meditrack.app.databinding.ActivityDoctorRecommendationsBinding
import com.meditrack.app.util.overrideTransitionCompat
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.*
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity displaying doctor recommendations to patients.
 * Shows prescriptions, follow-ups, alerts, and recommendations in a clean medical-grade UI.
 */
@AndroidEntryPoint
class DoctorRecommendationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDoctorRecommendationsBinding
    private val viewModel: DoctorRecommendationsViewModel by viewModels()

    private lateinit var recommendationAdapter: RecommendationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDoctorRecommendationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupBottomNavigation()
        setupTabs()
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()

        // Load data
        viewModel.refreshAll()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigation?.setOnItemSelectedListener(null)
        binding.bottomNavigation?.selectedItemId = R.id.nav_doctor
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation?.setOnItemSelectedListener(null)
        binding.bottomNavigation?.selectedItemId = R.id.nav_doctor
        binding.bottomNavigation?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, com.meditrack.app.ui.main.HomeDashboardActivity::class.java))
                    overrideTransitionCompat(0, 0)
                    finish()
                    true
                }
                R.id.nav_medicines -> {
                    startActivity(Intent(this, com.meditrack.app.ui.medicine.MedicinesListActivity::class.java))
                    overrideTransitionCompat(0, 0)
                    finish()
                    true
                }
                R.id.nav_health_logs -> {
                    startActivity(Intent(this, com.meditrack.app.ui.healthlog.HealthLogListActivity::class.java))
                    overrideTransitionCompat(0, 0)
                    finish()
                    true
                }
                R.id.nav_doctor -> true // Already here
                R.id.nav_profile -> {
                    startActivity(Intent(this, com.meditrack.app.ui.profile.ProfileActivity::class.java))
                    overrideTransitionCompat(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupToolbar() {
        try { binding.toolbar.setNavigationOnClickListener { finish() } } catch (_: Exception) {}
        try { binding.backBtn?.setOnClickListener { finish() } } catch (_: Exception) {}
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showAllRecommendations()
                    1 -> showPrescriptions()
                    2 -> showFollowUps()
                    3 -> showAlerts()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerView() {
        recommendationAdapter = RecommendationAdapter(
            onItemClick = { decision -> showDecisionDetail(decision) },
            onAcknowledgeClick = { decision -> acknowledgeDecision(decision) }
        )

        binding.recommendationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DoctorRecommendationsActivity)
            adapter = recommendationAdapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshAll()
        }
    }

    private fun observeViewModel() {
        viewModel.recommendations.observe(this) { result ->
            binding.swipeRefresh.isRefreshing = false
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    if (binding.tabLayout.selectedTabPosition == 0) {
                        updateRecommendationsList(result.data)
                    }
                    updateAlertsCount(result.data.count { it.priority == Priority.HIGH || it.priority == Priority.CRITICAL })
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    if (!isTransientError(result.message)) {
                        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        viewModel.prescriptions.observe(this) { result ->
            if (result is Resource.Success && binding.tabLayout.selectedTabPosition == 1) {
                updateRecommendationsList(result.data)
            }
        }

        viewModel.followUps.observe(this) { result ->
            if (result is Resource.Success && binding.tabLayout.selectedTabPosition == 2) {
                updateRecommendationsList(result.data)
            }
        }

        viewModel.criticalAlerts.observe(this) { result ->
            if (result is Resource.Success && binding.tabLayout.selectedTabPosition == 3) {
                updateRecommendationsList(result.data)
            }
        }

        viewModel.acknowledgeResult.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    Toast.makeText(this, "Marked as seen", Toast.LENGTH_SHORT).show()
                    viewModel.clearAcknowledgeResult()
                }
                is Resource.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    private fun showAllRecommendations() {
        val result = viewModel.recommendations.value
        if (result is Resource.Success) {
            updateRecommendationsList(result.data)
        }
    }

    private fun showPrescriptions() {
        viewModel.loadActivePrescriptions()
    }

    private fun showFollowUps() {
        viewModel.loadUpcomingFollowUps()
    }

    private fun showAlerts() {
        viewModel.loadCriticalAlerts()
    }

    private fun updateRecommendationsList(decisions: List<ClinicalDecision>) {
        if (decisions.isEmpty()) {
            binding.emptyStateView.visibility = View.VISIBLE
            binding.recommendationsRecyclerView.visibility = View.GONE
            binding.emptyStateText.text = when (binding.tabLayout.selectedTabPosition) {
                1 -> "No active prescriptions"
                2 -> "No upcoming follow-ups"
                3 -> "No alerts"
                else -> "No recommendations from your doctor yet"
            }
        } else {
            binding.emptyStateView.visibility = View.GONE
            binding.recommendationsRecyclerView.visibility = View.VISIBLE
            recommendationAdapter.submitList(decisions)
        }
    }

    private fun updateAlertsCount(count: Int) {
        if (count > 0) {
            binding.alertsBadge.visibility = View.VISIBLE
            binding.alertsBadge.text = count.toString()
        } else {
            binding.alertsBadge.visibility = View.GONE
        }
    }

    private fun showDecisionDetail(decision: ClinicalDecision) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_recommendation_detail, null)

        val titleText = dialogView.findViewById<android.widget.TextView>(R.id.titleText)
        val doctorText = dialogView.findViewById<android.widget.TextView>(R.id.doctorText)
        val dateText = dialogView.findViewById<android.widget.TextView>(R.id.dateText)
        val typeChip = dialogView.findViewById<com.google.android.material.chip.Chip>(R.id.typeChip)
        val priorityChip = dialogView.findViewById<com.google.android.material.chip.Chip>(R.id.priorityChip)
        val descriptionText = dialogView.findViewById<android.widget.TextView>(R.id.descriptionText)
        val prescriptionsSection = dialogView.findViewById<android.widget.LinearLayout>(R.id.prescriptionsSection)
        val prescriptionsContainer = dialogView.findViewById<android.widget.LinearLayout>(R.id.prescriptionsContainer)
        val followUpSection = dialogView.findViewById<android.widget.LinearLayout>(R.id.followUpSection)
        val followUpDateText = dialogView.findViewById<android.widget.TextView>(R.id.followUpDateText)
        val followUpInstructionsText = dialogView.findViewById<android.widget.TextView>(R.id.followUpInstructionsText)

        titleText.text = decision.title
        doctorText.text = "Dr. ${decision.doctorName}"

        decision.createdAt?.let {
            val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            dateText.text = dateFormat.format(it)
        }

        typeChip.text = getTypeDisplayName(decision.type)
        priorityChip.text = decision.priority.name
        priorityChip.setChipBackgroundColorResource(getPriorityColor(decision.priority))

        descriptionText.text = decision.description.ifEmpty { "No additional notes" }

        // Show prescriptions if applicable
        if (decision.type == ClinicalDecisionType.PRESCRIPTION && decision.prescriptions.isNotEmpty()) {
            prescriptionsSection.visibility = View.VISIBLE
            prescriptionsContainer.removeAllViews()

            decision.prescriptions.forEach { rx ->
                val rxView = layoutInflater.inflate(R.layout.item_prescription_simple, prescriptionsContainer, false)
                rxView.findViewById<android.widget.TextView>(R.id.medNameText).text = rx.medicationName
                rxView.findViewById<android.widget.TextView>(R.id.dosageText).text = "${rx.dosage} ${rx.unit} - ${rx.frequency}"
                rxView.findViewById<android.widget.TextView>(R.id.instructionsText).apply {
                    text = rx.instructions
                    visibility = if (rx.instructions.isEmpty()) View.GONE else View.VISIBLE
                }
                prescriptionsContainer.addView(rxView)
            }
        }

        // Show follow-up date if applicable
        if (decision.type == ClinicalDecisionType.FOLLOW_UP && decision.followUpDate != null) {
            followUpSection.visibility = View.VISIBLE
            val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
            followUpDateText.text = dateFormat.format(decision.followUpDate)
            followUpInstructionsText.text = decision.followUpInstructions.ifEmpty { "No specific instructions" }
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .apply {
                if (!decision.patientAcknowledged) {
                    setNeutralButton("Mark as Seen") { _, _ ->
                        viewModel.acknowledgeRecommendation(decision.id)
                    }
                }
            }
            .show()
    }

    private fun acknowledgeDecision(decision: ClinicalDecision) {
        viewModel.acknowledgeRecommendation(decision.id)
    }

    private fun getTypeDisplayName(type: ClinicalDecisionType): String {
        return when (type) {
            ClinicalDecisionType.PRESCRIPTION -> "💊 Prescription"
            ClinicalDecisionType.FOLLOW_UP -> "📅 Follow-up"
            ClinicalDecisionType.VITAL_ALERT -> "⚠️ Vital Alert"
            ClinicalDecisionType.RECOMMENDATION -> "💡 Recommendation"
            ClinicalDecisionType.DIAGNOSIS -> "🔬 Diagnosis"
            ClinicalDecisionType.LAB_ORDER -> "🧪 Lab Order"
            ClinicalDecisionType.LIFESTYLE -> "🏃 Lifestyle"
        }
    }

    private fun getPriorityColor(priority: Priority): Int {
        return when (priority) {
            Priority.LOW -> R.color.medicine_green
            Priority.NORMAL -> R.color.medicine_blue
            Priority.HIGH -> R.color.medicine_orange
            Priority.CRITICAL -> R.color.medicine_red
        }
    }

    private fun isTransientError(message: String?): Boolean {
        if (message == null) return false
        val transientErrors = listOf(
            "PERMISSION_DENIED",
            "Missing or insufficient permissions",
            "requires an index"
        )
        return transientErrors.any { message.contains(it, ignoreCase = true) }
    }
}

