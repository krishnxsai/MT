package com.meditrack.app.ui.doctor

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.meditrack.app.R
import com.meditrack.app.data.model.*
import com.meditrack.app.databinding.ActivityClinicalDecisionPremiumBinding
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity for doctors to create and manage clinical decisions including:
 * - Prescription orders
 * - Follow-up scheduling
 * - Vital alerts
 * - Recommendations
 * - Patient analysis
 */
@AndroidEntryPoint
class ClinicalDecisionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATIENT_ID = "extra_patient_id"
        const val EXTRA_PATIENT_NAME = "extra_patient_name"
    }

    private lateinit var binding: ActivityClinicalDecisionPremiumBinding
    private val viewModel: ClinicalDecisionViewModel by viewModels()

    private var patientId: String = ""
    private var patientName: String = ""

    private val prescriptionsList = mutableListOf<Prescription>()
    private lateinit var prescriptionAdapter: PrescriptionAdapter

    private var selectedFollowUpDate: Date? = null
    private var selectedDecisionType: ClinicalDecisionType = ClinicalDecisionType.RECOMMENDATION
    private var selectedPriority: Priority = Priority.NORMAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClinicalDecisionPremiumBinding.inflate(layoutInflater)
        setContentView(binding.root)

        patientId = intent.getStringExtra(EXTRA_PATIENT_ID) ?: ""
        patientName = intent.getStringExtra(EXTRA_PATIENT_NAME) ?: ""

        if (patientId.isEmpty()) {
            Toast.makeText(this, "Invalid patient", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        setupDecisionTypeChips()
        setupPriorityChips()
        setupPrescriptionSection()
        setupFollowUpSection()
        setupVisibilityToggle()
        setupSaveButton()
        setupAnalysisSection()
        observeViewModel()

        // Load patient data for analysis
        viewModel.loadPatientDataForAnalysis(patientId)
    }

    private fun setupToolbar() {
        binding.backButton.setOnClickListener { finish() }
        binding.subtitleText.text = "Patient: $patientName"
    }

    private fun setupDecisionTypeChips() {
        binding.chipPrescription.setOnClickListener { selectDecisionType(ClinicalDecisionType.PRESCRIPTION) }
        binding.chipFollowUp.setOnClickListener { selectDecisionType(ClinicalDecisionType.FOLLOW_UP) }
        binding.chipVitalAlert.setOnClickListener { selectDecisionType(ClinicalDecisionType.VITAL_ALERT) }
        binding.chipRecommendation.setOnClickListener { selectDecisionType(ClinicalDecisionType.RECOMMENDATION) }
        binding.chipDiagnosis.setOnClickListener { selectDecisionType(ClinicalDecisionType.DIAGNOSIS) }
        binding.chipLifestyle.setOnClickListener { selectDecisionType(ClinicalDecisionType.LIFESTYLE) }

        // Default selection
        selectDecisionType(ClinicalDecisionType.RECOMMENDATION)
    }

    private fun selectDecisionType(type: ClinicalDecisionType) {
        selectedDecisionType = type

        // Update chip states
        binding.chipPrescription.isChecked = type == ClinicalDecisionType.PRESCRIPTION
        binding.chipFollowUp.isChecked = type == ClinicalDecisionType.FOLLOW_UP
        binding.chipVitalAlert.isChecked = type == ClinicalDecisionType.VITAL_ALERT
        binding.chipRecommendation.isChecked = type == ClinicalDecisionType.RECOMMENDATION
        binding.chipDiagnosis.isChecked = type == ClinicalDecisionType.DIAGNOSIS
        binding.chipLifestyle.isChecked = type == ClinicalDecisionType.LIFESTYLE

        // Show/hide relevant sections
        binding.prescriptionSection.visibility =
            if (type == ClinicalDecisionType.PRESCRIPTION) View.VISIBLE else View.GONE
        binding.followUpSection.visibility =
            if (type == ClinicalDecisionType.FOLLOW_UP) View.VISIBLE else View.GONE
        binding.vitalAlertSection.visibility =
            if (type == ClinicalDecisionType.VITAL_ALERT) View.VISIBLE else View.GONE

        // Update title hint based on type
        binding.titleInput.hint = when (type) {
            ClinicalDecisionType.PRESCRIPTION -> "Prescription Title (e.g., Blood Pressure Medication)"
            ClinicalDecisionType.FOLLOW_UP -> "Follow-up Title (e.g., 2-Week Check-in)"
            ClinicalDecisionType.VITAL_ALERT -> "Alert Title (e.g., High BP Alert)"
            ClinicalDecisionType.DIAGNOSIS -> "Diagnosis (e.g., Hypertension Stage 1)"
            ClinicalDecisionType.LIFESTYLE -> "Lifestyle Advice Title"
            else -> "Recommendation Title"
        }
    }

    private fun setupPriorityChips() {
        binding.chipPriorityLow.setOnClickListener { selectPriority(Priority.LOW) }
        binding.chipPriorityNormal.setOnClickListener { selectPriority(Priority.NORMAL) }
        binding.chipPriorityHigh.setOnClickListener { selectPriority(Priority.HIGH) }
        binding.chipPriorityCritical.setOnClickListener { selectPriority(Priority.CRITICAL) }

        selectPriority(Priority.NORMAL)
    }

    private fun selectPriority(priority: Priority) {
        selectedPriority = priority

        binding.chipPriorityLow.isChecked = priority == Priority.LOW
        binding.chipPriorityNormal.isChecked = priority == Priority.NORMAL
        binding.chipPriorityHigh.isChecked = priority == Priority.HIGH
        binding.chipPriorityCritical.isChecked = priority == Priority.CRITICAL
    }

    private fun setupPrescriptionSection() {
        prescriptionAdapter = PrescriptionAdapter(
            prescriptions = prescriptionsList,
            onEditClick = { position -> showEditPrescriptionDialog(position) },
            onDeleteClick = { position ->
                prescriptionsList.removeAt(position)
                prescriptionAdapter.notifyDataSetChanged()
                updatePrescriptionCount()
            }
        )

        binding.prescriptionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ClinicalDecisionActivity)
            adapter = prescriptionAdapter
        }

        binding.addPrescriptionButton.setOnClickListener {
            showAddPrescriptionDialog()
        }

        updatePrescriptionCount()
    }

    private fun showAddPrescriptionDialog() {
        showPrescriptionDialog(null) { prescription ->
            prescriptionsList.add(prescription)
            prescriptionAdapter.notifyDataSetChanged()
            updatePrescriptionCount()
        }
    }

    private fun showEditPrescriptionDialog(position: Int) {
        showPrescriptionDialog(prescriptionsList[position]) { prescription ->
            prescriptionsList[position] = prescription
            prescriptionAdapter.notifyDataSetChanged()
        }
    }

    private fun showPrescriptionDialog(existing: Prescription?, onSave: (Prescription) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_prescription, null)

        val medicationNameInput = dialogView.findViewById<TextInputEditText>(R.id.medicationNameInput)
        val dosageInput = dialogView.findViewById<TextInputEditText>(R.id.dosageInput)
        val unitSpinner = dialogView.findViewById<Spinner>(R.id.unitSpinner)
        val frequencySpinner = dialogView.findViewById<Spinner>(R.id.frequencySpinner)
        val routeSpinner = dialogView.findViewById<Spinner>(R.id.routeSpinner)
        val durationInput = dialogView.findViewById<TextInputEditText>(R.id.durationInput)
        val instructionsInput = dialogView.findViewById<TextInputEditText>(R.id.instructionsInput)
        val refillsInput = dialogView.findViewById<TextInputEditText>(R.id.refillsInput)
        val warningsInput = dialogView.findViewById<TextInputEditText>(R.id.warningsInput)
        val substitutionCheckbox = dialogView.findViewById<CheckBox>(R.id.substitutionCheckbox)

        // Setup spinners
        unitSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, MedicationUnits.UNITS)
        frequencySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, MedicationFrequencies.FREQUENCIES)
        routeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, MedicationRoutes.ROUTES)

        // Pre-fill if editing
        existing?.let { rx ->
            medicationNameInput.setText(rx.medicationName)
            dosageInput.setText(rx.dosage)
            unitSpinner.setSelection(MedicationUnits.UNITS.indexOf(rx.unit).coerceAtLeast(0))
            frequencySpinner.setSelection(MedicationFrequencies.FREQUENCIES.indexOf(rx.frequency).coerceAtLeast(0))
            routeSpinner.setSelection(MedicationRoutes.ROUTES.indexOf(rx.route).coerceAtLeast(0))
            durationInput.setText(rx.duration)
            instructionsInput.setText(rx.instructions)
            refillsInput.setText(rx.refills.toString())
            warningsInput.setText(rx.warnings.joinToString("\n"))
            substitutionCheckbox.isChecked = rx.substitutionAllowed
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add Prescription" else "Edit Prescription")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val medName = medicationNameInput.text.toString().trim()
                if (medName.isEmpty()) {
                    Toast.makeText(this, "Please enter medication name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val prescription = Prescription(
                    medicationName = medName,
                    dosage = dosageInput.text.toString().trim(),
                    unit = unitSpinner.selectedItem.toString(),
                    frequency = frequencySpinner.selectedItem.toString(),
                    route = routeSpinner.selectedItem.toString(),
                    duration = durationInput.text.toString().trim(),
                    instructions = instructionsInput.text.toString().trim(),
                    refills = refillsInput.text.toString().toIntOrNull() ?: 0,
                    warnings = warningsInput.text.toString().split("\n").filter { it.isNotBlank() },
                    substitutionAllowed = substitutionCheckbox.isChecked,
                    isActive = true,
                    startDate = Date()
                )
                onSave(prescription)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updatePrescriptionCount() {
        binding.prescriptionCountText.text = "${prescriptionsList.size} medication(s) added"
    }

    private fun setupFollowUpSection() {
        binding.selectDateButton.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 7) // Default to 1 week from now

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                selectedFollowUpDate = calendar.time
                updateFollowUpDateDisplay()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }

    private fun updateFollowUpDateDisplay() {
        selectedFollowUpDate?.let {
            val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
            binding.selectedDateText.text = dateFormat.format(it)
            binding.selectedDateText.visibility = View.VISIBLE
        }
    }

    private fun setupVisibilityToggle() {
        binding.visibilitySwitch.isChecked = true // Public by default
        binding.visibilitySwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.visibilityDescription.text = if (isChecked) {
                "Patient can see this recommendation"
            } else {
                "Only visible to medical staff"
            }
        }
    }

    private fun setupSaveButton() {
        binding.saveButton.setOnClickListener {
            saveClinicalDecision()
        }
    }

    private fun saveClinicalDecision() {
        val title = binding.titleInput.text.toString().trim()
        val description = binding.descriptionInput.text.toString().trim()

        if (title.isEmpty()) {
            binding.titleInputLayout.error = "Title is required"
            return
        }
        binding.titleInputLayout.error = null

        // Validate type-specific requirements
        when (selectedDecisionType) {
            ClinicalDecisionType.PRESCRIPTION -> {
                if (prescriptionsList.isEmpty()) {
                    Toast.makeText(this, "Please add at least one prescription", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            ClinicalDecisionType.FOLLOW_UP -> {
                if (selectedFollowUpDate == null) {
                    Toast.makeText(this, "Please select a follow-up date", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            else -> { /* No additional validation */ }
        }

        val decision = ClinicalDecision(
            patientId = patientId,
            patientName = patientName,
            type = selectedDecisionType,
            priority = selectedPriority,
            status = DecisionStatus.ACTIVE,
            title = title,
            description = description,
            isPublic = binding.visibilitySwitch.isChecked,
            prescriptions = if (selectedDecisionType == ClinicalDecisionType.PRESCRIPTION) prescriptionsList else emptyList(),
            followUpDate = if (selectedDecisionType == ClinicalDecisionType.FOLLOW_UP) selectedFollowUpDate else null,
            followUpInstructions = if (selectedDecisionType == ClinicalDecisionType.FOLLOW_UP) {
                binding.followUpNotesInput.text.toString().trim()
            } else "",
            analysisSummary = binding.analysisText.text.toString()
        )

        viewModel.createClinicalDecision(decision)
    }

    private fun setupAnalysisSection() {
        // Analysis is displayed automatically when patient data is loaded
    }

    private fun observeViewModel() {
        viewModel.saveResult.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.saveButton.isEnabled = false
                    binding.loadingOverlay.visibility = View.VISIBLE
                }
                is Resource.Success -> {
                    binding.saveButton.isEnabled = true
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, "Clinical decision saved successfully", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
                is Resource.Error -> {
                    binding.saveButton.isEnabled = true
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.vitalAnalysis.observe(this) { analysis ->
            analysis?.let { displayAnalysis(it) }
        }

        viewModel.patientLogs.observe(this) { result ->
            if (result is Resource.Success) {
                val symptoms = result.data.flatMap { it.symptoms }.distinct()
                displayRecentSymptoms(symptoms)
            }
        }
    }

    private fun displayAnalysis(analysis: VitalAnalysis) {
        binding.analysisSection.visibility = View.VISIBLE

        val sb = StringBuilder()
        sb.appendLine("📊 VITAL SIGNS ANALYSIS")
        sb.appendLine("=" .repeat(30))

        analysis.heartRateAvg?.let {
            sb.appendLine("\n❤️ Heart Rate:")
            sb.appendLine("   Average: ${it.toInt()} bpm")
            sb.appendLine("   Range: ${analysis.heartRateMin?.toInt() ?: "N/A"} - ${analysis.heartRateMax?.toInt() ?: "N/A"} bpm")
            sb.appendLine("   Trend: ${analysis.heartRateTrend.replaceFirstChar { c -> c.uppercase() }}")
        }

        if (analysis.bpSystolicAvg != null && analysis.bpDiastolicAvg != null) {
            sb.appendLine("\n🩺 Blood Pressure:")
            sb.appendLine("   Average: ${analysis.bpSystolicAvg.toInt()}/${analysis.bpDiastolicAvg.toInt()} mmHg")
            sb.appendLine("   Trend: ${analysis.bpTrend.replaceFirstChar { c -> c.uppercase() }}")
        }

        analysis.glucoseAvg?.let {
            sb.appendLine("\n🍬 Glucose:")
            sb.appendLine("   Average: ${it.toInt()} mg/dL")
            sb.appendLine("   Trend: ${analysis.glucoseTrend.replaceFirstChar { c -> c.uppercase() }}")
        }

        analysis.temperatureAvg?.let {
            sb.appendLine("\n🌡️ Temperature:")
            sb.appendLine("   Average: ${"%.1f".format(it)}°C")
        }

        if (analysis.concerns.isNotEmpty()) {
            sb.appendLine("\n⚠️ CONCERNS:")
            analysis.concerns.forEach { concern ->
                sb.appendLine("   • $concern")
            }
        }

        if (analysis.recommendations.isNotEmpty()) {
            sb.appendLine("\n💡 RECOMMENDATIONS:")
            analysis.recommendations.forEach { rec ->
                sb.appendLine("   • $rec")
            }
        }

        sb.appendLine("\n📋 Overall Assessment:")
        sb.appendLine("   ${analysis.overallAssessment}")
        sb.appendLine("\n🎯 Risk Level: ${analysis.riskLevel.uppercase()}")

        binding.analysisText.text = sb.toString()
    }

    private fun displayRecentSymptoms(symptoms: List<String>) {
        // Symptoms display not included in premium layout
        // Just store for analysis if needed
    }
}

/**
 * Adapter for displaying prescriptions in the list
 */
class PrescriptionAdapter(
    private val prescriptions: MutableList<Prescription>,
    private val onEditClick: (Int) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<PrescriptionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val medicationName: TextView = view.findViewById(R.id.medicationNameText)
        val dosageInfo: TextView = view.findViewById(R.id.dosageInfoText)
        val frequencyInfo: TextView = view.findViewById(R.id.frequencyInfoText)
        val instructionsInfo: TextView = view.findViewById(R.id.instructionsInfoText)
        val editButton: ImageButton = view.findViewById(R.id.editButton)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prescription_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val prescription = prescriptions[position]

        holder.medicationName.text = prescription.medicationName
        holder.dosageInfo.text = "${prescription.dosage} ${prescription.unit}"
        holder.frequencyInfo.text = "${prescription.frequency} • ${prescription.route}"
        holder.instructionsInfo.text = prescription.instructions.ifEmpty { "No special instructions" }
        holder.instructionsInfo.visibility = if (prescription.instructions.isEmpty()) View.GONE else View.VISIBLE

        holder.editButton.setOnClickListener { onEditClick(position) }
        holder.deleteButton.setOnClickListener { onDeleteClick(position) }
    }

    override fun getItemCount() = prescriptions.size
}

