package com.meditrack.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.meditrack.app.R
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.User
import com.meditrack.app.data.model.UserRole
import com.meditrack.app.databinding.ActivityMainBinding
import com.meditrack.app.ui.auth.LoginActivity
import com.meditrack.app.ui.profile.EditProfileActivity
import com.meditrack.app.ui.profile.ProfileActivity
import com.meditrack.app.ui.profile.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadCurrentUser()
    }

    private fun setupClickListeners() {
        binding.editProfileButton.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        binding.viewProfileButton.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.signOutButton.setOnClickListener {
            viewModel.signOut()
            navigateToLogin()
        }
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
        binding.userInfoText.text = getString(
            R.string.welcome_back
        ) + ", ${user.displayName.ifEmpty { "User" }}!"

        binding.profileNameText.text = user.displayName.ifEmpty { "Set your name" }
        binding.profileEmailText.text = user.email
        binding.profileRoleText.text = when (user.role) {
            UserRole.PATIENT -> getString(R.string.patient)
            UserRole.DOCTOR -> getString(R.string.doctor)
            UserRole.ADMIN -> getString(R.string.admin)
            UserRole.PHARMACY -> getString(R.string.pharmacy_store)
        }

        if (user.profileImageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(user.profileImageUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(binding.profileImage)
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

