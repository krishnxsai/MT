package com.example.meditrack.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meditrack.R
import com.example.meditrack.data.model.AccountStatus
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.model.User
import com.example.meditrack.data.model.UserRole
import com.example.meditrack.databinding.ActivityAdminDashboardBinding
import com.example.meditrack.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private val viewModel: AdminViewModel by viewModels()

    private lateinit var pendingAdapter: AdminUserAdapter
    private lateinit var allUsersAdapter: AdminUserAdapter
    private lateinit var auditLogAdapter: AuditLogAdapter

    private var currentRoleFilter: UserRole? = null
    private var currentSearchQuery: String = ""

    /** Tracks which tab is active: 0 = Pending, 1 = All Users, 2 = Audit Logs */
    private var activeTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAdapters()
        setupRecyclerView()
        setupSearch()
        setupFilterChips()
        setupClickListeners()
        setupTabs()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAllUsers()
        viewModel.loadPendingUsers()
    }

    private fun setupAdapters() {
        pendingAdapter = AdminUserAdapter(
            onUserClick = { showUserDetails(it) },
            onApprove = { viewModel.approveUser(it.uid) },
            onReject = { showRejectDialog(it) }
        )

        allUsersAdapter = AdminUserAdapter(
            onUserClick = { showUserDetails(it) },
            onApprove = { viewModel.approveUser(it.uid) },
            onReject = { showRejectDialog(it) },
            onSuspend = { confirmSuspend(it) },
            onReactivate = { viewModel.reactivateUser(it.uid) }
        )

        auditLogAdapter = AuditLogAdapter()
    }

    private fun setupRecyclerView() {
        binding.usersRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.usersRecyclerView.adapter = pendingAdapter // default tab
    }

    private fun setupTabs() {
        // Add tabs if not already present via XML
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.pending_approvals)))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.all_users)))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.audit_logs)))

        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                activeTab = tab?.position ?: 0
                when (activeTab) {
                    0 -> {
                        binding.usersRecyclerView.adapter = pendingAdapter
                        binding.searchLayout.visibility = View.GONE
                        binding.filterChipGroup.visibility = View.GONE
                        viewModel.loadPendingUsers()
                    }
                    1 -> {
                        binding.usersRecyclerView.adapter = allUsersAdapter
                        binding.searchLayout.visibility = View.VISIBLE
                        binding.filterChipGroup.visibility = View.VISIBLE
                        viewModel.loadAllUsers()
                    }
                    2 -> {
                        binding.usersRecyclerView.adapter = auditLogAdapter
                        binding.searchLayout.visibility = View.GONE
                        binding.filterChipGroup.visibility = View.GONE
                        viewModel.loadAuditLogs()
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        // Start with search/filter hidden (pending tab default)
        binding.searchLayout.visibility = View.GONE
        binding.filterChipGroup.visibility = View.GONE
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
                else -> null
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

    private fun showRejectDialog(user: User) {
        val input = EditText(this).apply {
            hint = getString(R.string.rejection_reason_hint)
            setPadding(48, 32, 48, 16)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.reject_user_title, user.displayName))
            .setMessage(getString(R.string.reject_user_message))
            .setView(input)
            .setPositiveButton(getString(R.string.reject)) { _, _ ->
                val reason = input.text.toString().trim().ifEmpty { "No reason provided" }
                viewModel.rejectUser(user.uid, reason)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmSuspend(user: User) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.suspend_user_title))
            .setMessage(getString(R.string.suspend_user_message, user.displayName))
            .setPositiveButton(getString(R.string.suspend_account)) { _, _ ->
                viewModel.suspendUser(user.uid)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showUserDetails(user: User) {
        val details = buildString {
            append("Name: ${user.displayName}\n")
            append("Email: ${user.email}\n")
            append("Role: ${user.role.name}\n")
            append("Status: ${user.status.name}\n")
            if (user.phoneNumber.isNotEmpty()) append("Phone: ${user.phoneNumber}\n")
            if (user.licenseUrl.isNotEmpty()) append("\n✓ License document uploaded")
            if (user.verifiedBy.isNotEmpty()) append("\nVerified by: ${user.verifiedBy}")
            if (user.rejectionReason.isNotEmpty()) append("\nRejection reason: ${user.rejectionReason}")
        }
        AlertDialog.Builder(this)
            .setTitle(user.displayName.ifEmpty { user.email })
            .setMessage(details)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.pendingUsers.observe(this) { result ->
            if (activeTab != 0) return@observe
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.emptyStateText.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    if (result.data.isEmpty()) {
                        binding.emptyStateText.text = getString(R.string.no_pending_approvals)
                        binding.emptyStateText.visibility = View.VISIBLE
                        binding.usersRecyclerView.visibility = View.GONE
                    } else {
                        binding.emptyStateText.visibility = View.GONE
                        binding.usersRecyclerView.visibility = View.VISIBLE
                        pendingAdapter.submitList(result.data)
                    }
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.allUsers.observe(this) { result ->
            if (activeTab != 1) return@observe
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.emptyStateText.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    if (result.data.isEmpty()) {
                        binding.emptyStateText.text = getString(R.string.no_users_found)
                        binding.emptyStateText.visibility = View.VISIBLE
                        binding.usersRecyclerView.visibility = View.GONE
                    } else {
                        binding.emptyStateText.visibility = View.GONE
                        binding.usersRecyclerView.visibility = View.VISIBLE
                        allUsersAdapter.submitList(result.data)
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

        viewModel.actionResult.observe(this) { result ->
            when (result) {
                is Resource.Success -> Toast.makeText(this, getString(R.string.action_success), Toast.LENGTH_SHORT).show()
                is Resource.Error -> Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                is Resource.Loading -> { /* shown by individual list observers */ }
            }
        }

        viewModel.auditLogs.observe(this) { result ->
            if (activeTab != 2) return@observe
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.emptyStateText.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    if (result.data.isEmpty()) {
                        binding.emptyStateText.text = getString(R.string.no_audit_logs)
                        binding.emptyStateText.visibility = View.VISIBLE
                        binding.usersRecyclerView.visibility = View.GONE
                    } else {
                        binding.emptyStateText.visibility = View.GONE
                        binding.usersRecyclerView.visibility = View.VISIBLE
                        auditLogAdapter.submitList(result.data)
                    }
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

