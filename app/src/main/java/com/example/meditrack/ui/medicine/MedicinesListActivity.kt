package com.example.meditrack.ui.medicine

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meditrack.R
import com.example.meditrack.alarm.AlarmScheduler
import com.example.meditrack.data.model.Medicine
import com.example.meditrack.data.model.Resource
import com.example.meditrack.databinding.ActivityMedicinesListBinding
import com.example.meditrack.ui.order.OrderViewModel
import com.example.meditrack.ui.order.PharmacyListActivity
import com.example.meditrack.ui.order.RefillOrdersActivity
import com.example.meditrack.util.overrideTransitionCompat

class MedicinesListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMedicinesListBinding
    private val viewModel: MedicineViewModel by viewModels()
    private val orderViewModel: OrderViewModel by viewModels()
    private lateinit var adapter: MedicineAdapter
    private lateinit var alarmScheduler: AlarmScheduler

    // Pending refill state (held while user picks pharmacy)
    private var pendingRefillMedicine: Medicine? = null
    private var pendingRefillQty: Int = 0
    private var pendingRefillNotes: String = ""

    private val pharmacyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pharmacyId = result.data?.getStringExtra(PharmacyListActivity.RESULT_PHARMACY_ID) ?: ""
            val pharmacyName = result.data?.getStringExtra(PharmacyListActivity.RESULT_PHARMACY_NAME) ?: ""
            val medicine = pendingRefillMedicine
            if (medicine != null && pharmacyId.isNotEmpty()) {
                orderViewModel.placeRefillOrderWithPharmacy(
                    medicine, pendingRefillQty, pharmacyId, pharmacyName, pendingRefillNotes
                )
            }
            pendingRefillMedicine = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMedicinesListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alarmScheduler = AlarmScheduler(this)

        setupToolbar()
        setupBottomNavigation()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigation?.setOnItemSelectedListener(null)
        binding.bottomNavigation?.selectedItemId = R.id.nav_medicines
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation?.setOnItemSelectedListener(null)
        binding.bottomNavigation?.selectedItemId = R.id.nav_medicines
        binding.bottomNavigation?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, com.example.meditrack.ui.main.HomeDashboardActivity::class.java))
                    overrideTransitionCompat(0, 0)
                    finish()
                    true
                }
                R.id.nav_medicines -> true // Already here
                R.id.nav_health_logs -> {
                    startActivity(Intent(this, com.example.meditrack.ui.healthlog.HealthLogListActivity::class.java))
                    overrideTransitionCompat(0, 0)
                    finish()
                    true
                }
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
        try { binding.toolbar.setNavigationOnClickListener { finish() } } catch (_: Exception) {}
    }

    private fun setupRecyclerView() {
        adapter = MedicineAdapter(
            onItemClick = { medicine ->
                showMedicineOptions(medicine)
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

        // Refill Orders shortcut
        binding.refillOrdersButton.setOnClickListener {
            startActivity(Intent(this, RefillOrdersActivity::class.java))
        }

        try { binding.addMedicineBottomButton?.setOnClickListener { navigateToAddMedicine() } } catch (_: Exception) {}
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

    /**
     * Show options menu when tapping a medicine card:
     * Edit, Order Refill, Stock Tracking, View Orders, Delete
     */
    private fun showMedicineOptions(medicine: Medicine) {
        val options = mutableListOf("Edit Medicine")

        // Always show Order Refill — primary action
        options.add("Order Refill")

        if (medicine.isRefillTrackingEnabled) {
            options.add("Update Stock")
            options.add("Disable Stock Tracking")
        } else {
            options.add("Enable Stock Tracking")
        }

        options.add("View Refill Orders")
        options.add("Delete")

        AlertDialog.Builder(this)
            .setTitle(medicine.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Edit Medicine" -> navigateToEditMedicine(medicine)
                    "Order Refill" -> showRefillOrderDialog(medicine)
                    "Enable Stock Tracking" -> showStockTrackingDialog(medicine)
                    "Update Stock" -> showStockTrackingDialog(medicine)
                    "View Refill Orders" -> {
                        startActivity(Intent(this, RefillOrdersActivity::class.java))
                    }
                    "Disable Stock Tracking" -> {
                        orderViewModel.disableRefillTracking(medicine.id)
                        Toast.makeText(this, "Stock tracking disabled", Toast.LENGTH_SHORT).show()
                    }
                    "Delete" -> showDeleteConfirmation(medicine)
                }
            }
            .show()
    }

    /**
     * Show dialog to set up or update stock tracking.
     */
    private fun showStockTrackingDialog(medicine: Medicine) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_stock_tracking, null)
        val currentQtyInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.currentQtyInput)
        val totalQtyInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.totalQtyInput)
        val thresholdInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.thresholdInput)
        val reminderSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.reminderSwitch)

        // Pre-fill if already tracking
        if (medicine.isRefillTrackingEnabled) {
            currentQtyInput.setText(medicine.currentQuantity.toString())
            totalQtyInput.setText(if (medicine.totalQuantity > 0) medicine.totalQuantity.toString() else "30")
            thresholdInput.setText(medicine.lowStockThreshold.toString())
            reminderSwitch.isChecked = medicine.refillReminderEnabled
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val currentQty = currentQtyInput.text?.toString()?.toIntOrNull() ?: 0
                val totalQty = totalQtyInput.text?.toString()?.toIntOrNull() ?: 30
                val threshold = thresholdInput.text?.toString()?.toIntOrNull() ?: 5
                val reminder = reminderSwitch.isChecked

                orderViewModel.enableRefillTracking(medicine.id, currentQty, totalQty, threshold, reminder)
                Toast.makeText(this, "Stock tracking updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show dialog to place a refill order.
     * Includes prescription verification check and pharmacy selection.
     */
    private fun showRefillOrderDialog(medicine: Medicine) {
        // First verify prescription in background
        orderViewModel.verifyPrescription(medicine)

        val dialogView = layoutInflater.inflate(R.layout.dialog_refill_order, null)
        val medicineInfoText = dialogView.findViewById<android.widget.TextView>(R.id.medicineInfoText)
        val stockInfoCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.stockInfoCard)
        val currentStockText = dialogView.findViewById<android.widget.TextView>(R.id.currentStockText)
        val quantityInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.quantityInput)
        val notesInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.notesInput)

        medicineInfoText.text = "${medicine.dosage} ${medicine.unit}"

        if (medicine.isRefillTrackingEnabled) {
            stockInfoCard.visibility = View.VISIBLE
            currentStockText.text = "Current stock: ${medicine.currentQuantity} remaining"
        }

        // Default quantity to totalQuantity if set
        if (medicine.totalQuantity > 0) {
            quantityInput.setText(medicine.totalQuantity.toString())
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Place Order") { _, _ ->
                val qty = quantityInput.text?.toString()?.toIntOrNull() ?: 0
                val notes = notesInput.text?.toString()?.trim() ?: ""

                if (qty <= 0) {
                    Toast.makeText(this, "Please enter a valid quantity", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                orderViewModel.placeRefillOrder(medicine, qty, notes)
            }
            .setNeutralButton("Choose Pharmacy") { _, _ ->
                val qty = quantityInput.text?.toString()?.toIntOrNull() ?: 0
                val notes = notesInput.text?.toString()?.trim() ?: ""

                if (qty <= 0) {
                    Toast.makeText(this, "Please enter a valid quantity", Toast.LENGTH_SHORT).show()
                    return@setNeutralButton
                }

                // Save pending state and launch pharmacy picker
                pendingRefillMedicine = medicine
                pendingRefillQty = qty
                pendingRefillNotes = notes

                val verification = orderViewModel.prescriptionVerification.value
                val isValid = (verification as? Resource.Success)?.data?.isValid ?: false
                val msg = (verification as? Resource.Success)?.data?.reason ?: ""

                val intent = Intent(this, PharmacyListActivity::class.java).apply {
                    putExtra(PharmacyListActivity.EXTRA_MEDICINE_NAME, medicine.name)
                    putExtra(PharmacyListActivity.EXTRA_MEDICINE_ID, medicine.id)
                    putExtra(PharmacyListActivity.EXTRA_PRESCRIPTION_VALID, isValid)
                    putExtra(PharmacyListActivity.EXTRA_PRESCRIPTION_MSG, msg)
                }
                pharmacyLauncher.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()

        // Observe the order result
        orderViewModel.placeOrderResult.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    Toast.makeText(this, "Refill order placed!", Toast.LENGTH_SHORT).show()
                }
                is Resource.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
                is Resource.Loading -> {}
            }
        }
    }
}

