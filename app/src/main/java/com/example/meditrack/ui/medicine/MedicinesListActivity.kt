package com.example.meditrack.ui.medicine

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meditrack.R
import com.example.meditrack.alarm.AlarmScheduler
import com.example.meditrack.data.model.Medicine
import com.example.meditrack.data.model.Resource
import com.example.meditrack.databinding.ActivityMedicinesListBinding

class MedicinesListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMedicinesListBinding
    private val viewModel: MedicineViewModel by viewModels()
    private lateinit var adapter: MedicineAdapter
    private lateinit var alarmScheduler: AlarmScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMedicinesListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alarmScheduler = AlarmScheduler(this)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }


    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = MedicineAdapter(
            onItemClick = { medicine ->
                navigateToEditMedicine(medicine)
            },
            onToggleClick = { medicine, isActive ->
                toggleMedicine(medicine, isActive)
            }
        )

        binding.medicinesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MedicinesListActivity)
            adapter = this@MedicinesListActivity.adapter
        }
    }

    private fun setupClickListeners() {
        binding.addMedicineFab.setOnClickListener {
            navigateToAddMedicine()
        }

        binding.addFirstMedicineButton.setOnClickListener {
            navigateToAddMedicine()
        }
    }

    private fun observeViewModel() {
        viewModel.medicines.observe(this) { result ->
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

        viewModel.toggleResult.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    // The list will auto-update via Flow
                }
                is Resource.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        viewModel.deleteMedicineResult.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    Toast.makeText(this, getString(R.string.medicine_deleted), Toast.LENGTH_SHORT).show()
                    viewModel.clearDeleteResult()
                }
                is Resource.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    private fun updateUI(medicines: List<Medicine>) {
        if (medicines.isEmpty()) {
            binding.emptyStateContainer.visibility = View.VISIBLE
            binding.medicinesRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateContainer.visibility = View.GONE
            binding.medicinesRecyclerView.visibility = View.VISIBLE
            // Create a new list to ensure DiffUtil detects changes
            adapter.submitList(medicines.toList())
        }
    }

    private fun toggleMedicine(medicine: Medicine, isActive: Boolean) {
        viewModel.toggleMedicineActive(medicine.id, isActive)

        if (isActive) {
            // Reschedule alarms
            val newAlarmIds = alarmScheduler.scheduleMedicineAlarms(medicine.copy(isActive = true))
            viewModel.updateAlarmIds(medicine.id, newAlarmIds)
            Toast.makeText(this, getString(R.string.medicine_activated), Toast.LENGTH_SHORT).show()
        } else {
            // Cancel alarms
            alarmScheduler.cancelMedicineAlarms(medicine.alarmIds)
            viewModel.updateAlarmIds(medicine.id, emptyList())
            Toast.makeText(this, getString(R.string.medicine_paused), Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToAddMedicine() {
        startActivity(Intent(this, AddMedicineActivity::class.java))
    }

    private fun navigateToEditMedicine(medicine: Medicine) {
        val intent = Intent(this, AddMedicineActivity::class.java).apply {
            putExtra(AddMedicineActivity.EXTRA_MEDICINE_ID, medicine.id)
        }
        startActivity(intent)
    }

    fun showDeleteConfirmation(medicine: Medicine) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_medicine))
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                // Cancel alarms first
                alarmScheduler.cancelMedicineAlarms(medicine.alarmIds)
                viewModel.deleteMedicine(medicine.id)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}

