package com.meditrack.app.ui.appointment

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.meditrack.app.R
import com.meditrack.app.data.model.AppointmentType
import com.meditrack.app.data.model.DoctorAvailability
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.TimeSlot
import com.meditrack.app.databinding.ActivityAppointmentBookingBinding
import com.meditrack.app.util.CallUtils
import java.text.SimpleDateFormat
import java.util.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
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
    private var availabilityList: List<DoctorAvailability> = emptyList()

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

        try { binding.toolbar.setNavigationOnClickListener { finish() } } catch (_: Exception) {}
        try { binding.backBtn?.setOnClickListener { finish() } } catch (_: Exception) {}
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
        setupCallButton()
        observeViewModel()

        // Load doctor's availability schedule
        viewModel.loadDoctorAvailability(doctorId)
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

    private fun setupCallButton() {
        // Try to find call button in layout and set listener
        try {
            binding.callDoctorButton?.setOnClickListener {
                callDoctor()
            }
        } catch (e: Exception) {
            // Call button might not exist in layout
        }
    }

    private fun callDoctor() {
        lifecycleScope.launch {
            val result = viewModel.getDoctorPhone(doctorId)
            when (result) {
                is Resource.Success -> {
                    val phone = result.data.orEmpty()
                    val doctorName = intent.getStringExtra(EXTRA_DOCTOR_NAME) ?: "Doctor"
                    CallUtils.dialPhoneNumber(this@AppointmentBookingActivity, phone, "Dr. $doctorName")
                }
                is Resource.Error -> {
                    Toast.makeText(this@AppointmentBookingActivity, "Failed to load doctor contact", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    private fun observeViewModel() {
        // Observe doctor availability for the schedule card
        viewModel.availability.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.availabilityProgressBar.visibility = View.VISIBLE
                    binding.availableDaysChipGroup.visibility = View.GONE
                    binding.noAvailabilityText.visibility = View.GONE
                    binding.tapDayHint.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.availabilityProgressBar.visibility = View.GONE
                    availabilityList = result.data
                    if (availabilityList.isEmpty()) {
                        binding.noAvailabilityText.visibility = View.VISIBLE
                        binding.availableDaysChipGroup.visibility = View.GONE
                        binding.tapDayHint.visibility = View.GONE
                    } else {
                        binding.noAvailabilityText.visibility = View.GONE
                        binding.availableDaysChipGroup.visibility = View.VISIBLE
                        binding.tapDayHint.visibility = View.VISIBLE
                        populateAvailabilityChips(availabilityList)
                    }
                }
                is Resource.Error -> {
                    binding.availabilityProgressBar.visibility = View.GONE
                    binding.noAvailabilityText.text = result.message
                    binding.noAvailabilityText.visibility = View.VISIBLE
                    binding.availableDaysChipGroup.visibility = View.GONE
                    binding.tapDayHint.visibility = View.GONE
                }
            }
        }

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
                    // Schedule a reminder notification 30 min before the appointment
                    val appt = result.data
                    if (rescheduleId.isNotEmpty()) {
                        // Cancel old reminder for the rescheduled appointment
                        com.meditrack.app.alarm.AppointmentAlarmScheduler(this)
                            .cancelReminder(rescheduleId)
                    }
                    com.meditrack.app.alarm.AppointmentAlarmScheduler(this)
                        .scheduleReminder(appt)

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
                        tv.setTextColor(ContextCompat.getColor(this@AppointmentBookingActivity, R.color.primary))
                    }
                    tv.alpha = 1f
                    tv.setOnClickListener {
                        selectedSlot = slot
                        updateBookButton()
                        notifyDataSetChanged()
                    }
                } else {
                    tv.setBackgroundResource(R.drawable.bg_time_slot_booked)
                    tv.setTextColor(ContextCompat.getColor(this@AppointmentBookingActivity, R.color.text_tertiary))
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

    /**
     * Populates the availability chips with the doctor's weekly schedule.
     * Each chip shows the day name and time range (e.g., "Mon 9-17").
     */
    private fun populateAvailabilityChips(availabilityList: List<DoctorAvailability>) {
        binding.availableDaysChipGroup.removeAllViews()

        // Sort by day of week and group by day (in case multiple slots per day)
        val byDay = availabilityList
            .filter { it.isActive }
            .sortedBy { it.dayOfWeek }
            .groupBy { it.dayOfWeek }

        for ((dayOfWeek, slots) in byDay) {
            val dayName = getDayShortName(dayOfWeek)
            // Combine time ranges if multiple slots on same day
            val timeRange = slots.joinToString(", ") { "${formatTime(it.startTime)}-${formatTime(it.endTime)}" }

            val chip = Chip(this).apply {
                text = "$dayName\n$timeRange"
                isCheckable = true
                isCheckedIconVisible = false
                setChipBackgroundColorResource(R.color.primary_container)
                setTextColor(ContextCompat.getColor(context, R.color.primary))
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setOnClickListener {
                    selectNextOccurrenceOfDay(dayOfWeek)
                }
            }
            binding.availableDaysChipGroup.addView(chip)
        }
    }

    /**
     * Returns short day name (Mon, Tue, etc.) for ISO day of week.
     */
    private fun getDayShortName(isoDayOfWeek: Int): String = when (isoDayOfWeek) {
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        6 -> "Sat"
        7 -> "Sun"
        else -> "?"
    }

    /**
     * Formats time string from "HH:mm" to shorter form if needed.
     */
    private fun formatTime(time: String): String {
        // Remove leading zero for readability: "09:00" -> "9:00"
        return time.trimStart('0')
    }

    /**
     * Finds the next occurrence of the given ISO day of week (1=Mon, 7=Sun)
     * and auto-selects it in the date picker.
     */
    private fun selectNextOccurrenceOfDay(isoDayOfWeek: Int) {
        val cal = Calendar.getInstance()
        // Convert ISO (1=Mon, 7=Sun) to Calendar (1=Sun, 2=Mon, ... 7=Sat)
        val calendarDow = if (isoDayOfWeek == 7) Calendar.SUNDAY else isoDayOfWeek + 1

        // Find next occurrence (including today if it matches)
        val todayDow = cal.get(Calendar.DAY_OF_WEEK)
        var daysToAdd = calendarDow - todayDow
        if (daysToAdd < 0) {
            daysToAdd += 7
        }
        cal.add(Calendar.DAY_OF_MONTH, daysToAdd)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // Update selected date and UI
        selectedDate = cal.time
        val fmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        binding.selectDateButton.text = fmt.format(cal.time)
        selectedSlot = null
        updateBookButton()

        // Load available slots for the selected date
        viewModel.loadAvailableSlots(doctorId, cal.time)
    }
}
