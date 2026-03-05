package com.example.meditrack.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.meditrack.R
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.model.User
import com.example.meditrack.data.model.UserRole
import com.example.meditrack.databinding.ActivityProfileBinding
import com.example.meditrack.ui.auth.LoginActivity

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupClickListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadCurrentUser()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        binding.editProfileButton.setOnClickListener {
            startActivity(android.content.Intent(this, EditProfileActivity::class.java))
        }

        binding.signOutButton.setOnClickListener {
            showSignOutConfirmation()
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
        }
        binding.emailText.text = user.email
        binding.phoneText.text = user.phoneNumber.ifEmpty { getString(R.string.not_set) }

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
        }
    }
}

