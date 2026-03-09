package com.example.meditrack.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.meditrack.R
import com.example.meditrack.data.model.AccountStatus
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.model.User
import com.example.meditrack.data.repository.AuthRepository
import com.example.meditrack.databinding.ActivityAccountPendingBinding
import com.example.meditrack.ui.admin.AdminDashboardActivity
import com.example.meditrack.ui.doctor.DoctorDashboardActivity
import com.example.meditrack.ui.main.HomeDashboardActivity
import com.example.meditrack.ui.pharmacy.PharmacyDashboardActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Gate screen shown to users whose account is PENDING, REJECTED, or SUSPENDED.
 * They cannot access any dashboard until an admin approves their account.
 */
class AccountPendingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountPendingBinding
    private val authRepository = AuthRepository()

    private val pickDocument = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadLicense(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountPendingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        loadUserStatus()
    }

    private fun setupClickListeners() {
        binding.refreshButton.setOnClickListener {
            loadUserStatus()
        }

        binding.signOutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.uploadLicenseButton.setOnClickListener {
            pickDocument.launch("*/*")
        }
    }

    private fun loadUserStatus() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            when (val result = authRepository.getCurrentUser()) {
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    val user = result.data
                    updateUI(user)

                    // If approved now, navigate to dashboard
                    if (user.status == AccountStatus.APPROVED) {
                        navigateToDashboard(user)
                    }
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@AccountPendingActivity, result.message, Toast.LENGTH_LONG).show()
                }
                is Resource.Loading -> { /* already showing */ }
            }
        }
    }

    private fun updateUI(user: User) {
        when (user.status) {
            AccountStatus.PENDING -> {
                binding.statusIcon.setImageResource(R.drawable.ic_clock)
                binding.statusTitle.text = getString(R.string.account_pending_title)
                binding.statusMessage.text = getString(R.string.account_pending_message)
                binding.rejectionReasonLabel.visibility = View.GONE
                binding.rejectionReasonText.visibility = View.GONE

                // Show upload button if no license uploaded yet
                if (user.licenseUrl.isEmpty() && user.requiresApproval) {
                    binding.uploadLicenseButton.visibility = View.VISIBLE
                    binding.licenseUploadedText.visibility = View.GONE
                } else if (user.licenseUrl.isNotEmpty()) {
                    binding.uploadLicenseButton.visibility = View.GONE
                    binding.licenseUploadedText.visibility = View.VISIBLE
                }
            }
            AccountStatus.REJECTED -> {
                binding.statusIcon.setImageResource(R.drawable.ic_close)
                binding.statusTitle.text = getString(R.string.account_rejected_title)
                binding.statusMessage.text = getString(R.string.account_rejected_message)
                binding.uploadLicenseButton.visibility = View.GONE

                if (user.rejectionReason.isNotEmpty()) {
                    binding.rejectionReasonLabel.visibility = View.VISIBLE
                    binding.rejectionReasonText.visibility = View.VISIBLE
                    binding.rejectionReasonText.text = user.rejectionReason
                }
            }
            AccountStatus.SUSPENDED -> {
                binding.statusIcon.setImageResource(R.drawable.ic_warning)
                binding.statusTitle.text = getString(R.string.account_suspended_title)
                binding.statusMessage.text = getString(R.string.account_suspended_message)
                binding.uploadLicenseButton.visibility = View.GONE
                binding.rejectionReasonLabel.visibility = View.GONE
                binding.rejectionReasonText.visibility = View.GONE
            }
            AccountStatus.APPROVED -> { /* will navigate away */ }
        }
    }

    private fun uploadLicense(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        binding.uploadLicenseButton.isEnabled = false
        lifecycleScope.launch {
            when (val result = authRepository.uploadLicenseDocument(uri)) {
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.uploadLicenseButton.visibility = View.GONE
                    binding.licenseUploadedText.visibility = View.VISIBLE
                    Toast.makeText(
                        this@AccountPendingActivity,
                        getString(R.string.license_upload_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.uploadLicenseButton.isEnabled = true
                    Toast.makeText(this@AccountPendingActivity, result.message, Toast.LENGTH_LONG).show()
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun navigateToDashboard(user: User) {
        val intent = when (user.role) {
            com.example.meditrack.data.model.UserRole.DOCTOR -> Intent(this, DoctorDashboardActivity::class.java)
            com.example.meditrack.data.model.UserRole.ADMIN -> Intent(this, AdminDashboardActivity::class.java)
            com.example.meditrack.data.model.UserRole.PHARMACY -> Intent(this, PharmacyDashboardActivity::class.java)
            com.example.meditrack.data.model.UserRole.PATIENT -> Intent(this, HomeDashboardActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

