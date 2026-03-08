package com.example.meditrack.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meditrack.R
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.model.UserRole
import com.example.meditrack.databinding.ActivityAdminDashboardBinding
import com.example.meditrack.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private val viewModel: AdminViewModel by viewModels()
    private lateinit var userAdapter: AdminUserAdapter

    private var currentRoleFilter: UserRole? = null
    private var currentSearchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSearch()
        setupFilterChips()
        setupClickListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAllUsers()
    }

    private fun setupRecyclerView() {
        userAdapter = AdminUserAdapter { user ->
            Toast.makeText(this, "${user.displayName} (${user.role.name})", Toast.LENGTH_SHORT).show()
        }
        binding.usersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AdminDashboardActivity)
            adapter = userAdapter
        }
    }

    private fun setupSearch() {
        binding.searchEditText.doAfterTextChanged { text ->
            currentSearchQuery = text?.toString()?.trim() ?: ""
            viewModel.filterUsers(currentSearchQuery, currentRoleFilter)
        }
    }

    private fun setupFilterChips() {
        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentRoleFilter = when {
                checkedIds.contains(R.id.filterPatientChip) -> UserRole.PATIENT
                checkedIds.contains(R.id.filterDoctorChip) -> UserRole.DOCTOR
                checkedIds.contains(R.id.filterPharmacyChip) -> UserRole.PHARMACY
                else -> null // "All" or no selection
            }
            viewModel.filterUsers(currentSearchQuery, currentRoleFilter)
        }
    }

    private fun setupClickListeners() {
        binding.signOutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun observeViewModel() {
        viewModel.allUsers.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.emptyStateText.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    if (result.data.isEmpty()) {
                        binding.emptyStateText.visibility = View.VISIBLE
                        binding.usersRecyclerView.visibility = View.GONE
                    } else {
                        binding.emptyStateText.visibility = View.GONE
                        binding.usersRecyclerView.visibility = View.VISIBLE
                        userAdapter.submitList(result.data)
                    }
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.userCounts.observe(this) { counts ->
            binding.totalUsersCount.text = (counts["total"] ?: 0).toString()
            binding.totalPatientsCount.text = (counts["patients"] ?: 0).toString()
            binding.totalDoctorsCount.text = (counts["doctors"] ?: 0).toString()
            binding.totalPharmaciesCount.text = (counts["pharmacies"] ?: 0).toString()
        }
    }
}

