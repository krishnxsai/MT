package com.example.meditrack.ui.pharmacy

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.meditrack.R
import com.example.meditrack.data.model.Pharmacy
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.repository.PharmacyRepository
import com.example.meditrack.databinding.ActivityPharmacyProfileSetupBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class PharmacyProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPharmacyProfileSetupBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val pharmacyRepository = PharmacyRepository()

    /** Non-null when editing an existing pharmacy. */
    private var editingPharmacyId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPharmacyProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        editingPharmacyId = intent.getStringExtra("pharmacyId")

        if (editingPharmacyId != null) {
            binding.titleText.text = getString(R.string.edit_pharmacy_profile)
            binding.subtitleText.text = getString(R.string.update_profile)
            binding.saveButton.text = getString(R.string.update_profile)
            loadExistingPharmacy(editingPharmacyId!!)
        }

        setupClickListeners()
    }

    private fun loadExistingPharmacy(pharmacyId: String) {
        showLoading(true)
        lifecycleScope.launch {
            when (val result = pharmacyRepository.getPharmacy(pharmacyId)) {
                is Resource.Success -> {
                    showLoading(false)
                    prefillFields(result.data)
                }
                is Resource.Error -> {
                    showLoading(false)
                    Toast.makeText(this@PharmacyProfileSetupActivity, result.message, Toast.LENGTH_LONG).show()
                }
                is Resource.Loading -> { /* handled */ }
            }
        }
    }

    private fun prefillFields(pharmacy: Pharmacy) {
        binding.nameEditText.setText(pharmacy.name)
        binding.addressEditText.setText(pharmacy.address)
        binding.cityEditText.setText(pharmacy.city)
        binding.stateEditText.setText(pharmacy.state)
        binding.zipEditText.setText(pharmacy.zipCode)
        binding.phoneEditText.setText(pharmacy.phone)
        binding.emailEditText.setText(pharmacy.email)
        binding.hoursEditText.setText(pharmacy.operatingHours)
        binding.deliverySwitch.isChecked = pharmacy.isDeliveryAvailable
        binding.licenseNumberEditText.setText(pharmacy.licenseNumber)
        binding.licenseDocumentUrlEditText.setText(pharmacy.licenseDocumentUrl)

        // Show verification status in edit mode
        if (pharmacy.verificationStatus.isNotEmpty()) {
            binding.verificationStatusText.visibility = View.VISIBLE
            binding.verificationStatusText.text = when (pharmacy.verificationStatus) {
                "APPROVED" -> getString(R.string.verification_approved)
                "REJECTED" -> getString(R.string.verification_rejected)
                else -> getString(R.string.verification_pending)
            }
        }
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }

        binding.saveButton.setOnClickListener {
            if (validateInputs()) {
                savePharmacyProfile()
            }
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        val requiredFields = listOf(
            binding.nameLayout to binding.nameEditText,
            binding.addressLayout to binding.addressEditText,
            binding.cityLayout to binding.cityEditText,
            binding.stateLayout to binding.stateEditText,
            binding.zipLayout to binding.zipEditText,
            binding.phoneLayout to binding.phoneEditText
        )

        for ((layout, editText) in requiredFields) {
            if (editText.text.toString().trim().isEmpty()) {
                layout.error = getString(R.string.field_required)
                isValid = false
            } else {
                layout.error = null
            }
        }

        return isValid
    }

    private fun savePharmacyProfile() {
        showLoading(true)

        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            showLoading(false)
            return
        }

        val pharmacy = Pharmacy(
            id = editingPharmacyId ?: "",
            name = binding.nameEditText.text.toString().trim(),
            address = binding.addressEditText.text.toString().trim(),
            city = binding.cityEditText.text.toString().trim(),
            state = binding.stateEditText.text.toString().trim(),
            zipCode = binding.zipEditText.text.toString().trim(),
            phone = binding.phoneEditText.text.toString().trim(),
            email = binding.emailEditText.text.toString().trim(),
            operatingHours = binding.hoursEditText.text.toString().trim(),
            isDeliveryAvailable = binding.deliverySwitch.isChecked,
            isActive = true,
            ownerId = uid,
            licenseNumber = binding.licenseNumberEditText.text.toString().trim(),
            licenseDocumentUrl = binding.licenseDocumentUrlEditText.text.toString().trim()
        )

        lifecycleScope.launch {
            when (val result = pharmacyRepository.savePharmacyProfile(pharmacy)) {
                is Resource.Success -> {
                    showLoading(false)
                    val msg = if (editingPharmacyId != null)
                        getString(R.string.pharmacy_profile_updated)
                    else
                        getString(R.string.pharmacy_profile_saved)
                    Toast.makeText(this@PharmacyProfileSetupActivity, msg, Toast.LENGTH_SHORT).show()
                    finish()
                }
                is Resource.Error -> {
                    showLoading(false)
                    Toast.makeText(
                        this@PharmacyProfileSetupActivity,
                        getString(R.string.pharmacy_profile_save_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
                is Resource.Loading -> { /* handled */ }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.saveButton.isEnabled = !show
    }
}

