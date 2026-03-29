package com.meditrack.app.ui.pharmacy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.meditrack.app.R
import com.meditrack.app.data.repository.StockForecastRepository
import com.meditrack.app.data.model.OrderStatus
import com.meditrack.app.data.model.RefillOrder
import com.meditrack.app.data.model.Resource
import com.meditrack.app.databinding.ActivityPharmacyDashboardRedesignedBinding
import com.meditrack.app.domain.usecase.AcceptOrderResult
import com.meditrack.app.domain.usecase.VerifyAndAcceptOrderUseCase
import com.meditrack.app.ui.auth.LoginActivity
import com.meditrack.app.ui.profile.ProfileActivity
import com.meditrack.app.util.CallUtils
import com.meditrack.app.util.PharmacyNotificationHelper
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PharmacyDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPharmacyDashboardRedesignedBinding
    val dashboardViewModel: PharmacyDashboardViewModel by viewModels()
    @Inject lateinit var verifyAndAcceptOrderUseCase: VerifyAndAcceptOrderUseCase
    @Inject lateinit var stockForecastRepository: StockForecastRepository
    private lateinit var tabAdapter: PharmacyTabAdapter
    private var activeStatFilter: String? = null

    private val tabTitles = arrayOf("Orders", "Inventory", "Analytics")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPharmacyDashboardRedesignedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()
        setupQuickStatsFilters()
        setupBottomNavigation()
        setupClickListeners()
        displayStockForecastCard()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        dashboardViewModel.loadPharmacyProfile(forceRefresh = true)
    }

    // ── Tab Setup ─────────────────────────────────────────────

    private fun setupTabs() {
        tabAdapter = PharmacyTabAdapter(this)
        binding.viewPager.adapter = tabAdapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    // ── Quick Stats Clickable Filters ─────────────────────────

    private fun setupQuickStatsFilters() {
        binding.statPendingCard.setOnClickListener { toggleFilter("PENDING") }
        binding.statPreparingCard.setOnClickListener { toggleFilter("PREPARING") }
        binding.statReadyCard.setOnClickListener { toggleFilter("SHIPPED") }
        binding.statDeliveredCard.setOnClickListener { toggleFilter("DELIVERED") }
    }

    private fun toggleFilter(status: String) {
        activeStatFilter = if (activeStatFilter == status) null else status
        // Switch to Orders tab
        binding.viewPager.currentItem = 0
        tabAdapter.getOrdersFragment().setStatusFilter(activeStatFilter)

        // Highlight the active filter card
        updateStatCardSelection()
    }

    private fun updateStatCardSelection() {
        val elevation0 = 0f
        val elevationActive = 4f
        binding.statPendingCard.cardElevation = if (activeStatFilter == "PENDING") elevationActive else elevation0
        binding.statPreparingCard.cardElevation = if (activeStatFilter == "PREPARING") elevationActive else elevation0
        binding.statReadyCard.cardElevation = if (activeStatFilter == "SHIPPED") elevationActive else elevation0
        binding.statDeliveredCard.cardElevation = if (activeStatFilter == "DELIVERED") elevationActive else elevation0
    }

    // ── Bottom Navigation ─────────────────────────────────────

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_pharmacy_dashboard -> {
                    binding.viewPager.currentItem = 0
                    true
                }
                R.id.nav_pharmacy_orders -> {
                    binding.viewPager.currentItem = 0
                    true
                }
                R.id.nav_pharmacy_inventory -> {
                    binding.viewPager.currentItem = 1
                    true
                }
                R.id.nav_pharmacy_transactions -> {
                    val pharmacyId = dashboardViewModel.getPharmacyId()
                    if (pharmacyId != null) {
                        startActivity(
                            Intent(this, TransactionHistoryActivity::class.java)
                                .putExtra("pharmacyId", pharmacyId)
                        )
                    } else {
                        Toast.makeText(this, "Set up pharmacy profile first", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.nav_pharmacy_profile -> {
                    val pharmacyId = dashboardViewModel.getPharmacyId()
                    if (pharmacyId != null) {
                        startActivity(
                            Intent(this, PharmacyProfileSetupActivity::class.java)
                                .putExtra("pharmacyId", pharmacyId)
                        )
                    } else {
                        startActivity(Intent(this, ProfileActivity::class.java))
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ── Click Listeners ───────────────────────────────────────

    private fun setupClickListeners() {
        binding.signOutButton.setOnClickListener {
            dashboardViewModel.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.setupProfileCard.setOnClickListener {
            startActivity(Intent(this, PharmacyProfileSetupActivity::class.java))
        }

        binding.notificationButton.setOnClickListener {
            // Navigate to Orders tab filtered by pending orders
            binding.viewPager.currentItem = 0
            activeStatFilter = "PENDING"
            tabAdapter.getOrdersFragment().setStatusFilter("PENDING")
            updateStatCardSelection()
            Toast.makeText(this, "Showing pending orders", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Stock Forecast Display ───────────────────────────────

    /**
     * Display ML-based stock forecast card with 14-day predictions.
     *
     * Shows:
     * - Next 7 days predicted demand with confidence intervals
     * - Model accuracy metrics (MAE, RMSE)
     * - Re-order recommendation based on current inventory
     */
    private fun displayStockForecastCard() {
        try {
            val bundle = stockForecastRepository.getForecastBundle()
            val forecastText = StringBuilder()

            forecastText.append("14-Day Stock Forecast (ARIMA Model)\n")
            forecastText.append("Model Accuracy: MAE=${bundle.metrics.mae.toInt()} units\n")
            forecastText.append("─".repeat(40)).append("\n")

            // Display next 7 days
            bundle.forecasts.take(7).forEach { forecast ->
                forecastText.append(
                    "Day ${forecast.dayAhead}: ${forecast.predictedUnits} units " +
                    "[${forecast.confidenceMin}, ${forecast.confidenceMax}]\n"
                )
            }

            // Log to Logcat for development/debug
            android.util.Log.d("PharmacyDashboard", forecastText.toString())

            // Display as toast for quick feedback (production: would use CardView in layout)
            Toast.makeText(
                this,
                "Stock forecast updated: Avg=${bundle.forecasts.map { it.predictedUnits }.average().toInt()} units/day",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            android.util.Log.e("PharmacyDashboard", "Error loading stock forecast", e)
        }
    }

    // ── Order Actions (called from OrdersFragment) ────────────

    fun handleAcceptOrder(order: RefillOrder) {
        lifecycleScope.launch {
            when (val result = verifyAndAcceptOrderUseCase.execute(order)) {
                is AcceptOrderResult.Accepted -> {
                    Toast.makeText(this@PharmacyDashboardActivity, result.message, Toast.LENGTH_SHORT).show()
                }
                is AcceptOrderResult.PrescriptionInvalid -> {
                    Toast.makeText(this@PharmacyDashboardActivity, "Prescription invalid: ${result.reason}", Toast.LENGTH_LONG).show()
                }
                is AcceptOrderResult.Error -> {
                    Toast.makeText(this@PharmacyDashboardActivity, result.message, Toast.LENGTH_LONG).show()
                }
                is AcceptOrderResult.StatusAdvanced -> {
                    Toast.makeText(this@PharmacyDashboardActivity, "Order updated", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun handleRejectOrder(order: RefillOrder) {
        dashboardViewModel.updateOrderStatus(order.id, OrderStatus.CANCELLED.name)
        PharmacyNotificationHelper.notifyOrderCancelled(this, order.medicineName, order.id)
        Toast.makeText(this, "Order rejected", Toast.LENGTH_SHORT).show()
    }

    fun handleCallPatient(order: RefillOrder) {
        val fallbackPhone = order.deliveryAddress?.contactPhone.orEmpty()
        val patientName = dashboardViewModel.patientNames.value?.get(order.userId) ?: "Patient"

        FirebaseFirestore.getInstance().collection("users")
            .document(order.userId)
            .get()
            .addOnSuccessListener { doc ->
                val userPhone = doc.getString("phoneNumber").orEmpty()
                val phoneToCall = if (userPhone.isNotBlank()) userPhone else fallbackPhone
                CallUtils.dialPhoneNumber(this, phoneToCall, patientName)
            }
            .addOnFailureListener {
                // Fall back to delivery contact phone if user lookup fails.
                CallUtils.dialPhoneNumber(this, fallbackPhone, patientName)
            }
    }

    // ── Observe ViewModel ─────────────────────────────────────

    private fun observeViewModel() {
        dashboardViewModel.pharmacy.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.setupProfileCard.visibility = View.GONE
                    setDashboardSectionsVisible(false)
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    if (result.data == null) {
                        binding.setupProfileCard.visibility = View.VISIBLE
                        setDashboardSectionsVisible(false)
                        binding.emptyStateText.visibility = View.VISIBLE
                        binding.emptyStateText.text = getString(R.string.setup_pharmacy_subtitle)
                    } else {
                        binding.setupProfileCard.visibility = View.GONE
                        setDashboardSectionsVisible(true)
                    }
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.setupProfileCard.visibility = View.GONE
                    setDashboardSectionsVisible(false)
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        // Quick Stats counts
        dashboardViewModel.orderCounts.observe(this) { counts ->
            binding.pendingCount.text = (counts["pending"] ?: 0).toString()
            binding.preparingCount.text = (counts["preparing"] ?: 0).toString()
            binding.readyCount.text = (counts["ready"] ?: 0).toString()
            binding.deliveredTodayCount.text = (counts["deliveredToday"] ?: 0).toString()

            // Update notification badge with pending count
            val pending = counts["pending"] ?: 0
            if (pending > 0) {
                binding.notificationBadge.text = if (pending > 9) "9+" else pending.toString()
                binding.notificationBadge.visibility = View.VISIBLE
            } else {
                binding.notificationBadge.visibility = View.GONE
            }
        }

        // Show error if orders fail to load
        dashboardViewModel.orders.observe(this) { result ->
            if (result is Resource.Error) {
                Toast.makeText(this, "Orders: ${result.message}", Toast.LENGTH_LONG).show()
            }
        }

        // Update error
        dashboardViewModel.updateError.observe(this) { error ->
            if (error != null) {
                Toast.makeText(this, "Update failed: $error", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setDashboardSectionsVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        binding.quickStatsLayout.visibility = visibility
        binding.tabLayout.visibility = visibility
        binding.viewPager.visibility = visibility
        binding.bottomNavigation.visibility = visibility
        binding.emptyStateText.visibility = if (visible) View.GONE else binding.emptyStateText.visibility
    }
}

