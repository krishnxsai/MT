package com.example.meditrack.ui.healthlog

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meditrack.R
import com.example.meditrack.data.model.HealthLog
import com.example.meditrack.data.model.Resource
import com.example.meditrack.databinding.ActivityHealthLogListBinding
import com.example.meditrack.util.overrideTransitionCompat

class HealthLogListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHealthLogListBinding
    private val viewModel: HealthLogViewModel by viewModels()
    private lateinit var adapter: HealthLogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHealthLogListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupBottomNavigation()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigation?.setOnItemSelectedListener(null)
        binding.bottomNavigation?.selectedItemId = R.id.nav_health_logs
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation?.setOnItemSelectedListener(null)
        binding.bottomNavigation?.selectedItemId = R.id.nav_health_logs
        binding.bottomNavigation?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, com.example.meditrack.ui.main.HomeDashboardActivity::class.java))
                    overrideTransitionCompat(0, 0)
                    finish()
                    true
                }
                R.id.nav_medicines -> {
                    startActivity(Intent(this, com.example.meditrack.ui.medicine.MedicinesListActivity::class.java))
                    overrideTransitionCompat(0, 0)
                    finish()
                    true
                }
                R.id.nav_health_logs -> true // Already here
                R.id.nav_doctor -> {
                    startActivity(Intent(this, com.example.meditrack.ui.recommendations.DoctorRecommendationsActivity::class.java))
                    overrideTransitionCompat(0, 0)
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, com.example.meditrack.ui.profile.ProfileActivity::class.java))
                    overrideTransitionCompat(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupToolbar() {
        try {
            binding.toolbar.setNavigationOnClickListener { finish() }
        } catch (_: Exception) {}
        try {
            binding.backButton?.setOnClickListener { finish() }
        } catch (_: Exception) {}
    }

    private fun setupRecyclerView() {
        adapter = HealthLogAdapter(
            onItemClick = { healthLog ->
                navigateToEditHealthLog(healthLog)
            },
            onDeleteClick = { healthLog ->
                showDeleteConfirmation(healthLog)
            }
        )

        binding.healthLogsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@HealthLogListActivity)
            adapter = this@HealthLogListActivity.adapter
        }
    }

    private fun setupClickListeners() {
        binding.addHealthLogFab.setOnClickListener {
            navigateToAddHealthLog()
        }

        binding.addFirstLogButton.setOnClickListener {
            navigateToAddHealthLog()
        }
    }

    private fun observeViewModel() {
        viewModel.healthLogs.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    updateUI(result.data)
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.deleteResult.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    Toast.makeText(this, "Health log deleted", Toast.LENGTH_SHORT).show()
                    viewModel.clearDeleteResult()
                }
                is Resource.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    private fun updateUI(logs: List<HealthLog>) {
        if (logs.isEmpty()) {
            binding.emptyStateContainer.visibility = View.VISIBLE
            binding.healthLogsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateContainer.visibility = View.GONE
            binding.healthLogsRecyclerView.visibility = View.VISIBLE
            adapter.submitList(logs)
        }
    }

    private fun navigateToAddHealthLog() {
        startActivity(Intent(this, AddHealthLogActivity::class.java))
    }

    private fun navigateToEditHealthLog(healthLog: HealthLog) {
        val intent = Intent(this, AddHealthLogActivity::class.java).apply {
            putExtra(AddHealthLogActivity.EXTRA_LOG_ID, healthLog.id)
        }
        startActivity(intent)
    }

    private fun showDeleteConfirmation(healthLog: HealthLog) {
        AlertDialog.Builder(this)
            .setTitle("Delete Health Log")
            .setMessage("Are you sure you want to delete this health log?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteHealthLog(healthLog.id)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}

