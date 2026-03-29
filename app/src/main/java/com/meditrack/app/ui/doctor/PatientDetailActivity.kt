package com.meditrack.app.ui.doctor

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.meditrack.app.R
import com.meditrack.app.data.analytics.HealthAnalytics
import com.meditrack.app.data.model.DoctorNote
import com.meditrack.app.data.model.HealthLog
import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.MedicationFrequencies
import com.meditrack.app.data.model.MedicationRoutes
import com.meditrack.app.data.model.MedicationUnits
import com.meditrack.app.data.model.NoteCategory
import com.meditrack.app.data.model.PrescriptionRecord
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.User
import com.meditrack.app.databinding.ActivityPatientDetailPremiumBinding
import com.meditrack.app.ui.appointment.AppointmentBookingActivity
import com.meditrack.app.ui.chat.ChatActivity
import com.meditrack.app.ui.chat.ChatListViewModel
import com.meditrack.app.ui.healthlog.HealthLogAdapter
import com.meditrack.app.ui.medicine.MedicineAdapter
import com.meditrack.app.util.CallUtils
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PatientDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATIENT_ID = "extra_patient_id"
        const val EXTRA_PATIENT_NAME = "extra_patient_name"
        private const val REQUEST_CLINICAL_DECISION = 1001
    }

    private lateinit var binding: ActivityPatientDetailPremiumBinding
    private val viewModel: DoctorViewModel by viewModels()
    private val prescriptionViewModel: DoctorPrescriptionViewModel by viewModels()
    private val chatListViewModel: ChatListViewModel by viewModels()

    private lateinit var medicineAdapter: MedicineAdapter
    private lateinit var healthLogAdapter: HealthLogAdapter
    private lateinit var noteAdapter: DoctorNoteAdapter

    private var patientId: String = ""
    private var patientName: String = ""
    private var patientPhone: String = ""
    private var currentHealthLogs: List<HealthLog> = emptyList()
    private var currentPrescriptions: List<PrescriptionRecord> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPatientDetailPremiumBinding.inflate(layoutInflater)
        setContentView(binding.root)

        patientId = intent.getStringExtra(EXTRA_PATIENT_ID) ?: ""
        patientName = intent.getStringExtra(EXTRA_PATIENT_NAME) ?: ""

        if (patientId.isEmpty()) {
            Toast.makeText(this, "Invalid patient", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        setupTabs()
        setupRecyclerViews()
        setupClickListeners()
        observeViewModel()
        loadData()
    }

    private fun setupToolbar() {
        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showMedicinesTab()
                    1 -> showHealthLogsTab()
                    2 -> showNotesTab()
                    3 -> showTrendsTab()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerViews() {
        // Medicines adapter — tap shows prescription actions
        medicineAdapter = MedicineAdapter(
            onItemClick = { medicine -> showMedicineActionsDialog(medicine) },
            onToggleClick = { _, _ -> /* Doctors can't toggle directly */ }
        )

        // Health logs adapter (read-only)
        healthLogAdapter = HealthLogAdapter(
            onItemClick = { /* View only */ },
            onDeleteClick = { /* Doctors can't delete patient logs */ }
        )

        // Doctor notes adapter
        noteAdapter = DoctorNoteAdapter(
            onNoteClick = { note -> showEditNoteDialog(note) },
            onDeleteClick = { note -> showDeleteNoteConfirmation(note) }
        )

        binding.medicinesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PatientDetailActivity)
            adapter = medicineAdapter
        }

        binding.healthLogsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PatientDetailActivity)
            adapter = healthLogAdapter
        }

        binding.notesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PatientDetailActivity)
            adapter = noteAdapter
        }
    }

    private fun setupClickListeners() {
        binding.addNoteFab.setOnClickListener {
            showAddNoteDialog()
        }

        // Clinical Decision button
        binding.clinicalDecisionButton.setOnClickListener {
            val intent = Intent(this, ClinicalDecisionActivity::class.java).apply {
                putExtra(ClinicalDecisionActivity.EXTRA_PATIENT_ID, patientId)
                putExtra(ClinicalDecisionActivity.EXTRA_PATIENT_NAME, patientName)
            }
            startActivity(intent)
        }

        // Call Patient button
        binding.callPatientButton.setOnClickListener {
            CallUtils.dialPhoneNumber(this, patientPhone, patientName)
        }

        // Chat with Patient button
        binding.chatWithPatientButton.setOnClickListener {
            chatListViewModel.getOrCreateConversation(patientId) { result ->
                when (result) {
                    is Resource.Success -> {
                        val conv = result.data
                        val intent = Intent(this, ChatActivity::class.java).apply {
                            putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conv.id)
                            putExtra(ChatActivity.EXTRA_OTHER_USER_NAME, patientName)
                            putExtra(ChatActivity.EXTRA_OTHER_USER_IMAGE, conv.patientProfileUrl)
                        }
                        startActivity(intent)
                    }
                    is Resource.Error -> {
                        Toast.makeText(this, "Failed to open chat: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                    is Resource.Loading -> {}
                }
            }
        }

        // Book Appointment button
        binding.bookAppointmentButton.setOnClickListener {
            val currentDoctor = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val intent = Intent(this, AppointmentBookingActivity::class.java).apply {
                putExtra(AppointmentBookingActivity.EXTRA_DOCTOR_ID, currentDoctor?.uid ?: "")
                putExtra(AppointmentBookingActivity.EXTRA_DOCTOR_NAME, currentDoctor?.displayName ?: "")
                putExtra(AppointmentBookingActivity.EXTRA_PATIENT_ID, patientId)
                putExtra(AppointmentBookingActivity.EXTRA_PATIENT_NAME, patientName)
            }
            startActivity(intent)
        }
    }

    private fun observeViewModel() {
        // Patient details
        viewModel.selectedPatient.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.loadingOverlay.visibility = View.VISIBLE
                }
                is Resource.Success -> {
                    binding.loadingOverlay.visibility = View.GONE
                    updatePatientHeader(result.data)
                }
                is Resource.Error -> {
                    binding.loadingOverlay.visibility = View.GONE
                    if (!isTransientFirestoreError(result.message)) {
                        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Medicines
        viewModel.patientMedicines.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    updateMedicinesList(result.data)
                }
                is Resource.Error -> {
                    if (!isTransientFirestoreError(result.message)) {
                        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {}
            }
        }

        // Health logs
        viewModel.patientHealthLogs.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    updateHealthLogsList(result.data)
                }
                is Resource.Error -> {
                    if (!isTransientFirestoreError(result.message)) {
                        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {}
            }
        }

        // Notes
        viewModel.patientNotes.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    updateNotesList(result.data)
                }
                is Resource.Error -> {
                    if (!isTransientFirestoreError(result.message)) {
                        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {}
            }
        }

        // Save note result
        viewModel.saveNoteResult.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
                    viewModel.clearSaveNoteResult()
                }
                is Resource.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        // Delete note result
        viewModel.deleteNoteResult.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show()
                    viewModel.clearDeleteNoteResult()
                }
                is Resource.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        // ── Prescription observers ──────────────────────────────
        prescriptionViewModel.prescriptions.observe(this) { result ->
            if (result is Resource.Success) {
                currentPrescriptions = result.data
            }
        }

        prescriptionViewModel.saveResult.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    Toast.makeText(this, "Prescription saved", Toast.LENGTH_SHORT).show()
                    prescriptionViewModel.clearSaveResult()
                }
                is Resource.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    prescriptionViewModel.clearSaveResult()
                }
                else -> {}
            }
        }

        prescriptionViewModel.stopResult.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    Toast.makeText(this, "Prescription stopped", Toast.LENGTH_SHORT).show()
                    prescriptionViewModel.clearStopResult()
                }
                is Resource.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    prescriptionViewModel.clearStopResult()
                }
                else -> {}
            }
        }

        prescriptionViewModel.duplicateWarning.observe(this) { warning ->
            if (warning != null) {
                Snackbar.make(binding.root, warning, Snackbar.LENGTH_LONG).show()
                prescriptionViewModel.clearDuplicateWarning()
            }
        }
    }

    private fun loadData() {
        viewModel.loadPatientDetails(patientId)
        viewModel.loadPatientMedicines(patientId)
        viewModel.loadPatientHealthLogs(patientId)
        viewModel.loadPatientNotes(patientId)
        prescriptionViewModel.loadPatientPrescriptions(patientId)
    }

    private fun updatePatientHeader(patient: User) {
        binding.patientName.text = patient.displayName.ifEmpty { "Unknown" }
        binding.patientEmail.text = patient.email

        // Store phone for call button
        patientPhone = patient.phoneNumber

        if (patient.phoneNumber.isNotEmpty()) {
            binding.patientPhone.visibility = View.VISIBLE
            binding.patientPhone.text = patient.phoneNumber
        } else {
            binding.patientPhone.visibility = View.GONE
        }

        if (patient.profileImageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(patient.profileImageUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .circleCrop()
                .into(binding.patientImage)
        }

        // Member since
        patient.createdAt?.let {
            val dateFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
            binding.memberSinceText.text = "Patient since ${dateFormat.format(it)}"
        }
    }

    private fun showMedicinesTab() {
        binding.medicinesContainer.visibility = View.VISIBLE
        binding.healthLogsContainer.visibility = View.GONE
        binding.notesContainer.visibility = View.GONE
        binding.trendsContainer.visibility = View.GONE
        binding.addNoteFab.visibility = View.VISIBLE
    }

    private fun showHealthLogsTab() {
        binding.medicinesContainer.visibility = View.GONE
        binding.healthLogsContainer.visibility = View.VISIBLE
        binding.notesContainer.visibility = View.GONE
        binding.trendsContainer.visibility = View.GONE
        binding.addNoteFab.visibility = View.GONE
    }

    private fun showNotesTab() {
        binding.medicinesContainer.visibility = View.GONE
        binding.healthLogsContainer.visibility = View.GONE
        binding.notesContainer.visibility = View.VISIBLE
        binding.trendsContainer.visibility = View.GONE
        binding.addNoteFab.visibility = View.VISIBLE
    }

    private fun showTrendsTab() {
        binding.medicinesContainer.visibility = View.GONE
        binding.healthLogsContainer.visibility = View.GONE
        binding.notesContainer.visibility = View.GONE
        binding.trendsContainer.visibility = View.VISIBLE
        binding.addNoteFab.visibility = View.GONE
    }

    private fun updateMedicinesList(medicines: List<Medicine>) {
        if (medicines.isEmpty()) {
            binding.emptyMedicinesText.visibility = View.VISIBLE
            binding.medicinesRecyclerView.visibility = View.GONE
        } else {
            binding.emptyMedicinesText.visibility = View.GONE
            binding.medicinesRecyclerView.visibility = View.VISIBLE
            medicineAdapter.submitList(medicines)
        }
        binding.medicinesCountText.text = "${medicines.size} medicines"
    }

    private fun updateHealthLogsList(logs: List<HealthLog>) {
        currentHealthLogs = logs
        if (logs.isEmpty()) {
            binding.emptyHealthLogsText.visibility = View.VISIBLE
            binding.healthLogsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyHealthLogsText.visibility = View.GONE
            binding.healthLogsRecyclerView.visibility = View.VISIBLE
            healthLogAdapter.submitList(logs)
        }
        binding.healthLogsCountText.text = "${logs.size} logs"

        // Update trends tab
        updateTrendsCharts(logs)
    }

    private fun updateNotesList(notes: List<DoctorNote>) {
        if (notes.isEmpty()) {
            binding.emptyNotesContainer.visibility = View.VISIBLE
            binding.notesRecyclerView.visibility = View.GONE
        } else {
            binding.emptyNotesContainer.visibility = View.GONE
            binding.notesRecyclerView.visibility = View.VISIBLE
            noteAdapter.submitList(notes)
        }
        binding.notesCountText.text = "${notes.size} notes"
    }

    private fun showAddNoteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_note, null)
        val noteInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.noteInput)
        val categorySpinner = dialogView.findViewById<android.widget.Spinner>(R.id.categorySpinner)
        val privateCheckbox = dialogView.findViewById<android.widget.CheckBox>(R.id.privateCheckbox)

        // Setup category spinner
        val categories = NoteCategory.values().map { getCategoryDisplayName(it) }
        val spinnerAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        categorySpinner.adapter = spinnerAdapter

        AlertDialog.Builder(this)
            .setTitle("Add Note")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val noteText = noteInput.text.toString().trim()
                if (noteText.isEmpty()) {
                    Toast.makeText(this, "Please enter a note", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val selectedCategory = NoteCategory.values()[categorySpinner.selectedItemPosition]
                val isPrivate = privateCheckbox.isChecked

                val note = DoctorNote(
                    patientId = patientId,
                    patientName = patientName,
                    note = noteText,
                    category = selectedCategory,
                    isPrivate = isPrivate
                )

                viewModel.addNote(note)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditNoteDialog(note: DoctorNote) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_note, null)
        val noteInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.noteInput)
        val categorySpinner = dialogView.findViewById<android.widget.Spinner>(R.id.categorySpinner)
        val privateCheckbox = dialogView.findViewById<android.widget.CheckBox>(R.id.privateCheckbox)

        // Pre-fill values
        noteInput.setText(note.note)
        privateCheckbox.isChecked = note.isPrivate

        // Setup category spinner
        val categories = NoteCategory.values().map { getCategoryDisplayName(it) }
        val spinnerAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        categorySpinner.adapter = spinnerAdapter
        categorySpinner.setSelection(note.category.ordinal)

        AlertDialog.Builder(this)
            .setTitle("Edit Note")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val noteText = noteInput.text.toString().trim()
                if (noteText.isEmpty()) {
                    Toast.makeText(this, "Please enter a note", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val selectedCategory = NoteCategory.values()[categorySpinner.selectedItemPosition]
                val isPrivate = privateCheckbox.isChecked

                val updatedNote = note.copy(
                    note = noteText,
                    category = selectedCategory,
                    isPrivate = isPrivate
                )

                viewModel.updateNote(updatedNote)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteNoteConfirmation(note: DoctorNote) {
        AlertDialog.Builder(this)
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to delete this note?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteNote(note.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getCategoryDisplayName(category: NoteCategory): String {
        return when (category) {
            NoteCategory.GENERAL -> "General"
            NoteCategory.DIAGNOSIS -> "Diagnosis"
            NoteCategory.PRESCRIPTION -> "Prescription"
            NoteCategory.FOLLOW_UP -> "Follow-up"
            NoteCategory.LAB_RESULTS -> "Lab Results"
            NoteCategory.OBSERVATION -> "Observation"
            NoteCategory.WARNING -> "⚠ Warning"
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Prescription Management Dialogs
    // ═══════════════════════════════════════════════════════════

    /**
     * Actions dialog shown when doctor taps a medicine item.
     * Options: Modify Dosage, Stop Medicine, View History.
     */
    private fun showMedicineActionsDialog(medicine: Medicine) {
        // Find linked prescription for this medicine
        val linkedRx = currentPrescriptions.find { it.medicineId == medicine.id }

        val options = mutableListOf<String>()
        options.add("Modify Dosage")
        options.add("Stop Medicine")
        if (linkedRx != null) {
            options.add("View Prescription History (v${linkedRx.version})")
        }

        AlertDialog.Builder(this)
            .setTitle(medicine.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showModifyPrescriptionDialog(medicine, linkedRx)
                    1 -> showStopPrescriptionDialog(medicine, linkedRx)
                    2 -> linkedRx?.let { showPrescriptionHistoryDialog(it) }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Dialog for doctor to add a new prescription from the Medicines tab.
     * Reuses the same prescription fields as ClinicalDecisionActivity.
     */
    private fun showAddPrescriptionDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_prescription, null)

        val medicationNameInput = dialogView.findViewById<TextInputEditText>(R.id.medicationNameInput)
        val dosageInput = dialogView.findViewById<TextInputEditText>(R.id.dosageInput)
        val unitSpinner = dialogView.findViewById<Spinner>(R.id.unitSpinner)
        val frequencySpinner = dialogView.findViewById<Spinner>(R.id.frequencySpinner)
        val routeSpinner = dialogView.findViewById<Spinner>(R.id.routeSpinner)
        val durationInput = dialogView.findViewById<TextInputEditText>(R.id.durationInput)
        val instructionsInput = dialogView.findViewById<TextInputEditText>(R.id.instructionsInput)

        unitSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, MedicationUnits.UNITS)
        frequencySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, MedicationFrequencies.FREQUENCIES)
        routeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, MedicationRoutes.ROUTES)

        AlertDialog.Builder(this)
            .setTitle("Prescribe Medication")
            .setView(dialogView)
            .setPositiveButton("Prescribe") { _, _ ->
                val medName = medicationNameInput.text.toString().trim()
                if (medName.isEmpty()) {
                    Toast.makeText(this, "Please enter medication name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val record = PrescriptionRecord(
                    patientId = patientId,
                    medicationName = medName,
                    dosage = dosageInput.text.toString().trim(),
                    unit = unitSpinner.selectedItem.toString(),
                    frequency = frequencySpinner.selectedItem.toString(),
                    route = routeSpinner.selectedItem.toString(),
                    duration = durationInput.text.toString().trim(),
                    instructions = instructionsInput.text.toString().trim(),
                    startDate = Date()
                )

                prescriptionViewModel.addPrescription(record)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Dialog for doctor to modify an existing prescription's dosage / frequency.
     */
    private fun showModifyPrescriptionDialog(medicine: Medicine, linkedRx: PrescriptionRecord?) {
        if (linkedRx == null) {
            // No linked prescription — create one ad-hoc from the medicine
            Toast.makeText(this, "This medicine was not prescribed through the system. Use 'Prescribe Medication' instead.", Toast.LENGTH_LONG).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_modify_prescription, null)

        val dosageInput = dialogView.findViewById<TextInputEditText>(R.id.modifyDosageInput)
        val unitSpinner = dialogView.findViewById<Spinner>(R.id.modifyUnitSpinner)
        val frequencySpinner = dialogView.findViewById<Spinner>(R.id.modifyFrequencySpinner)
        val instructionsInput = dialogView.findViewById<TextInputEditText>(R.id.modifyInstructionsInput)
        val changeNoteInput = dialogView.findViewById<TextInputEditText>(R.id.changeNoteInput)

        // Pre-fill
        dosageInput.setText(linkedRx.dosage)
        unitSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, MedicationUnits.UNITS)
        unitSpinner.setSelection(MedicationUnits.UNITS.indexOf(linkedRx.unit).coerceAtLeast(0))
        frequencySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, MedicationFrequencies.FREQUENCIES)
        frequencySpinner.setSelection(MedicationFrequencies.FREQUENCIES.indexOf(linkedRx.frequency).coerceAtLeast(0))
        instructionsInput.setText(linkedRx.instructions)

        AlertDialog.Builder(this)
            .setTitle("Modify: ${linkedRx.medicationName}")
            .setView(dialogView)
            .setPositiveButton("Save Changes") { _, _ ->
                prescriptionViewModel.modifyPrescription(
                    prescriptionId = linkedRx.id,
                    newDosage = dosageInput.text.toString().trim(),
                    newUnit = unitSpinner.selectedItem.toString(),
                    newFrequency = frequencySpinner.selectedItem.toString(),
                    newInstructions = instructionsInput.text.toString().trim(),
                    changeNote = changeNoteInput.text.toString().trim()
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Confirmation dialog for stopping a prescription.
     */
    private fun showStopPrescriptionDialog(medicine: Medicine, linkedRx: PrescriptionRecord?) {
        if (linkedRx == null) {
            Toast.makeText(this, "This medicine was not prescribed through the system", Toast.LENGTH_LONG).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_stop_prescription, null)
        val reasonInput = dialogView.findViewById<TextInputEditText>(R.id.stopReasonInput)

        AlertDialog.Builder(this)
            .setTitle("Stop: ${linkedRx.medicationName}")
            .setMessage("This will deactivate the medicine on the patient's device. This action cannot be undone.")
            .setView(dialogView)
            .setPositiveButton("Stop Prescription") { _, _ ->
                prescriptionViewModel.stopPrescription(
                    prescriptionId = linkedRx.id,
                    reason = reasonInput.text.toString().trim()
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Shows the version history of a prescription.
     */
    private fun showPrescriptionHistoryDialog(rx: PrescriptionRecord) {
        val sb = StringBuilder()
        sb.appendLine("📋 ${rx.medicationName}")
        sb.appendLine("Status: ${rx.status.name}  •  Version: ${rx.version}")
        sb.appendLine()

        if (rx.versionHistory.isEmpty()) {
            sb.appendLine("No modifications recorded.")
        } else {
            rx.versionHistory.sortedByDescending { it.version }.forEach { entry ->
                val dateStr = entry.changedAt?.let {
                    SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(it)
                } ?: "—"
                sb.appendLine("─── v${entry.version} → v${entry.version + 1}  ($dateStr) ───")
                sb.appendLine("  Changed: ${entry.changedFields.joinToString(", ")}")
                for (e in entry.previousValues.entries) {
                    sb.appendLine("    ${e.key}: ${e.value} → (new)")
                }
                if (entry.changeNote.isNotEmpty()) sb.appendLine("  Note: ${entry.changeNote}")
                sb.appendLine()
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Prescription History")
            .setMessage(sb.toString())
            .setPositiveButton("Close", null)
            .show()
    }

    private fun updateTrendsCharts(logs: List<HealthLog>) {
        val insights = HealthAnalytics.calculateAllInsights(logs)

        // Update summary stats
        insights.heartRateStats?.let {
            binding.trendHeartRate.text = "${it.average.toInt()} bpm"
        } ?: run {
            binding.trendHeartRate.text = getString(R.string.no_data_available)
        }

        if (insights.systolicBPStats != null && insights.diastolicBPStats != null) {
            binding.trendBloodPressure.text = "${insights.systolicBPStats.average.toInt()}/${insights.diastolicBPStats.average.toInt()}"
        } else {
            binding.trendBloodPressure.text = getString(R.string.no_data_available)
        }

        // Setup charts
        setupPatientHeartRateChart(logs)
        setupPatientBloodPressureChart(logs)
        setupPatientGlucoseChart(logs)
    }

    private fun setupPatientHeartRateChart(logs: List<HealthLog>) {
        val entries = logs
            .filter { it.heartRate != null }
            .sortedBy { it.date }
            .mapIndexed { index, log ->
                Entry(index.toFloat(), log.heartRate!!.toFloat())
            }

        if (entries.isEmpty()) {
            binding.patientHeartRateChart.clear()
            binding.patientHeartRateChart.setNoDataText(getString(R.string.no_data_available))
            return
        }

        val dataSet = LineDataSet(entries, "Heart Rate").apply {
            color = ContextCompat.getColor(this@PatientDetailActivity, R.color.medicine_red)
            setCircleColor(ContextCompat.getColor(this@PatientDetailActivity, R.color.medicine_red))
            lineWidth = 2f
            circleRadius = 4f
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(this@PatientDetailActivity, R.color.medicine_red)
            fillAlpha = 30
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        setupChart(binding.patientHeartRateChart, LineData(dataSet), logs)
    }

    private fun setupPatientBloodPressureChart(logs: List<HealthLog>) {
        val systolicEntries = logs
            .filter { it.bloodPressureSystolic != null }
            .sortedBy { it.date }
            .mapIndexed { index, log ->
                Entry(index.toFloat(), log.bloodPressureSystolic!!.toFloat())
            }

        val diastolicEntries = logs
            .filter { it.bloodPressureDiastolic != null }
            .sortedBy { it.date }
            .mapIndexed { index, log ->
                Entry(index.toFloat(), log.bloodPressureDiastolic!!.toFloat())
            }

        if (systolicEntries.isEmpty() && diastolicEntries.isEmpty()) {
            binding.patientBloodPressureChart.clear()
            binding.patientBloodPressureChart.setNoDataText(getString(R.string.no_data_available))
            return
        }

        val systolicDataSet = LineDataSet(systolicEntries, "Systolic").apply {
            color = ContextCompat.getColor(this@PatientDetailActivity, R.color.medicine_blue)
            setCircleColor(ContextCompat.getColor(this@PatientDetailActivity, R.color.medicine_blue))
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val diastolicDataSet = LineDataSet(diastolicEntries, "Diastolic").apply {
            color = ContextCompat.getColor(this@PatientDetailActivity, R.color.medicine_teal)
            setCircleColor(ContextCompat.getColor(this@PatientDetailActivity, R.color.medicine_teal))
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        setupChart(binding.patientBloodPressureChart, LineData(systolicDataSet, diastolicDataSet), logs)
    }

    private fun setupPatientGlucoseChart(logs: List<HealthLog>) {
        val entries = logs
            .filter { it.glucoseLevel != null }
            .sortedBy { it.date }
            .mapIndexed { index, log ->
                Entry(index.toFloat(), log.glucoseLevel!!.toFloat())
            }

        if (entries.isEmpty()) {
            binding.patientGlucoseChart.clear()
            binding.patientGlucoseChart.setNoDataText(getString(R.string.no_data_available))
            return
        }

        val dataSet = LineDataSet(entries, "Glucose").apply {
            color = ContextCompat.getColor(this@PatientDetailActivity, R.color.medicine_purple)
            setCircleColor(ContextCompat.getColor(this@PatientDetailActivity, R.color.medicine_purple))
            lineWidth = 2f
            circleRadius = 4f
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(this@PatientDetailActivity, R.color.medicine_purple)
            fillAlpha = 30
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        setupChart(binding.patientGlucoseChart, LineData(dataSet), logs)
    }

    private fun setupChart(chart: LineChart, data: LineData, logs: List<HealthLog>) {
        chart.apply {
            this.data = data
            description.isEnabled = false
            legend.isEnabled = true
            legend.textColor = ContextCompat.getColor(this@PatientDetailActivity, R.color.text_secondary)

            setTouchEnabled(true)
            setDragEnabled(true)
            setScaleEnabled(false)
            setPinchZoom(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = ContextCompat.getColor(this@PatientDetailActivity, R.color.text_tertiary)
                granularity = 1f

                val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
                val sortedLogs = logs.sortedBy { it.date }
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index >= 0 && index < sortedLogs.size) {
                            dateFormat.format(sortedLogs[index].date)
                        } else ""
                    }
                }
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(this@PatientDetailActivity, R.color.divider)
                textColor = ContextCompat.getColor(this@PatientDetailActivity, R.color.text_tertiary)
            }

            axisRight.isEnabled = false

            animateX(500)
            invalidate()
        }
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

