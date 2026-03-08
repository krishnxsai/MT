package com.example.meditrack.ui.appointment

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.meditrack.R
import com.example.meditrack.data.model.DoctorAvailability
import com.example.meditrack.data.model.Resource
import com.example.meditrack.databinding.ActivityDoctorAvailabilityBinding
import com.google.firebase.auth.FirebaseAuth

class DoctorAvailabilityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDoctorAvailabilityBinding
    private val viewModel: AppointmentViewModel by viewModels()

    private var selectedDay: Int = -1
    private var startTime: String = "09:00"
    private var endTime: String = "17:00"
    private var slotDuration: Int = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDoctorAvailabilityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        try { binding.toolbar.setNavigationOnClickListener { finish() } } catch (_: Exception) {}
        try { binding.backBtn?.setOnClickListener { finish() } } catch (_: Exception) {}

        setupForm()
        setupRecyclerView()
        observeViewModel()

        val doctorId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        viewModel.loadDoctorAvailability(doctorId)
    }

    private fun setupRecyclerView() {
        binding.availabilityRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupForm() {
        // Day picker
        binding.dayPickerButton.setOnClickListener {
            val days = DoctorAvailability.DAY_OPTIONS.map { it.second }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Select Day")
                .setItems(days) { _, which ->
                    selectedDay = DoctorAvailability.DAY_OPTIONS[which].first
                    binding.dayPickerButton.text = DoctorAvailability.DAY_OPTIONS[which].second
                }
                .show()
        }

        // Start time
        binding.startTimeButton.setOnClickListener {
            showTimePicker(startTime) { time ->
                startTime = time
                binding.startTimeButton.text = time
            }
        }

        // End time
        binding.endTimeButton.setOnClickListener {
            showTimePicker(endTime) { time ->
                endTime = time
                binding.endTimeButton.text = time
            }
        }

        // Duration chips
        binding.durationChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            slotDuration = when {
                checkedIds.contains(R.id.chip15min) -> 15
                checkedIds.contains(R.id.chip60min) -> 60
                else -> 30
            }
        }

        // Save button
        binding.saveSlotButton.setOnClickListener {
            if (selectedDay == -1) {
                Toast.makeText(this, "Please select a day", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val slot = DoctorAvailability(
                dayOfWeek = selectedDay,
                startTime = startTime,
                endTime = endTime,
                slotDurationMinutes = slotDuration
            )
            viewModel.saveAvailability(slot)
        }
    }

    private fun showTimePicker(current: String, onPicked: (String) -> Unit) {
        val parts = current.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        TimePickerDialog(this, { _, h, m ->
            onPicked(String.format("%02d:%02d", h, m))
        }, hour, minute, true).show()
    }

    private fun observeViewModel() {
        viewModel.availability.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    val list = result.data
                    if (list.isEmpty()) {
                        binding.emptyStateContainer.visibility = View.VISIBLE
                        binding.availabilityRecyclerView.visibility = View.GONE
                    } else {
                        binding.emptyStateContainer.visibility = View.GONE
                        binding.availabilityRecyclerView.visibility = View.VISIBLE
                        binding.availabilityRecyclerView.adapter = AvailabilityAdapter(list) { slot ->
                            viewModel.deleteAvailability(slot.id)
                        }
                    }
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.actionResult.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                    // Reset form
                    selectedDay = -1
                    binding.dayPickerButton.text = "Select day"
                }
                is Resource.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
                is Resource.Loading -> {}
            }
        }
    }

    /** Simple inline adapter for availability slots. */
    private class AvailabilityAdapter(
        private val items: List<DoctorAvailability>,
        private val onDelete: (DoctorAvailability) -> Unit
    ) : RecyclerView.Adapter<AvailabilityAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val dayText: TextView = view.findViewById(R.id.dayText)
            val timeRangeText: TextView = view.findViewById(R.id.timeRangeText)
            val deleteButton: ImageView = view.findViewById(R.id.deleteButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_availability_slot, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val slot = items[position]
            holder.dayText.text = slot.getDayName()
            holder.timeRangeText.text = "${slot.startTime} – ${slot.endTime} · ${slot.slotDurationMinutes} min slots"
            holder.deleteButton.setOnClickListener { onDelete(slot) }
        }
    }
}

