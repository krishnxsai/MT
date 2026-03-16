package com.meditrack.app.ui.medicine

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.meditrack.app.R
import com.meditrack.app.alarm.AlarmScheduler
import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.RepeatType
import com.meditrack.app.data.model.Resource
import com.meditrack.app.databinding.ActivityAddMedicineBinding
import com.google.android.material.chip.Chip
import java.util.Calendar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AddMedicineActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MEDICINE_ID = "extra_medicine_id"
    }

    private lateinit var binding: ActivityAddMedicineBinding
    private val viewModel: MedicineViewModel by viewModels()
    private lateinit var alarmScheduler: AlarmScheduler

    private var editingMedicineId: String? = null
    private var existingMedicine: Medicine? = null
    private val reminderTimes = mutableListOf<String>()
    private var selectedColor = "teal"
    private var selectedRepeatType = RepeatType.DAILY

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission required for reminders", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddMedicineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alarmScheduler = AlarmScheduler(this)

        editingMedicineId = intent.getStringExtra(EXTRA_MEDICINE_ID)

        setupToolbar()
        setupUnitDropdown()
        setupRepeatToggle()
        setupColorSelection()
        setupClickListeners()
        observeViewModel()
        requestNotificationPermission()

        if (editingMedicineId != null) {
            loadMedicine()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        if (editingMedicineId != null) {
            binding.toolbar.title = getString(R.string.edit_profile) // TODO: Add "Edit Medicine" string
        }
    }

    private fun setupUnitDropdown() {
        val units = arrayOf(
            getString(R.string.unit_mg),
            getString(R.string.unit_ml),
            getString(R.string.unit_tablet),
            getString(R.string.unit_capsule),
            getString(R.string.unit_drops),
            getString(R.string.unit_puff)
        )
        val adapter = ArrayAdapter(this, R.layout.item_dropdown, units)
        binding.unitDropdown.setAdapter(adapter)
    }

    private fun setupRepeatToggle() {
        binding.repeatToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedRepeatType = when (checkedId) {
                    R.id.dailyButton -> RepeatType.DAILY
                    R.id.weeklyButton -> RepeatType.WEEKLY
                    R.id.asNeededButton -> RepeatType.AS_NEEDED
                    else -> RepeatType.DAILY
                }
            }
        }
    }

    private fun setupColorSelection() {
        val colorViews = listOf(
            binding.colorRed to "red",
            binding.colorOrange to "orange",
            binding.colorYellow to "yellow",
            binding.colorGreen to "green",
            binding.colorTeal to "teal",
            binding.colorBlue to "blue",
            binding.colorPurple to "purple",
            binding.colorPink to "pink"
        )

        val checkViews = listOf(
            binding.colorRedCheck,
            binding.colorOrangeCheck,
            binding.colorYellowCheck,
            binding.colorGreenCheck,
            binding.colorTealCheck,
            binding.colorBlueCheck,
            binding.colorPurpleCheck,
            binding.colorPinkCheck
        )

        colorViews.forEach { (view, color) ->
            view.setOnClickListener {
                selectedColor = color
                updateColorSelection(checkViews, color)
            }
        }
    }

    private fun updateColorSelection(checkViews: List<View>, selectedColor: String) {
        val colors = listOf("red", "orange", "yellow", "green", "teal", "blue", "purple", "pink")
        colors.forEachIndexed { index, color ->
            checkViews[index].visibility = if (color == selectedColor) View.VISIBLE else View.GONE
        }

        // Update stroke color
        val colorCards = listOf(
            binding.colorRed, binding.colorOrange, binding.colorYellow, binding.colorGreen,
            binding.colorTeal, binding.colorBlue, binding.colorPurple, binding.colorPink
        )
        colorCards.forEachIndexed { index, card ->
            if (colors[index] == selectedColor) {
                card.strokeColor = ContextCompat.getColor(this, R.color.primary)
                card.strokeWidth = 3
            } else {
                card.strokeColor = ContextCompat.getColor(this, android.R.color.transparent)
                card.strokeWidth = 0
            }
        }
    }

    private fun setupClickListeners() {
        binding.addTimeButton.setOnClickListener {
            showTimePicker()
        }

        binding.saveMedicineButton.setOnClickListener {
            if (validateInputs()) {
                saveMedicine()
            }
        }
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                val timeString = String.format("%02d:%02d", selectedHour, selectedMinute)
                if (!reminderTimes.contains(timeString)) {
                    reminderTimes.add(timeString)
                    addTimeChip(timeString)
                    updateEmptyTimesVisibility()
                }
            },
            hour,
            minute,
            false
        ).show()
    }

    private fun addTimeChip(time: String) {
        val chip = Chip(this).apply {
            text = formatTimeForDisplay(time)
            isCloseIconVisible = true
            setChipBackgroundColorResource(R.color.primary_container)
            setTextColor(ContextCompat.getColor(context, R.color.primary))
            setCloseIconTintResource(R.color.text_tertiary)
            chipIcon = ContextCompat.getDrawable(context, R.drawable.ic_clock)
            setChipIconTintResource(R.color.primary)
            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(20f.dpToPx())
                .build()
            chipMinHeight = 44f.dpToPx()

            setOnCloseIconClickListener {
                reminderTimes.remove(time)
                binding.timesChipGroup.removeView(this)
                updateEmptyTimesVisibility()
            }
        }

        binding.timesChipGroup.addView(chip)
    }

    private fun formatTimeForDisplay(time: String): String {
        return try {
            val parts = time.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1]
            val amPm = if (hour >= 12) "PM" else "AM"
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            "$displayHour:$minute $amPm"
        } catch (e: Exception) {
            time
        }
    }

    private fun updateEmptyTimesVisibility() {
        binding.emptyTimesContainer.visibility = if (reminderTimes.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun Float.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }

    private fun validateInputs(): Boolean {
        val name = binding.medicineNameInput.text.toString().trim()
        val dosage = binding.dosageInput.text.toString().trim()
        val unit = binding.unitDropdown.text.toString().trim()

        if (name.isEmpty()) {
            binding.medicineNameLayout.error = getString(R.string.error_empty_medicine_name)
            return false
        } else {
            binding.medicineNameLayout.error = null
        }

        if (dosage.isEmpty()) {
            binding.dosageLayout.error = getString(R.string.error_empty_dosage)
            return false
        } else {
            binding.dosageLayout.error = null
        }

        if (unit.isEmpty()) {
            binding.unitLayout.error = getString(R.string.error_select_unit)
            return false
        } else {
            binding.unitLayout.error = null
        }

        if (reminderTimes.isEmpty() && selectedRepeatType != RepeatType.AS_NEEDED) {
            Toast.makeText(this, getString(R.string.error_add_reminder), Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun saveMedicine() {
        val medicine = Medicine(
            id = editingMedicineId ?: "",
            userId = existingMedicine?.userId ?: "", // Preserve existing userId for updates, repository sets for new
            name = binding.medicineNameInput.text.toString().trim(),
            dosage = binding.dosageInput.text.toString().trim(),
            unit = binding.unitDropdown.text.toString().trim(),
            instructions = binding.instructionsInput.text.toString().trim(),
            reminderTimes = reminderTimes.toList(),
            repeatType = selectedRepeatType,
            color = selectedColor,
            isActive = existingMedicine?.isActive ?: true,
            alarmIds = existingMedicine?.alarmIds ?: emptyList(),
            prescriptionId = existingMedicine?.prescriptionId ?: "",
            prescribedByDoctor = existingMedicine?.prescribedByDoctor ?: false,
            // Preserve refill tracking fields
            currentQuantity = existingMedicine?.currentQuantity ?: -1,
            totalQuantity = existingMedicine?.totalQuantity ?: -1,
            lowStockThreshold = existingMedicine?.lowStockThreshold ?: 5,
            refillReminderEnabled = existingMedicine?.refillReminderEnabled ?: false,
            lastRefillDate = existingMedicine?.lastRefillDate
        )

        if (editingMedicineId != null) {
            // Cancel existing alarms
            existingMedicine?.let { alarmScheduler.cancelMedicineAlarms(it.alarmIds) }
            viewModel.updateMedicine(medicine)
        } else {
            viewModel.addMedicine(medicine)
        }
    }

    private fun observeViewModel() {
        viewModel.saveMedicineResult.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.saveMedicineButton.isEnabled = false
                    binding.saveMedicineButton.text = "Saving..."
                }
                is Resource.Success -> {
                    val savedMedicine = result.data

                    // Schedule alarms
                    val alarmIds = alarmScheduler.scheduleMedicineAlarms(savedMedicine)
                    if (alarmIds.isNotEmpty()) {
                        viewModel.updateAlarmIds(savedMedicine.id, alarmIds)
                    }

                    Toast.makeText(
                        this,
                        if (editingMedicineId != null) getString(R.string.medicine_updated)
                        else getString(R.string.medicine_saved),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
                is Resource.Error -> {
                    binding.saveMedicineButton.isEnabled = true
                    binding.saveMedicineButton.text = getString(R.string.save_medicine)
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
                null -> {}
            }
        }

        viewModel.currentMedicine.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.saveMedicineButton.isEnabled = false
                }
                is Resource.Success -> {
                    binding.saveMedicineButton.isEnabled = true
                    populateFields(result.data)
                }
                is Resource.Error -> {
                    binding.saveMedicineButton.isEnabled = true
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun loadMedicine() {
        editingMedicineId?.let { viewModel.getMedicine(it) }
    }

    private fun populateFields(medicine: Medicine) {
        existingMedicine = medicine

        binding.medicineNameInput.setText(medicine.name)
        binding.dosageInput.setText(medicine.dosage)
        binding.unitDropdown.setText(medicine.unit, false)
        binding.instructionsInput.setText(medicine.instructions)

        // Reminder times
        reminderTimes.clear()
        reminderTimes.addAll(medicine.reminderTimes)
        binding.timesChipGroup.removeAllViews()
        medicine.reminderTimes.forEach { addTimeChip(it) }
        updateEmptyTimesVisibility()

        // Repeat type
        selectedRepeatType = medicine.repeatType
        when (medicine.repeatType) {
            RepeatType.DAILY -> binding.dailyButton.isChecked = true
            RepeatType.WEEKLY -> binding.weeklyButton.isChecked = true
            RepeatType.AS_NEEDED -> binding.asNeededButton.isChecked = true
        }

        // Color
        selectedColor = medicine.color
        val checkViews = listOf(
            binding.colorRedCheck, binding.colorOrangeCheck, binding.colorYellowCheck,
            binding.colorGreenCheck, binding.colorTealCheck, binding.colorBlueCheck,
            binding.colorPurpleCheck, binding.colorPinkCheck
        )
        updateColorSelection(checkViews, selectedColor)

        // Update button text
        binding.saveMedicineButton.text = getString(R.string.update_medicine)
        binding.toolbar.title = "Edit Medicine"
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}