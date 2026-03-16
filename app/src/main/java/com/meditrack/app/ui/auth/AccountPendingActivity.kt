package com.meditrack.app.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.meditrack.app.R
import com.meditrack.app.core.navigation.RoleBasedNavigator
import com.meditrack.app.data.model.AccountStatus
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.User
import com.meditrack.app.databinding.ActivityAccountPendingBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AccountPendingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountPendingBinding
    private val viewModel: AccountPendingViewModel by viewModels()

    private val pickDocument = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadLicense(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountPendingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeViewModel()
        viewModel.loadUserStatus()
    }

    private fun setupClickListeners() {
        binding.refreshButton.setOnClickListener {
            viewModel.loadUserStatus(forceServerRefresh = true)
        }

        binding.signOutButton.setOnClickListener {
            viewModel.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.uploadLicenseButton.setOnClickListener {
            pickDocument.launch("*/*")
        }
    }

    private fun observeViewModel() {
        viewModel.userStatus.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.refreshButton.isEnabled = false
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.refreshButton.isEnabled = true
                    val user = result.data
                    updateUI(user)

                    if (user.status == AccountStatus.APPROVED) {
                        RoleBasedNavigator.navigateToDashboard(this, user)
                    }
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.refreshButton.isEnabled = true
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.uploadResult.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.uploadLicenseButton.isEnabled = false
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.uploadLicenseButton.visibility = View.GONE
                    binding.licenseUploadedText.visibility = View.VISIBLE
                    Toast.makeText(this, getString(R.string.license_upload_success), Toast.LENGTH_SHORT).show()
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.uploadLicenseButton.isEnabled = true
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
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
}
