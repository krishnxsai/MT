package com.example.meditrack.ui.doctor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meditrack.data.model.PrescriptionRecord
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.repository.PrescriptionRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel for doctor-side prescription management.
 *
 * Used by [PatientDetailActivity] (Medicines tab) to:
 *  • List prescriptions for a patient
 *  • Add a new prescription (with dedup)
 *  • Modify dosage / frequency / instructions
 *  • Stop a prescription
 *
 * All writes go through [PrescriptionRepository] which handles:
 *  – Atomic batch writes (prescriptions + medicines)
 *  – Version tracking
 *  – Duplicate prevention
 */
class DoctorPrescriptionViewModel : ViewModel() {

    private val repository = PrescriptionRepository()

    // ── Prescription list ───────────────────────────────────────
    private val _prescriptions = MutableLiveData<Resource<List<PrescriptionRecord>>>()
    val prescriptions: LiveData<Resource<List<PrescriptionRecord>>> = _prescriptions

    // ── Create / modify result ──────────────────────────────────
    private val _saveResult = MutableLiveData<Resource<PrescriptionRecord>?>()
    val saveResult: LiveData<Resource<PrescriptionRecord>?> = _saveResult

    // ── Stop result ─────────────────────────────────────────────
    private val _stopResult = MutableLiveData<Resource<Unit>?>()
    val stopResult: LiveData<Resource<Unit>?> = _stopResult

    // ── Duplicate warning for UI ────────────────────────────────
    private val _duplicateWarning = MutableLiveData<String?>()
    val duplicateWarning: LiveData<String?> = _duplicateWarning

    // ═══════════════════════════════════════════════════════════
    // Load
    // ═══════════════════════════════════════════════════════════

    fun loadPatientPrescriptions(patientId: String) {
        viewModelScope.launch {
            repository.getPatientPrescriptionsFlow(patientId).collectLatest { result ->
                _prescriptions.postValue(result)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Create
    // ═══════════════════════════════════════════════════════════

    fun addPrescription(record: PrescriptionRecord) {
        viewModelScope.launch {
            _saveResult.postValue(Resource.Loading)
            val result = repository.createPrescription(record)

            if (result is Resource.Error && result.message?.contains("Duplicate") == true) {
                _duplicateWarning.postValue(result.message)
                _saveResult.postValue(null)
            } else {
                _saveResult.postValue(result)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Modify
    // ═══════════════════════════════════════════════════════════

    fun modifyPrescription(
        prescriptionId: String,
        newDosage: String? = null,
        newUnit: String? = null,
        newFrequency: String? = null,
        newInstructions: String? = null,
        changeNote: String = ""
    ) {
        viewModelScope.launch {
            _saveResult.postValue(Resource.Loading)
            val result = repository.modifyPrescription(
                prescriptionId = prescriptionId,
                newDosage = newDosage,
                newUnit = newUnit,
                newFrequency = newFrequency,
                newInstructions = newInstructions,
                changeNote = changeNote
            )
            _saveResult.postValue(result)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Stop
    // ═══════════════════════════════════════════════════════════

    fun stopPrescription(prescriptionId: String, reason: String = "") {
        viewModelScope.launch {
            _stopResult.postValue(Resource.Loading)
            val result = repository.stopPrescription(prescriptionId, reason)
            _stopResult.postValue(result)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Clear
    // ═══════════════════════════════════════════════════════════

    fun clearSaveResult() { _saveResult.value = null }
    fun clearStopResult() { _stopResult.value = null }
    fun clearDuplicateWarning() { _duplicateWarning.value = null }
}

