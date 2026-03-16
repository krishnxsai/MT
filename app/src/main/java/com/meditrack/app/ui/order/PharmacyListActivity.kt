package com.meditrack.app.ui.order

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.meditrack.app.R
import com.meditrack.app.data.model.Pharmacy
import com.meditrack.app.data.model.Resource
import com.meditrack.app.databinding.ActivityPharmacyListBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity to browse and select a pharmacy for a refill order.
 * Also shows digital prescription verification status.
 *
 * Extras (input):
 *   EXTRA_MEDICINE_NAME  — name of the medicine being refilled
 *   EXTRA_MEDICINE_ID    — ID for prescription lookup
 *   EXTRA_PRESCRIPTION_VALID — pre-computed prescription validity
 *
 * Result:
 *   RESULT_PHARMACY_ID, RESULT_PHARMACY_NAME
 */
@AndroidEntryPoint
class PharmacyListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
        const val EXTRA_MEDICINE_ID = "extra_medicine_id"
        const val EXTRA_PRESCRIPTION_VALID = "extra_prescription_valid"
        const val EXTRA_PRESCRIPTION_MSG = "extra_prescription_msg"

        const val RESULT_PHARMACY_ID = "result_pharmacy_id"
        const val RESULT_PHARMACY_NAME = "result_pharmacy_name"
    }

    private lateinit var binding: ActivityPharmacyListBinding
    private val viewModel: OrderViewModel by viewModels()
    private lateinit var adapter: PharmacyAdapter

    private var prescriptionValid = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPharmacyListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prescriptionValid = intent.getBooleanExtra(EXTRA_PRESCRIPTION_VALID, false)
        val prescriptionMsg = intent.getStringExtra(EXTRA_PRESCRIPTION_MSG) ?: ""
        val medicineName = intent.getStringExtra(EXTRA_MEDICINE_NAME) ?: ""

        binding.toolbar.title = if (medicineName.isNotEmpty()) "Pharmacy for $medicineName" else "Select Pharmacy"
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupPrescriptionBanner(prescriptionMsg)
        setupRecyclerView()
        observeViewModel()
        viewModel.loadPharmacies()
    }

    private fun setupPrescriptionBanner(message: String) {
        if (message.isEmpty()) {
            binding.prescriptionBanner.visibility = View.GONE
            return
        }

        binding.prescriptionBanner.visibility = View.VISIBLE
        binding.prescriptionStatusText.text = message

        if (prescriptionValid) {
            binding.prescriptionBanner.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.success_container)
            )
            binding.prescriptionIcon.setImageResource(R.drawable.ic_check)
            binding.prescriptionIcon.setColorFilter(ContextCompat.getColor(this, R.color.success))
            binding.prescriptionStatusText.setTextColor(
                ContextCompat.getColor(this, R.color.on_success_container)
            )
        } else {
            binding.prescriptionBanner.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.warning_container)
            )
            binding.prescriptionIcon.setImageResource(R.drawable.ic_warning)
            binding.prescriptionIcon.setColorFilter(ContextCompat.getColor(this, R.color.warning))
            binding.prescriptionStatusText.setTextColor(
                ContextCompat.getColor(this, R.color.on_warning_container)
            )
        }
    }

    private fun setupRecyclerView() {
        adapter = PharmacyAdapter { pharmacy ->
            selectPharmacy(pharmacy)
        }
        binding.pharmacyRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PharmacyListActivity)
            adapter = this@PharmacyListActivity.adapter
        }
    }

    private fun observeViewModel() {
        viewModel.pharmacies.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    if (result.data.isEmpty()) {
                        binding.emptyStateContainer.visibility = View.VISIBLE
                        binding.pharmacyRecyclerView.visibility = View.GONE
                    } else {
                        binding.emptyStateContainer.visibility = View.GONE
                        binding.pharmacyRecyclerView.visibility = View.VISIBLE
                        adapter.submitList(result.data)
                    }
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun selectPharmacy(pharmacy: Pharmacy) {
        val result = Intent().apply {
            putExtra(RESULT_PHARMACY_ID, pharmacy.id)
            putExtra(RESULT_PHARMACY_NAME, pharmacy.name)
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}

