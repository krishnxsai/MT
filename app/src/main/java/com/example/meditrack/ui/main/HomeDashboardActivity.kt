package com.example.meditrack.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.meditrack.R
import com.example.meditrack.data.analytics.VitalAlertEngine
import com.example.meditrack.data.analytics.RiskScoreEngine
import com.example.meditrack.data.analytics.HealthAnalytics
import com.example.meditrack.data.model.ClinicalDecision
import com.example.meditrack.data.model.Medicine
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.model.User
import com.example.meditrack.databinding.ActivityHomeDashboardBinding
import com.example.meditrack.ui.auth.LoginActivity
import com.example.meditrack.ui.chat.ChatListActivity
import com.example.meditrack.ui.chat.ChatListViewModel
import com.example.meditrack.ui.appointment.AppointmentsListActivity
import com.example.meditrack.ui.healthlog.HealthLogListActivity
import com.example.meditrack.ui.healthlog.HealthLogViewModel
import com.example.meditrack.ui.insights.HealthInsightsActivity
import com.example.meditrack.ui.insights.RiskDashboardActivity
import com.example.meditrack.ui.medicine.MedicinesListActivity
import com.example.meditrack.ui.medicine.MedicineViewModel
import com.example.meditrack.ui.order.PharmacyListActivity
import com.example.meditrack.ui.order.RefillOrdersActivity
import com.example.meditrack.ui.profile.ProfileActivity
import com.example.meditrack.ui.profile.ProfileViewModel
import com.example.meditrack.ui.recommendations.DoctorRecommendationsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeDashboardBinding
    private val profileViewModel: ProfileViewModel by viewModels()
    private val medicineViewModel: MedicineViewModel by viewModels()
    private val healthLogViewModel: HealthLogViewModel by viewModels()
    private val recommendationsViewModel: DoctorRecommendationsViewModel by viewModels()
    private val chatListViewModel: ChatListViewModel by viewModels()

    private var cachedLogs: List<com.example.meditrack.data.model.HealthLog> = emptyList()
    private var cachedMedicines: List<Medicine> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
        setupClickListeners()
        observeViewModel()
        updateGreeting()
    }

    override fun onResume() {
        super.onResume()
        profileViewModel.loadCurrentUser()
        medicineViewModel.loadMedicinesOnce()
        // Temporarily clear listener to avoid triggering navigation when resetting selection
        binding.bottomNavigation?.setOnItemSelectedListener(null)
        binding.bottomNavigation?.selectedItemId = R.id.nav_home
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation?.setOnItemSelectedListener(null)
        binding.bottomNavigation?.selectedItemId = R.id.nav_home
        binding.bottomNavigation?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true // Already here
                R.id.nav_medicines -> {
                    startActivity(Intent(this, MedicinesListActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_health_logs -> {
                    startActivity(Intent(this, HealthLogListActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_doctor -> {
                    startActivity(Intent(this, com.example.meditrack.ui.recommendations.DoctorRecommendationsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupClickListeners() {
        // Profile Image Click
        binding.profileImageContainer.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Schedule Card - View All
        binding.viewAllText.setOnClickListener {
            startActivity(Intent(this, MedicinesListActivity::class.java))
        }

        // Health Insights Card
        binding.healthInsightsCard.setOnClickListener {
            startActivity(Intent(this, com.example.meditrack.ui.insights.HealthInsightsActivity::class.java))
        }

        // Risk Score Card
        binding.riskScoreCard.setOnClickListener {
            startActivity(Intent(this, RiskDashboardActivity::class.java))
        }


        // Chat Card
        binding.chatCard?.setOnClickListener {
            startActivity(Intent(this, ChatListActivity::class.java))
        }

        // Appointments Card
        binding.appointmentsCard?.setOnClickListener {
            startActivity(Intent(this, AppointmentsListActivity::class.java))
        }

        // Refill Orders Card
        binding.refillOrdersCard?.setOnClickListener {
            startActivity(Intent(this, RefillOrdersActivity::class.java))
        }

        // Pharmacies Card
        binding.pharmaciesCard?.setOnClickListener {
            startActivity(Intent(this, PharmacyListActivity::class.java))
        }
    }

    private fun observeViewModel() {
        profileViewModel.currentUser.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    // Show loading if needed
                }
                is Resource.Success -> {
                    updateUserUI(result.data)
                }
                is Resource.Error -> {
                    if (result.message?.contains("Not logged in") == true) {
                        navigateToLogin()
                    } else {
                        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        medicineViewModel.medicines.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    // Show loading if needed
                }
                is Resource.Success -> {
                    updateScheduleCard(result.data)
                    updateLowStockAlert(result.data)
                }
                is Resource.Error -> {
                    // Show error or empty state
                    updateScheduleCard(emptyList())
                }
            }
        }

        // ── Vital alert badge on Health Insights card ──
        healthLogViewModel.alertSummary.observe(this) { summary ->
            updateAlertBadge(summary)
        }

        // ── Health logs for risk score ──
        healthLogViewModel.healthLogs.observe(this) { result ->
            if (result is Resource.Success) {
                cachedLogs = result.data
                updateRiskScoreCard()
            }
        }

        // ── Medicines for risk score ──
        medicineViewModel.medicines.observe(this) { result ->
            if (result is Resource.Success) {
                cachedMedicines = result.data
                updateRiskScoreCard()
            }
        }

        // ── Follow-up date banner ──
        recommendationsViewModel.followUps.observe(this) { result ->
            if (result is Resource.Success) {
                updateFollowUpBanner(result.data)
            }
        }
        recommendationsViewModel.loadUpcomingFollowUps()

        // ── Unread message badge ──
        chatListViewModel.totalUnread.observe(this) { count ->
            updateChatUnreadBadge(count)
        }
    }

    /**
     * Computes and displays a mini risk score on the home dashboard.
     */
    private fun updateRiskScoreCard() {
        try {
            val recentLogs = HealthAnalytics.getLogsForLastDays(cachedLogs, 30)
            val adherence = HealthAnalytics.calculateTodayAdherence(cachedMedicines)

            val riskResult = RiskScoreEngine.calculateRiskScore(
                logs = recentLogs,
                medicines = cachedMedicines,
                adherencePercentage = adherence.adherencePercentage
            )

            binding.riskScoreCard.visibility = View.VISIBLE
            binding.riskMiniScore.text = riskResult.overallScore.toString()
            binding.riskMiniGauge.setProgressCompat(riskResult.overallScore, true)

            val (label, badgeColor, gaugeColor) = when (riskResult.category) {
                RiskScoreEngine.RiskCategory.LOW -> Triple("Low Risk", R.color.medicine_green, R.color.medicine_green)
                RiskScoreEngine.RiskCategory.MODERATE -> Triple("Moderate", R.color.warning, R.color.warning)
                RiskScoreEngine.RiskCategory.HIGH -> Triple("High Risk", R.color.medicine_orange, R.color.medicine_orange)
                RiskScoreEngine.RiskCategory.CRITICAL -> Triple("Critical", R.color.medicine_red, R.color.medicine_red)
            }

            binding.riskBadge.text = label
            binding.riskBadge.backgroundTintList =
                android.content.res.ColorStateList.valueOf(getColor(badgeColor))
            binding.riskMiniGauge.setIndicatorColor(getColor(gaugeColor))

            val factorCount = riskResult.contributingFactors.size
            binding.riskSummaryText.text = when {
                factorCount == 0 -> "All clear — tap for details"
                factorCount == 1 -> "1 factor detected — tap for details"
                else -> "$factorCount factors detected — tap for details"
            }
        } catch (_: Exception) {
            binding.riskScoreCard.visibility = View.GONE
        }
    }

    /**
     * Shows a colored alert badge on the Health Insights card
     * based on the latest vital readings evaluation.
     */
    private fun updateAlertBadge(summary: VitalAlertEngine.AlertSummary) {
        try {
            when (summary.overallSeverity) {
                VitalAlertEngine.AlertSeverity.CRITICAL -> {
                    binding.alertBadge.visibility = View.VISIBLE
                    binding.alertBadgeText.text = "Critical"
                    binding.alertBadge.setBackgroundResource(R.drawable.bg_badge_alert)
                    binding.alertBadge.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(getColor(R.color.medicine_red))
                }
                VitalAlertEngine.AlertSeverity.WARNING -> {
                    binding.alertBadge.visibility = View.VISIBLE
                    binding.alertBadgeText.text = "Warning"
                    binding.alertBadge.setBackgroundResource(R.drawable.bg_badge_alert)
                    binding.alertBadge.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(getColor(R.color.medicine_orange))
                }
                VitalAlertEngine.AlertSeverity.NORMAL -> {
                    binding.alertBadge.visibility = View.GONE
                }
            }
        } catch (_: Exception) {
            // Badge views may not exist in current layout
        }
    }

    /**
     * Updates the unread badge on the Messages card.
     */
    private fun updateChatUnreadBadge(count: Int) {
        try {
            val badge = binding.chatUnreadBadge
            if (count > 0) {
                badge?.visibility = View.VISIBLE
                badge?.text = if (count > 99) "99+" else count.toString()
            } else {
                badge?.visibility = View.GONE
            }
        } catch (_: Exception) {
            // Badge view may not exist in current layout version
        }
    }

    /**
     * Shows the next follow-up date on the dashboard if available.
     */
    private fun updateFollowUpBanner(followUps: List<ClinicalDecision>) {
        try {
            val next = followUps.firstOrNull() // Already sorted by followUpDate ASC
            if (next?.followUpDate != null) {
                binding.followUpBanner?.visibility = View.VISIBLE
                val dateFormat = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
                binding.followUpDateText?.text = dateFormat.format(next.followUpDate)
                binding.followUpDoctorText?.text = "Dr. ${next.doctorName}"
                if (next.followUpInstructions.isNotEmpty()) {
                    binding.followUpInstructionsText?.visibility = View.VISIBLE
                    binding.followUpInstructionsText?.text = next.followUpInstructions
                } else {
                    binding.followUpInstructionsText?.visibility = View.GONE
                }
            } else {
                binding.followUpBanner?.visibility = View.GONE
            }
        } catch (_: Exception) {
            // Follow-up views may not exist in current layout version
        }
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> getString(R.string.greeting_morning)
            hour < 17 -> getString(R.string.greeting_afternoon)
            else -> getString(R.string.greeting_evening)
        }
        binding.greetingText.text = "$greeting 👋"
    }

    private fun updateUserUI(user: User) {
        binding.userNameText.text = user.displayName.ifEmpty { "User" }

        if (user.profileImageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(user.profileImageUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .circleCrop()
                .into(binding.profileImage)
        }
    }

    private fun updateScheduleCard(medicines: List<Medicine>) {
        // Filter active medicines only
        val activeMedicines = medicines.filter { it.isActive }

        if (activeMedicines.isEmpty()) {
            binding.nextMedicineName.text = getString(R.string.no_medicines_scheduled)
            binding.nextMedicineTime.text = "—"
            binding.progressText.text = "0/0 taken"
            binding.medicineProgress.progress = 0
            return
        }

        // Get current time
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTimeMinutes = currentHour * 60 + currentMinute

        // Find the next upcoming medicine
        var nextMedicine: Medicine? = null
        var nextTime: String? = null
        var smallestDiff = Int.MAX_VALUE

        for (medicine in activeMedicines) {
            for (timeStr in medicine.reminderTimes) {
                val parts = timeStr.split(":")
                if (parts.size == 2) {
                    val hour = parts[0].toIntOrNull() ?: continue
                    val minute = parts[1].toIntOrNull() ?: continue
                    val timeMinutes = hour * 60 + minute

                    // Calculate difference (considering next day if time has passed)
                    val diff = if (timeMinutes > currentTimeMinutes) {
                        timeMinutes - currentTimeMinutes
                    } else {
                        (24 * 60) - currentTimeMinutes + timeMinutes
                    }

                    if (diff < smallestDiff) {
                        smallestDiff = diff
                        nextMedicine = medicine
                        nextTime = timeStr
                    }
                }
            }
        }

        if (nextMedicine != null && nextTime != null) {
            binding.nextMedicineName.text = nextMedicine.name
            binding.nextMedicineTime.text = formatTime(nextTime)
        } else {
            binding.nextMedicineName.text = getString(R.string.no_medicines_scheduled)
            binding.nextMedicineTime.text = "—"
        }

        // Calculate today's progress
        // Count total reminders for today and how many have passed
        var totalReminders = 0
        var passedReminders = 0

        for (medicine in activeMedicines) {
            for (timeStr in medicine.reminderTimes) {
                totalReminders++
                val parts = timeStr.split(":")
                if (parts.size == 2) {
                    val hour = parts[0].toIntOrNull() ?: continue
                    val minute = parts[1].toIntOrNull() ?: continue
                    val timeMinutes = hour * 60 + minute
                    if (timeMinutes <= currentTimeMinutes) {
                        passedReminders++
                    }
                }
            }
        }

        binding.progressText.text = "$passedReminders/$totalReminders taken"
        binding.medicineProgress.max = if (totalReminders > 0) totalReminders else 1
        binding.medicineProgress.progress = passedReminders
    }

    private fun formatTime(time24: String): String {
        return try {
            val inputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = inputFormat.parse(time24)
            date?.let { outputFormat.format(it) } ?: time24
        } catch (e: Exception) {
            time24
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * Show/hide the low stock alert banner based on medicine stock levels.
     */
    private fun updateLowStockAlert(medicines: List<Medicine>) {
        val lowStockMeds = medicines.filter { it.isActive && it.isLowStock }
        if (lowStockMeds.isNotEmpty()) {
            binding.lowStockAlertCard.visibility = View.VISIBLE
            val count = lowStockMeds.size
            binding.lowStockAlertText.text = "$count medicine${if (count > 1) "s" else ""} running low on stock"

            binding.lowStockAlertCard.setOnClickListener {
                startActivity(Intent(this, RefillOrdersActivity::class.java))
            }
        } else {
            binding.lowStockAlertCard.visibility = View.GONE
        }
    }
}
