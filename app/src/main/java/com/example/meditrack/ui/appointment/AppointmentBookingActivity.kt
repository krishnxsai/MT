package com.example.meditrack.ui.appointment

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.meditrack.R
import com.example.meditrack.data.model.AppointmentType
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.repository.TimeSlot
import com.example.meditrack.databinding.ActivityAppointmentBookingBinding
import java.text.SimpleDateFormat
import java.util.*

class AppointmentBookingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DOCTOR_ID = "extra_doctor_id"
        const val EXTRA_DOCTOR_NAME = "extra_doctor_name"
        const val EXTRA_PATIENT_ID = "extra_patient_id"
        const val EXTRA_PATIENT_NAME = "extra_patient_name"
        const val EXTRA_RESCHEDULE_ID = "extra_reschedule_id"
    }

    private lateinit var binding: ActivityAppointmentBookingBinding
    private val viewModel: AppointmentViewModel by viewModels()

    private var doctorId: String = ""
    private var patientId: String = ""
    private var isDoctorBooking: Boolean = false
    private var rescheduleId: String = ""
    private var selectedDate: Date? = null
    private var selectedSlot: TimeSlot? = null
    private var selectedType: AppointmentType = AppointmentType.CONSULTATION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppointmentBookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        doctorId = intent.getStringExtra(EXTRA_DOCTOR_ID) ?: ""
        val doctorName = intent.getStringExtra(EXTRA_DOCTOR_NAME) ?: "Doctor"
        patientId = intent.getStringExtra(EXTRA_PATIENT_ID) ?: ""
        val patientName = intent.getStringExtra(EXTRA_PATIENT_NAME) ?: ""
        isDoctorBooking = patientId.isNotEmpty()
        rescheduleId = intent.getStringExtra(EXTRA_RESCHEDULE_ID) ?: ""

        if (doctorId.isEmpty()) {
            Toast.makeText(this, "Invalid doctor", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        if (rescheduleId.isNotEmpty()) {
            binding.doctorNameText.text = if (isDoctorBooking) "Rescheduling for $patientName" else "Reschedule with Dr. $doctorName"
        } else if (isDoctorBooking) {
            binding.doctorNameText.text = "Booking for $patientName"
        } else {
            binding.doctorNameText.text = "Dr. $doctorName"
        }

        setupDatePicker()
        setupTypeChips()
        setupBookButton()
        observeViewModel()
    }

    private fun setupDatePicker() {
        binding.selectDateButton.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    val picked = Calendar.getInstance().apply {
                        set(year, month, day, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    // Don't allow past dates
                    if (picked.time.before(Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.time)) {
                        Toast.makeText(this, "Cannot book past dates", Toast.LENGTH_SHORT).show()
                        return@DatePickerDialog
                    }
                    selectedDate = picked.time
                    val fmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
                    binding.selectDateButton.text = fmt.format(picked.time)
                    selectedSlot = null
                    updateBookButton()
                    viewModel.loadAvailableSlots(doctorId, picked.time)
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.minDate = System.currentTimeMillis()
            }.show()
        }
    }

    private fun setupTypeChips() {
        binding.typeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedType = when {
                checkedIds.contains(R.id.chipFollowUp) -> AppointmentType.FOLLOW_UP
                checkedIds.contains(R.id.chipCheckup) -> AppointmentType.CHECKUP
                else -> AppointmentType.CONSULTATION
            }
        }
    }

    private fun setupBookButton() {
        binding.bookButton.setOnClickListener {
            val date = selectedDate ?: return@setOnClickListener
            val slot = selectedSlot ?: return@setOnClickListener
            val notes = binding.notesInput.text?.toString()?.trim() ?: ""

            if (rescheduleId.isNotEmpty()) {
                // Reschedule: cancel old + create new
                viewModel.rescheduleAppointment(
                    oldAppointmentId = rescheduleId,
                    doctorId = doctorId,
                    patientId = if (isDoctorBooking) patientId else "",
                    newDate = date,
                    newStartTime = slot.startTime,
                    newEndTime = slot.endTime,
                    type = selectedType,
                    notes = notes
                )
            } else if (isDoctorBooking) {
                viewModel.bookAppointmentAsDoctor(
                    patientId = patientId,
                    date = date,
                    startTime = slot.startTime,
                    endTime = slot.endTime,
                    type = selectedType,
                    notes = notes
                )
            } else {
                viewModel.bookAppointment(
                    doctorId = doctorId,
                    date = date,
                    startTime = slot.startTime,
                    endTime = slot.endTime,
                    type = selectedType,
                    notes = notes
                )
            }
        }
    }

    private fun observeViewModel() {
        viewModel.availableSlots.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.noSlotsText.text = "Loading slots…"
                    binding.noSlotsText.visibility = View.VISIBLE
                    binding.slotsRecyclerView.visibility = View.GONE
                }
                is Resource.Success -> {
                    val slots = result.data.filter { it.isAvailable }
                    if (slots.isEmpty()) {
                        binding.noSlotsText.text = "No available slots on this day"
                        binding.noSlotsText.visibility = View.VISIBLE
                        binding.slotsRecyclerView.visibility = View.GONE
                    } else {
                        binding.noSlotsText.visibility = View.GONE
                        binding.slotsRecyclerView.visibility = View.VISIBLE
                        setupSlotsGrid(result.data)
                    }
                }
                is Resource.Error -> {
                    binding.noSlotsText.text = "Failed to load slots: ${result.message}"
                    binding.noSlotsText.visibility = View.VISIBLE
                    binding.slotsRecyclerView.visibility = View.GONE
                }
            }
        }

        viewModel.bookingResult.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.bookButton.isEnabled = false
                    binding.bookButton.text = if (rescheduleId.isNotEmpty()) "Rescheduling…" else "Booking…"
                }
                is Resource.Success -> {
                    val msg = if (rescheduleId.isNotEmpty()) "Appointment rescheduled successfully!" else "Appointment booked successfully!"
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    finish()
                }
                is Resource.Error -> {
                    binding.bookButton.isEnabled = true
                    binding.bookButton.text = if (rescheduleId.isNotEmpty()) "Reschedule Appointment" else "Book Appointment"
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupSlotsGrid(slots: List<TimeSlot>) {
        binding.slotsRecyclerView.layoutManager = GridLayoutManager(this, 3)
        binding.slotsRecyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class SlotVH(val textView: TextView) : RecyclerView.ViewHolder(textView)

            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = layoutInflater.inflate(R.layout.item_time_slot, parent, false)
                return SlotVH(view.findViewById(R.id.slotText))
            }

            override fun getItemCount() = slots.size

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val slot = slots[position]
                val tv = (holder as SlotVH).textView
                tv.text = slot.startTime

                if (slot.isAvailable) {
                    val isSelected = selectedSlot == slot
                    if (isSelected) {
                        tv.setBackgroundResource(R.drawable.bg_time_slot_selected)
                        tv.setTextColor(ContextCompat.getColor(this@AppointmentBookingActivity, R.color.white))
                    } else {
                        tv.setBackgroundResource(R.drawable.bg_time_slot_available)
                        tv.setTextColor(ContextCompat.getColor(this@AppointmentBookingActivity, R.color.premium_primary))
                    }
                    tv.alpha = 1f
                    tv.setOnClickListener {
                        selectedSlot = slot
                        updateBookButton()
                        notifyDataSetChanged()
                    }
                } else {
                    tv.setBackgroundResource(R.drawable.bg_time_slot_booked)
                    tv.setTextColor(ContextCompat.getColor(this@AppointmentBookingActivity, R.color.premium_text_tertiary))
                    tv.alpha = 0.5f
                    tv.setOnClickListener(null)
                }
            }
        }
    }

    private fun updateBookButton() {
        binding.bookButton.isEnabled = selectedDate != null && selectedSlot != null
        binding.bookButton.text = if (rescheduleId.isNotEmpty()) "Reschedule Appointment" else "Book Appointment"
    }
}
