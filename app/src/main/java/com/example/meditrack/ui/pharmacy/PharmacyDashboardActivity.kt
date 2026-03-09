package com.example.meditrack.ui.pharmacy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meditrack.R
import com.example.meditrack.data.model.Medicine
import com.example.meditrack.data.model.OrderStatus
import com.example.meditrack.data.model.RefillOrder
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.repository.PharmacyRepository
import com.example.meditrack.databinding.ActivityPharmacyDashboardBinding
import com.example.meditrack.ui.auth.LoginActivity
import com.example.meditrack.util.PharmacyNotificationHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class PharmacyDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPharmacyDashboardBinding
    private val viewModel: PharmacyDashboardViewModel by viewModels()
    private lateinit var orderAdapter: PharmacyOrderAdapter
    private val pharmacyRepository = PharmacyRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPharmacyDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadPharmacyProfile()
    }

    private fun setupRecyclerView() {
        orderAdapter = PharmacyOrderAdapter(
            onAccept = { order -> handleAccept(order) },
            onReject = { order -> handleReject(order) }
        )
        binding.ordersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PharmacyDashboardActivity)
            adapter = orderAdapter
        }
    }

    private fun handleAccept(order: RefillOrder) {
        if (order.status == OrderStatus.PENDING && order.prescriptionId.isNotEmpty()) {
            // Verify prescription before confirming PENDING orders
            lifecycleScope.launch {
                val medicine = Medicine(
                    id = order.medicineId,
                    name = order.medicineName,
                    prescriptionId = order.prescriptionId,
                    prescribedByDoctor = true
                )
                when (val result = pharmacyRepository.verifyPrescription(medicine)) {
                    is Resource.Success -> {
                        if (result.data.isValid) {
                            viewModel.updateOrderStatus(order.id, OrderStatus.CONFIRMED.name)
                            Toast.makeText(this@PharmacyDashboardActivity, "Prescription verified — order confirmed", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@PharmacyDashboardActivity, "Prescription invalid: ${result.data.reason}", Toast.LENGTH_LONG).show()
                        }
                    }
                    is Resource.Error -> {
                        Toast.makeText(this@PharmacyDashboardActivity, "Verification failed: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                    is Resource.Loading -> { /* ignore */ }
                }
            }
            return
        }

        val nextStatus = when (order.status) {
            OrderStatus.PENDING -> OrderStatus.CONFIRMED.name
            OrderStatus.CONFIRMED -> OrderStatus.PREPARING.name
            OrderStatus.PREPARING -> OrderStatus.SHIPPED.name
            OrderStatus.SHIPPED -> OrderStatus.DELIVERED.name
            else -> return
        }
        viewModel.updateOrderStatus(order.id, nextStatus)
        Toast.makeText(this, "Order updated", Toast.LENGTH_SHORT).show()
    }

    private fun handleReject(order: RefillOrder) {
        viewModel.updateOrderStatus(order.id, OrderStatus.CANCELLED.name)
        PharmacyNotificationHelper.notifyOrderCancelled(this, order.medicineName, order.id)
        Toast.makeText(this, "Order rejected", Toast.LENGTH_SHORT).show()
    }

    private fun setupClickListeners() {
        binding.signOutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.setupProfileCard.setOnClickListener {
            startActivity(Intent(this, PharmacyProfileSetupActivity::class.java))
        }

        binding.inventoryButton.setOnClickListener {
            val pharmacyId = viewModel.getPharmacyId()
            if (pharmacyId != null) {
                val intent = Intent(this, InventoryActivity::class.java)
                intent.putExtra("pharmacyId", pharmacyId)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Set up pharmacy profile first", Toast.LENGTH_SHORT).show()
            }
        }

        binding.transactionsButton.setOnClickListener {
            val pharmacyId = viewModel.getPharmacyId()
            if (pharmacyId != null) {
                val intent = Intent(this, TransactionHistoryActivity::class.java)
                intent.putExtra("pharmacyId", pharmacyId)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Set up pharmacy profile first", Toast.LENGTH_SHORT).show()
            }
        }

        binding.editProfileButton.setOnClickListener {
            val pharmacyId = viewModel.getPharmacyId()
            if (pharmacyId != null) {
                val intent = Intent(this, PharmacyProfileSetupActivity::class.java)
                intent.putExtra("pharmacyId", pharmacyId)
                startActivity(intent)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.pharmacy.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.setupProfileCard.visibility = View.GONE
                    binding.statsLabel.visibility = View.GONE
                    binding.statsCardsLayout.visibility = View.GONE
                    binding.analyticsLayout.visibility = View.GONE
                    binding.quickActionsLayout.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    if (result.data == null) {
                        // No pharmacy profile linked yet
                        binding.setupProfileCard.visibility = View.VISIBLE
                        binding.statsLabel.visibility = View.GONE
                        binding.statsCardsLayout.visibility = View.GONE
                        binding.analyticsLayout.visibility = View.GONE
                        binding.quickActionsLayout.visibility = View.GONE
                        binding.emptyStateText.visibility = View.VISIBLE
                        binding.emptyStateText.text = getString(R.string.setup_pharmacy_subtitle)
                        binding.ordersRecyclerView.visibility = View.GONE
                    } else {
                        binding.setupProfileCard.visibility = View.GONE
                        binding.statsLabel.visibility = View.VISIBLE
                        binding.statsCardsLayout.visibility = View.VISIBLE
                        binding.analyticsLayout.visibility = View.VISIBLE
                        binding.quickActionsLayout.visibility = View.VISIBLE
                    }
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.setupProfileCard.visibility = View.GONE
                    binding.statsLabel.visibility = View.GONE
                    binding.statsCardsLayout.visibility = View.GONE
                    binding.analyticsLayout.visibility = View.GONE
                    binding.quickActionsLayout.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.orders.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.emptyStateText.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    if (result.data.isEmpty()) {
                        binding.emptyStateText.visibility = View.VISIBLE
                        binding.ordersRecyclerView.visibility = View.GONE
                    } else {
                        binding.emptyStateText.visibility = View.GONE
                        binding.ordersRecyclerView.visibility = View.VISIBLE
                        orderAdapter.submitList(result.data)
                    }
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.orderCounts.observe(this) { counts ->
            binding.totalOrdersCount.text = (counts["total"] ?: 0).toString()
            binding.pendingOrdersCount.text = (counts["pending"] ?: 0).toString()
            binding.completedOrdersCount.text = (counts["completed"] ?: 0).toString()
        }

        viewModel.dailyRevenue.observe(this) { revenue ->
            binding.dailyRevenueText.text = String.format("$%.2f", revenue)
        }

        viewModel.topMedicines.observe(this) { medicines ->
            if (medicines.isNullOrEmpty()) {
                binding.topMedicinesText.text = getString(R.string.no_data_yet)
            } else {
                binding.topMedicinesText.text = medicines.joinToString("\n") { (name, count) ->
                    "$name — $count orders"
                }
            }
        }
    }
}

