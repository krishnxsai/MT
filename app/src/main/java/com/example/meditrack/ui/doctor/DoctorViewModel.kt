package com.example.meditrack.ui.doctor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meditrack.data.model.DoctorNote
import com.example.meditrack.data.model.HealthLog
import com.example.meditrack.data.model.Medicine
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.model.User
import com.example.meditrack.data.repository.DoctorRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DoctorViewModel : ViewModel() {

    private val repository = DoctorRepository()

    // Patients
    private val _patients = MutableLiveData<Resource<List<User>>>()
    val patients: LiveData<Resource<List<User>>> = _patients

    // Selected patient details
    private val _selectedPatient = MutableLiveData<Resource<User>>()
    val selectedPatient: LiveData<Resource<User>> = _selectedPatient

    // Patient medicines
    private val _patientMedicines = MutableLiveData<Resource<List<Medicine>>>()
    val patientMedicines: LiveData<Resource<List<Medicine>>> = _patientMedicines

    // Patient health logs
    private val _patientHealthLogs = MutableLiveData<Resource<List<HealthLog>>>()
    val patientHealthLogs: LiveData<Resource<List<HealthLog>>> = _patientHealthLogs

    // Doctor notes
    private val _patientNotes = MutableLiveData<Resource<List<DoctorNote>>>()
    val patientNotes: LiveData<Resource<List<DoctorNote>>> = _patientNotes

    // Note operations
    private val _saveNoteResult = MutableLiveData<Resource<DoctorNote>?>()
    val saveNoteResult: LiveData<Resource<DoctorNote>?> = _saveNoteResult

    private val _deleteNoteResult = MutableLiveData<Resource<Unit>?>()
    val deleteNoteResult: LiveData<Resource<Unit>?> = _deleteNoteResult

    // Doctor check
    private val _isDoctor = MutableLiveData<Boolean>()
    val isDoctor: LiveData<Boolean> = _isDoctor

    init {
        checkIfDoctor()
    }

    private fun checkIfDoctor() {
        viewModelScope.launch {
            _isDoctor.postValue(repository.isCurrentUserDoctor())
        }
    }

    // ==================== Patient Management ====================

    fun loadPatients() {
        viewModelScope.launch {
            repository.getAssignedPatientsFlow().collectLatest { result ->
                _patients.postValue(result)
            }
        }
    }

    fun loadPatientsOnce() {
        viewModelScope.launch {
            _patients.postValue(Resource.Loading)
            val result = repository.getAssignedPatients()
            _patients.postValue(result)
        }
    }

    fun loadPatientDetails(patientId: String) {
        viewModelScope.launch {
            _selectedPatient.postValue(Resource.Loading)
            val result = repository.getPatientDetails(patientId)
            _selectedPatient.postValue(result)
        }
    }

    // ==================== Patient Medicines ====================

    fun loadPatientMedicines(patientId: String) {
        viewModelScope.launch {
            repository.getPatientMedicinesFlow(patientId).collectLatest { result ->
                _patientMedicines.postValue(result)
            }
        }
    }

    fun loadPatientMedicinesOnce(patientId: String) {
        viewModelScope.launch {
            _patientMedicines.postValue(Resource.Loading)
            val result = repository.getPatientMedicines(patientId)
            _patientMedicines.postValue(result)
        }
    }

    // ==================== Patient Health Logs ====================

    fun loadPatientHealthLogs(patientId: String) {
        viewModelScope.launch {
            repository.getPatientHealthLogsFlow(patientId).collectLatest { result ->
                _patientHealthLogs.postValue(result)
            }
        }
    }

    fun loadPatientHealthLogsOnce(patientId: String) {
        viewModelScope.launch {
            _patientHealthLogs.postValue(Resource.Loading)
            val result = repository.getPatientHealthLogs(patientId)
            _patientHealthLogs.postValue(result)
        }
    }

    // ==================== Doctor Notes ====================

    fun loadPatientNotes(patientId: String) {
        viewModelScope.launch {
            repository.getPatientNotesFlow(patientId).collectLatest { result ->
                _patientNotes.postValue(result)
            }
        }
    }

    fun loadPatientNotesOnce(patientId: String) {
        viewModelScope.launch {
            _patientNotes.postValue(Resource.Loading)
            val result = repository.getPatientNotes(patientId)
            _patientNotes.postValue(result)
        }
    }

    fun addNote(note: DoctorNote) {
        viewModelScope.launch {
            _saveNoteResult.postValue(Resource.Loading)
            val result = repository.addDoctorNote(note)
            _saveNoteResult.postValue(result)
        }
    }

    fun updateNote(note: DoctorNote) {
        viewModelScope.launch {
            _saveNoteResult.postValue(Resource.Loading)
            val result = repository.updateDoctorNote(note)
            _saveNoteResult.postValue(result)
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            _deleteNoteResult.postValue(Resource.Loading)
            val result = repository.deleteDoctorNote(noteId)
            _deleteNoteResult.postValue(result)
        }
    }

    fun clearSaveNoteResult() {
        _saveNoteResult.value = null
    }

    fun clearDeleteNoteResult() {
        _deleteNoteResult.value = null
    }
}

