package com.example.meditrack.ui.pharmacy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meditrack.data.model.OrderStatus
import com.example.meditrack.data.model.RefillOrder
import com.example.meditrack.data.model.Resource
import com.example.meditrack.databinding.ActivityPharmacyDashboardBinding
import com.example.meditrack.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth

class PharmacyDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPharmacyDashboardBinding
    private val viewModel: PharmacyDashboardViewModel by viewModels()
    private lateinit var orderAdapter: PharmacyOrderAdapter

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
        val nextStatus = when (order.status) {
            OrderStatus.PENDING -> OrderStatus.CONFIRMED.name
            OrderStatus.CONFIRMED -> OrderStatus.SHIPPED.name
            OrderStatus.SHIPPED -> OrderStatus.DELIVERED.name
            else -> return
        }
        viewModel.updateOrderStatus(order.id, nextStatus)
        Toast.makeText(this, "Order updated", Toast.LENGTH_SHORT).show()
    }

    private fun handleReject(order: RefillOrder) {
        viewModel.updateOrderStatus(order.id, OrderStatus.CANCELLED.name)
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
    }

    private fun observeViewModel() {
        viewModel.pharmacy.observe(this) { result ->
            when (result) {
                is Resource.Loading -> { /* loading handled by orders */ }
                is Resource.Success -> {
                    if (result.data == null) {
                        // No pharmacy profile linked yet
                        binding.setupProfileCard.visibility = View.VISIBLE
                        binding.statsLabel.visibility = View.GONE
                    } else {
                        binding.setupProfileCard.visibility = View.GONE
                        binding.statsLabel.visibility = View.VISIBLE
                    }
                }
                is Resource.Error -> {
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
    }
}

