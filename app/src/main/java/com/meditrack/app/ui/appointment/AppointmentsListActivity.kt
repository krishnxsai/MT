package com.meditrack.app.ui.appointment

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.User
import com.meditrack.app.data.model.UserRole
import com.meditrack.app.databinding.ActivityAppointmentsListBinding
import com.meditrack.app.ui.profile.ProfileViewModel
import com.meditrack.app.util.CallUtils
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppointmentsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppointmentsListBinding
    private val viewModel: AppointmentViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    private lateinit var adapter: AppointmentAdapter
    private var isDoctor = false
    private var currentUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppointmentsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        observeProfile()
        setupTabs()
    }

    override fun onResume() {
        super.onResume()
        // Reload appointments when returning from booking/reschedule activity
        if (::adapter.isInitialized) {
            val selectedTab = binding.tabLayout.selectedTabPosition
            if (selectedTab == 1) {
                viewModel.loadPastAppointments()
            } else {
                viewModel.loadUpcomingAppointments()
            }
        }
    }

    private fun setupToolbar() {
        try { binding.toolbar.setNavigationOnClickListener { finish() } } catch (_: Exception) {}
        try { binding.backBtn?.setOnClickListener { finish() } } catch (_: Exception) {}
    }

    private fun observeProfile() {
        profileViewModel.currentUser.observe(this) { result ->
            if (result is Resource.Success) {
                currentUser = result.data
                isDoctor = result.data.role == UserRole.DOCTOR
                setupRecyclerView()
                setupBookFab(result.data)
                viewModel.loadUpcomingAppointments()
                observeAppointments()
            }
        }
    }

    /**
     * Show a "Book" FAB for patients who have at least one assigned doctor.
     * If multiple doctors, show a picker dialog.
     */
    private fun setupBookFab(user: User) {
        if (user.role == UserRole.PATIENT && user.assignedDoctors.isNotEmpty()) {
            binding.bookAppointmentFab.visibility = View.VISIBLE
            binding.bookAppointmentFab.setOnClickListener {
                if (user.assignedDoctors.size == 1) {
                    // Single doctor – go directly to booking
                    val doctorId = user.assignedDoctors.first()
                    val doctorName = user.assignedDoctorNames[doctorId] ?: ""
                    openBooking(doctorId, doctorName)
                } else {
                    // Multiple doctors – show picker
                    val doctorIds = user.assignedDoctors
                    val doctorNames = doctorIds.map { user.assignedDoctorNames[it] ?: "Doctor" }.toTypedArray()
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Select Doctor")
                        .setItems(doctorNames) { _, which ->
                            openBooking(doctorIds[which], doctorNames[which])
                        }
                        .show()
                }
            }
        } else {
            binding.bookAppointmentFab.visibility = View.GONE
        }
    }

    private fun openBooking(doctorId: String, doctorName: String) {
        val intent = Intent(this, AppointmentBookingActivity::class.java).apply {
            putExtra(AppointmentBookingActivity.EXTRA_DOCTOR_ID, doctorId)
            putExtra(AppointmentBookingActivity.EXTRA_DOCTOR_NAME, doctorName)
        }
        startActivity(intent)
    }

    private fun setupRecyclerView() {
        adapter = AppointmentAdapter(
            isDoctor = isDoctor,
            onConfirm = { appt ->
                viewModel.confirmAppointment(appt.id)
                // Schedule reminder for confirmed appointment
                com.meditrack.app.alarm.AppointmentAlarmScheduler(this)
                    .scheduleReminder(appt)
            },
            onReject = { appt ->
                viewModel.rejectAppointment(appt.id)
                // Cancel any pending reminder
                com.meditrack.app.alarm.AppointmentAlarmScheduler(this)
                    .cancelReminder(appt.id)
            },
            onCancel = { appt ->
                android.app.AlertDialog.Builder(this)
                    .setTitle("Cancel Appointment")
                    .setMessage("Are you sure you want to cancel this appointment?")
                    .setPositiveButton("Cancel Appointment") { _, _ ->
                        viewModel.cancelAppointment(appt.id, "Cancelled by ${if (isDoctor) "doctor" else "patient"}")
                        // Cancel the reminder
                        com.meditrack.app.alarm.AppointmentAlarmScheduler(this)
                            .cancelReminder(appt.id)
                    }
                    .setNegativeButton("Keep", null)
                    .show()
            },
            onReschedule = { appt ->
                val intent = Intent(this, AppointmentBookingActivity::class.java).apply {
                    putExtra(AppointmentBookingActivity.EXTRA_DOCTOR_ID, appt.doctorId)
                    putExtra(AppointmentBookingActivity.EXTRA_DOCTOR_NAME, appt.doctorName)
                    if (isDoctor) {
                        putExtra(AppointmentBookingActivity.EXTRA_PATIENT_ID, appt.patientId)
                        putExtra(AppointmentBookingActivity.EXTRA_PATIENT_NAME, appt.patientName)
                    }
                    putExtra(AppointmentBookingActivity.EXTRA_RESCHEDULE_ID, appt.id)
                }
                startActivity(intent)
            },
            onCall = { appt ->
                // Fetch phone number of the other party and make the call
                val userIdToCall = if (isDoctor) appt.patientId else appt.doctorId
                val nameToCall = if (isDoctor) appt.patientName else "Dr. ${appt.doctorName}"

                FirebaseFirestore.getInstance().collection("users")
                    .document(userIdToCall)
                    .get()
                    .addOnSuccessListener { doc ->
                        val phone = doc.getString("phoneNumber") ?: ""
                        CallUtils.dialPhoneNumber(this, phone, nameToCall)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to get phone number", Toast.LENGTH_SHORT).show()
                    }
            }
        )

        binding.appointmentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AppointmentsListActivity)
            adapter = this@AppointmentsListActivity.adapter
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> viewModel.loadUpcomingAppointments()
                    1 -> viewModel.loadPastAppointments()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun observeAppointments() {
        viewModel.upcomingAppointments.observe(this) { handleResult(it) }
        viewModel.pastAppointments.observe(this) { handleResult(it) }

        viewModel.actionResult.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    Toast.makeText(this, "Appointment updated", Toast.LENGTH_SHORT).show()
                }
                is Resource.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun handleResult(result: Resource<List<com.meditrack.app.data.model.Appointment>>) {
        when (result) {
            is Resource.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
            }
            is Resource.Success -> {
                binding.progressBar.visibility = View.GONE
                val list = result.data
                if (list.isEmpty()) {
                    binding.emptyStateContainer.visibility = View.VISIBLE
                    binding.appointmentsRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyStateContainer.visibility = View.GONE
                    binding.appointmentsRecyclerView.visibility = View.VISIBLE
                    adapter.submitList(list)
                }
            }
            is Resource.Error -> {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

