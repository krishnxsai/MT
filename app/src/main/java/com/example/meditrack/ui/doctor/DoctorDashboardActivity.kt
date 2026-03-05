package com.example.meditrack.ui.doctor

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.meditrack.R
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.model.User
import com.example.meditrack.data.security.SecurityManager
import com.example.meditrack.databinding.ActivityDoctorDashboardPremiumBinding
import com.example.meditrack.ui.auth.LoginActivity
import com.example.meditrack.ui.chat.ChatListActivity
import com.example.meditrack.ui.chat.ChatListViewModel
import com.example.meditrack.ui.appointment.AppointmentsListActivity
import com.example.meditrack.ui.appointment.DoctorAvailabilityActivity
import com.example.meditrack.ui.profile.ProfileActivity
import com.example.meditrack.ui.profile.ProfileViewModel
import kotlinx.coroutines.launch

class DoctorDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDoctorDashboardPremiumBinding
    private val doctorViewModel: DoctorViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    private val chatListViewModel: ChatListViewModel by viewModels()
    private lateinit var patientAdapter: PatientAdapter
    private lateinit var securityManager: SecurityManager
    private var isLoggingOut = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDoctorDashboardPremiumBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityManager = SecurityManager(this)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        profileViewModel.loadCurrentUser()
        doctorViewModel.loadPatients()
    }

    private fun setupRecyclerView() {
        patientAdapter = PatientAdapter { patient ->
            navigateToPatientDetail(patient)
        }

        binding.patientsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DoctorDashboardActivity)
            adapter = patientAdapter
        }
    }

    private fun setupClickListeners() {
        binding.profileImageContainer.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.signOutButton.setOnClickListener {
            performSecureLogout()
        }

        binding.messagesCard.setOnClickListener {
            startActivity(Intent(this, ChatListActivity::class.java))
        }

        binding.appointmentsCard.setOnClickListener {
            startActivity(Intent(this, AppointmentsListActivity::class.java))
        }

        binding.availabilityCard.setOnClickListener {
            startActivity(Intent(this, DoctorAvailabilityActivity::class.java))
        }
    }

    private fun performSecureLogout() {
        isLoggingOut = true
        lifecycleScope.launch {
            securityManager.secureLogout()
            navigateToLogin()
        }
    }

    private fun observeViewModel() {
        // Doctor profile
        profileViewModel.currentUser.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {}
                is Resource.Success -> {
                    updateDoctorUI(result.data)
                }
                is Resource.Error -> {
                    // Don't show errors during logout or for transient Firestore errors
                    if (!isLoggingOut && !isTransientFirestoreError(result.message)) {
                        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Patients list
        doctorViewModel.patients.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.loadingContainer.visibility = View.VISIBLE
                }
                is Resource.Success -> {
                    binding.loadingContainer.visibility = View.GONE
                    updatePatientsList(result.data)
                }
                is Resource.Error -> {
                    binding.loadingContainer.visibility = View.GONE
                    // Don't show errors during logout or for transient Firestore errors
                    if (!isLoggingOut && !isTransientFirestoreError(result.message)) {
                        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun updateDoctorUI(user: User) {
        binding.doctorNameText.text = "Dr. ${user.displayName.ifEmpty { "Doctor" }}"
        binding.greetingText.text = getGreeting()

        if (user.profileImageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(user.profileImageUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .circleCrop()
                .into(binding.profileImage)
        }
    }

    private fun getGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> getString(R.string.greeting_morning)
            hour < 17 -> getString(R.string.greeting_afternoon)
            else -> getString(R.string.greeting_evening)
        } + " 👋"
    }

    private fun updatePatientsList(patients: List<User>) {
        // Update count display - just the number for cleaner look
        binding.patientCountText.text = patients.size.toString()

        if (patients.isEmpty()) {
            binding.emptyStateContainer.visibility = View.VISIBLE
            binding.patientsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateContainer.visibility = View.GONE
            binding.patientsRecyclerView.visibility = View.VISIBLE
            patientAdapter.submitList(patients)
        }
    }

    private fun navigateToPatientDetail(patient: User) {
        val intent = Intent(this, PatientDetailActivity::class.java).apply {
            putExtra(PatientDetailActivity.EXTRA_PATIENT_ID, patient.uid)
            putExtra(PatientDetailActivity.EXTRA_PATIENT_NAME, patient.displayName)
        }
        startActivity(intent)
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * Check if an error message is a transient Firestore error that shouldn't be shown to users.
     * These include permission errors during logout, missing indexes, etc.
     */
    private fun isTransientFirestoreError(message: String?): Boolean {
        if (message == null) return false
        val transientErrors = listOf(
            "PERMISSION_DENIED",
            "Missing or insufficient permissions",
            "requires an index",
            "FAILED_PRECONDITION"
        )
        return transientErrors.any { message.contains(it, ignoreCase = true) }
    }
}

