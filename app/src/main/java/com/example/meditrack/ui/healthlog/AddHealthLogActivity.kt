package com.example.meditrack.ui.healthlog

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.meditrack.R
import com.example.meditrack.data.model.HealthLog
import com.example.meditrack.data.model.Resource
import com.example.meditrack.databinding.ActivityAddHealthLogBinding
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddHealthLogActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LOG_ID = "extra_log_id"
    }

    private lateinit var binding: ActivityAddHealthLogBinding
    private val viewModel: HealthLogViewModel by viewModels()

    private var editingLogId: String? = null
    private var existingLog: HealthLog? = null
    private var selectedDate: Date = Date()
    private val selectedSymptoms = mutableListOf<String>()

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddHealthLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        editingLogId = intent.getStringExtra(EXTRA_LOG_ID)

        setupToolbar()
        setupDateTimePickers()
        setupSymptomChips()
        setupClickListeners()
        observeViewModel()

        if (editingLogId != null) {
            loadHealthLog()
        } else {
            updateDateTimeDisplay()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        if (editingLogId != null) {
            binding.toolbar.title = "Edit Health Log"
        }
    }

    private fun setupDateTimePickers() {
        binding.dateButton.setOnClickListener {
            showDatePicker()
        }

        binding.timeButton.setOnClickListener {
            showTimePicker()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        calendar.time = selectedDate

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val newCalendar = Calendar.getInstance()
                newCalendar.time = selectedDate
                newCalendar.set(Calendar.YEAR, year)
                newCalendar.set(Calendar.MONTH, month)
                newCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                selectedDate = newCalendar.time
                updateDateTimeDisplay()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        calendar.time = selectedDate

        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val newCalendar = Calendar.getInstance()
                newCalendar.time = selectedDate
                newCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                newCalendar.set(Calendar.MINUTE, minute)
                selectedDate = newCalendar.time
                updateDateTimeDisplay()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun updateDateTimeDisplay() {
        binding.dateButton.text = dateFormat.format(selectedDate)
        binding.timeButton.text = timeFormat.format(selectedDate)
    }

    private fun setupSymptomChips() {
        val symptoms = HealthLog.COMMON_SYMPTOMS

        symptoms.forEach { symptom ->
            val chip = Chip(this).apply {
                text = symptom
                isCheckable = true
                isCheckedIconVisible = true
                setChipBackgroundColorResource(R.color.surface_variant)
                setTextColor(getColor(R.color.text_primary))
                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(20f * resources.displayMetrics.density)
                    .build()

                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedSymptoms.add(symptom)
                        setChipBackgroundColorResource(R.color.primary_container)
                    } else {
                        selectedSymptoms.remove(symptom)
                        setChipBackgroundColorResource(R.color.surface_variant)
                    }
                }
            }
            binding.symptomsChipGroup.addView(chip)
        }
    }

    private fun setupClickListeners() {
        binding.saveButton.setOnClickListener {
            saveHealthLog()
        }
    }

    private fun saveHealthLog() {
        val heartRate = binding.heartRateInput.text.toString().toIntOrNull()
        val systolic = binding.systolicInput.text.toString().toIntOrNull()
        val diastolic = binding.diastolicInput.text.toString().toIntOrNull()
        val glucose = binding.glucoseInput.text.toString().toDoubleOrNull()
        val weight = binding.weightInput.text.toString().toDoubleOrNull()
        val temperature = binding.temperatureInput.text.toString().toDoubleOrNull()
        val notes = binding.notesInput.text.toString().trim()

        // Validate at least one field is filled
        if (heartRate == null && systolic == null && diastolic == null &&
            glucose == null && weight == null && temperature == null &&
            selectedSymptoms.isEmpty() && notes.isEmpty()) {
            Toast.makeText(this, "Please fill in at least one field", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate blood pressure (both or neither)
        if ((systolic != null && diastolic == null) || (systolic == null && diastolic != null)) {
            Toast.makeText(this, "Please enter both systolic and diastolic values", Toast.LENGTH_SHORT).show()
            return
        }

        val healthLog = HealthLog(
            id = editingLogId ?: "",
            userId = "", // Will be set by repository
            date = selectedDate,
            heartRate = heartRate,
            bloodPressureSystolic = systolic,
            bloodPressureDiastolic = diastolic,
            glucoseLevel = glucose,
            weight = weight,
            temperature = temperature,
            symptoms = selectedSymptoms.toList(),
            notes = notes
        )

        if (editingLogId != null) {
            viewModel.updateHealthLog(healthLog)
        } else {
            viewModel.addHealthLog(healthLog)
        }
    }

    private fun observeViewModel() {
        viewModel.saveResult.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.saveButton.isEnabled = false
                    binding.saveButton.text = "Saving..."
                }
                is Resource.Success -> {
                    Toast.makeText(
                        this,
                        if (editingLogId != null) "Health log updated" else "Health log saved",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
                is Resource.Error -> {
                    binding.saveButton.isEnabled = true
                    binding.saveButton.text = "Save Log"
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
                null -> {}
            }
        }

        viewModel.currentLog.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.saveButton.isEnabled = false
                }
                is Resource.Success -> {
                    binding.saveButton.isEnabled = true
                    populateFields(result.data)
                }
                is Resource.Error -> {
                    binding.saveButton.isEnabled = true
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun loadHealthLog() {
        editingLogId?.let { viewModel.getHealthLog(it) }
    }

    private fun populateFields(healthLog: HealthLog) {
        existingLog = healthLog
        selectedDate = healthLog.date
        updateDateTimeDisplay()

        healthLog.heartRate?.let { binding.heartRateInput.setText(it.toString()) }
        healthLog.bloodPressureSystolic?.let { binding.systolicInput.setText(it.toString()) }
        healthLog.bloodPressureDiastolic?.let { binding.diastolicInput.setText(it.toString()) }
        healthLog.glucoseLevel?.let { binding.glucoseInput.setText(it.toString()) }
        healthLog.weight?.let { binding.weightInput.setText(it.toString()) }
        healthLog.temperature?.let { binding.temperatureInput.setText(it.toString()) }
        binding.notesInput.setText(healthLog.notes)

        // Check symptoms
        selectedSymptoms.clear()
        selectedSymptoms.addAll(healthLog.symptoms)
        for (i in 0 until binding.symptomsChipGroup.childCount) {
            val chip = binding.symptomsChipGroup.getChildAt(i) as? Chip
            chip?.isChecked = healthLog.symptoms.contains(chip?.text?.toString())
        }

        binding.saveButton.text = "Update Log"
        binding.toolbar.title = "Edit Health Log"
    }
}

