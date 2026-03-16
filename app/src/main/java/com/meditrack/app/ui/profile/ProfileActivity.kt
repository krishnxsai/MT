package com.meditrack.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.meditrack.app.R
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.User
import com.meditrack.app.data.model.UserRole
import com.meditrack.app.databinding.ActivityProfileBinding
import com.meditrack.app.ui.auth.LoginActivity
import com.meditrack.app.ui.main.HomeDashboardActivity
import com.meditrack.app.util.overrideTransitionCompat
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupClickListeners()
        setupBottomNavigation()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadCurrentUser()
        binding.bottomNavigation?.setOnItemSelectedListener(null)
        binding.bottomNavigation?.selectedItemId = R.id.nav_profile
        setupBottomNavigation()
    }

    private fun setupToolbar() {
        try {
            binding.toolbar.setNavigationOnClickListener {
                finish()
            }
        } catch (_: Exception) {}
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation?.setOnItemSelectedListener(null)
        binding.bottomNavigation?.selectedItemId = R.id.nav_profile
        binding.bottomNavigation?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, HomeDashboardActivity::class.java))
                    overrideTransitionCompat(0, 0)
                    finish()
                    true
                }
                R.id.nav_medicines -> {
                    startActivity(Intent(this, com.meditrack.app.ui.medicine.MedicinesListActivity::class.java))
                    overrideTransitionCompat(0, 0)
                    finish()
                    true
                }
                R.id.nav_health_logs -> {
                    startActivity(Intent(this, com.meditrack.app.ui.healthlog.HealthLogListActivity::class.java))
                    overrideTransitionCompat(0, 0)
                    finish()
                    true
                }
                R.id.nav_doctor -> {
                    startActivity(Intent(this, com.meditrack.app.ui.recommendations.DoctorRecommendationsActivity::class.java))
                    overrideTransitionCompat(0, 0)
                    finish()
                    true
                }
                R.id.nav_profile -> true // Already here
                else -> false
            }
        }
    }

    private fun setupClickListeners() {
        binding.editProfileButton.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        binding.signOutButton.setOnClickListener {
            showSignOutConfirmation()
        }

        // ── Notifications ──
        binding.notificationsItem.setOnClickListener {
            val intent = Intent().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                } else {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.parse("package:$packageName")
                }
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Unable to open notification settings", Toast.LENGTH_SHORT).show()
            }
        }

        // ── Privacy Settings ──
        binding.privacyItem.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.privacy_settings)
                .setMessage("MediTrack stores your health data securely in Firebase with end-to-end encryption.\n\n" +
                        "• Your data is only visible to you and your assigned doctors.\n" +
                        "• Medicines and health logs are never shared with third parties.\n" +
                        "• You can delete your account and all data at any time from Edit Profile.")
                .setPositiveButton("OK", null)
                .setNeutralButton("Privacy Policy") { _, _ ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://meditrack.app/privacy")))
                    } catch (_: Exception) {
                        Toast.makeText(this, "Unable to open browser", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }

        // ── Device Settings ──
        binding.deviceSettingsItem.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Unable to open app settings", Toast.LENGTH_SHORT).show()
            }
        }

        // ── Help & Support ──
        binding.helpItem.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.help_support)
                .setMessage("Need help with MediTrack?\n\n" +
                        "📧 Email: support@meditrack.app\n" +
                        "📖 Version: 1.0.0\n\n" +
                        "Common tips:\n" +
                        "• Enable exact alarms in Device Settings for reliable reminders\n" +
                        "• Make sure notifications are enabled for medicine alerts\n" +
                        "• Tap any medicine to order a refill or manage stock")
                .setPositiveButton("OK", null)
                .setNeutralButton("Email Support") { _, _ ->
                    try {
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:support@meditrack.app")
                            putExtra(Intent.EXTRA_SUBJECT, "MediTrack Support Request")
                        }
                        startActivity(emailIntent)
                    } catch (_: Exception) {
                        Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }
    }

    private fun showSignOutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.sign_out)
            .setMessage(R.string.sign_out_confirmation)
            .setPositiveButton(R.string.sign_out) { _, _ ->
                viewModel.signOut()
                navigateToLogin()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun observeViewModel() {
        viewModel.currentUser.observe(this) { result ->
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
    }

    private fun updateUI(user: User) {
        binding.nameText.text = user.displayName.ifEmpty { getString(R.string.not_set) }
        binding.roleText.text = when (user.role) {
            UserRole.PATIENT -> getString(R.string.patient)
            UserRole.DOCTOR -> getString(R.string.doctor)
            UserRole.ADMIN -> getString(R.string.admin)
            UserRole.PHARMACY -> getString(R.string.pharmacy_store)
        }
        binding.emailText.text = user.email
        binding.phoneText.text = user.phoneNumber.ifEmpty { getString(R.string.not_set) }

        // Set profile initial letter
        val displayName = user.displayName.ifEmpty { "U" }
        try {
            binding.profileInitial?.text = displayName.first().uppercase()
        } catch (_: Exception) {}

        // Show assigned doctor section only for patients
        if (user.role == UserRole.PATIENT) {
            binding.assignedDoctorLayout.visibility = View.VISIBLE
            val doctorNames = user.assignedDoctorNames.values.filter { it.isNotEmpty() }
            binding.assignedDoctorText.text = if (doctorNames.isNotEmpty()) {
                doctorNames.joinToString(", ")
            } else {
                getString(R.string.not_set)
            }
        } else {
            binding.assignedDoctorLayout.visibility = View.GONE
        }

        if (user.profileImageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(user.profileImageUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(binding.profileImage)
            try { binding.profileInitial?.visibility = View.GONE } catch (_: Exception) {}
        } else {
            try { binding.profileInitial?.visibility = View.VISIBLE } catch (_: Exception) {}
        }
    }
}

