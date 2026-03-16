package com.meditrack.app.ui.pharmacy

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.meditrack.app.R
import com.meditrack.app.data.model.Pharmacy
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.PharmacyRepository
import com.meditrack.app.databinding.ActivityPharmacyProfileSetupBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PharmacyProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPharmacyProfileSetupBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }
    private val pharmacyRepository = PharmacyRepository()

    /** Non-null when editing an existing pharmacy. */
    private var editingPharmacyId: String? = null

    /** Uploaded file URLs cached during this session. */
    private var licenseDocumentUrl: String = ""
    private var imageUrl: String = ""

    // ── File pickers ─────────────────────────────────────────
    private val licensePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { uploadFile(it, "licenses") { url ->
        licenseDocumentUrl = url
        binding.licenseUploadStatus.text = getString(R.string.license_uploaded)
        binding.licenseUploadStatus.visibility = View.VISIBLE
    }}}

    private val logoPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { uploadFile(it, "logos") { url ->
        imageUrl = url
        binding.logoUploadStatus.text = getString(R.string.logo_uploaded)
        binding.logoUploadStatus.visibility = View.VISIBLE
    }}}

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

        // Lat/Lng
        if (pharmacy.latitude != 0.0) binding.latitudeEditText.setText(pharmacy.latitude.toString())
        if (pharmacy.longitude != 0.0) binding.longitudeEditText.setText(pharmacy.longitude.toString())

        // Preserve existing uploaded URLs
        licenseDocumentUrl = pharmacy.licenseDocumentUrl
        imageUrl = pharmacy.imageUrl

        if (licenseDocumentUrl.isNotEmpty()) {
            binding.licenseUploadStatus.text = getString(R.string.license_uploaded)
            binding.licenseUploadStatus.visibility = View.VISIBLE
        }
        if (imageUrl.isNotEmpty()) {
            binding.logoUploadStatus.text = getString(R.string.logo_uploaded)
            binding.logoUploadStatus.visibility = View.VISIBLE
        }

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

        binding.uploadLicenseButton.setOnClickListener {
            licensePicker.launch("application/*")
        }

        binding.uploadLogoButton.setOnClickListener {
            logoPicker.launch("image/*")
        }

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
            latitude = binding.latitudeEditText.text.toString().toDoubleOrNull() ?: 0.0,
            longitude = binding.longitudeEditText.text.toString().toDoubleOrNull() ?: 0.0,
            operatingHours = binding.hoursEditText.text.toString().trim(),
            isDeliveryAvailable = binding.deliverySwitch.isChecked,
            isActive = true,
            ownerId = uid,
            licenseNumber = binding.licenseNumberEditText.text.toString().trim(),
            licenseDocumentUrl = licenseDocumentUrl,
            imageUrl = imageUrl
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

    /**
     * Upload a file to Firebase Storage under pharmacies/{uid}/{folder}/{timestamp}.
     */
    private fun uploadFile(uri: Uri, folder: String, onSuccess: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val ref = storage.reference
            .child("pharmacies/$uid/$folder/${System.currentTimeMillis()}")

        Toast.makeText(this, getString(R.string.uploading_file), Toast.LENGTH_SHORT).show()

        ref.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception!!
                ref.downloadUrl
            }
            .addOnSuccessListener { downloadUrl ->
                onSuccess(downloadUrl.toString())
            }
            .addOnFailureListener {
                Toast.makeText(this, getString(R.string.upload_failed), Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.saveButton.isEnabled = !show
    }
}

