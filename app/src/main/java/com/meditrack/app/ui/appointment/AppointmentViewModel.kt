package com.meditrack.app.ui.appointment

import androidx.lifecycle.*
import com.meditrack.app.data.model.*
import com.meditrack.app.data.repository.AppointmentRepository
import com.meditrack.app.data.repository.TimeSlot
import kotlinx.coroutines.flow.collectLatest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

/**
 * ViewModel for appointment booking, listing, and doctor availability management.
 */
@HiltViewModel
class AppointmentViewModel @Inject constructor(
    private val repository: AppointmentRepository
) : ViewModel() {

    // ── Appointments list ──
    private val _upcomingAppointments = MutableLiveData<Resource<List<Appointment>>>()
    val upcomingAppointments: LiveData<Resource<List<Appointment>>> = _upcomingAppointments

    private val _pastAppointments = MutableLiveData<Resource<List<Appointment>>>()
    val pastAppointments: LiveData<Resource<List<Appointment>>> = _pastAppointments

    // ── Available slots for booking ──
    private val _availableSlots = MutableLiveData<Resource<List<TimeSlot>>>()
    val availableSlots: LiveData<Resource<List<TimeSlot>>> = _availableSlots

    // ── Doctor availability management ──
    private val _availability = MutableLiveData<Resource<List<DoctorAvailability>>>()
    val availability: LiveData<Resource<List<DoctorAvailability>>> = _availability

    // ── Action results ──
    private val _bookingResult = MutableLiveData<Resource<Appointment>>()
    val bookingResult: LiveData<Resource<Appointment>> = _bookingResult

    private val _actionResult = MutableLiveData<Resource<Unit>>()
    val actionResult: LiveData<Resource<Unit>> = _actionResult

    // ─────────────── Appointment Listing ───────────────

    fun loadUpcomingAppointments() {
        viewModelScope.launch {
            repository.getAppointmentsFlow(upcoming = true).collectLatest {
                _upcomingAppointments.postValue(it)
            }
        }
    }

    fun loadPastAppointments() {
        viewModelScope.launch {
            repository.getAppointmentsFlow(upcoming = false).collectLatest {
                _pastAppointments.postValue(it)
            }
        }
    }

    // ─────────────── Slot Computation ───────────────

    fun loadAvailableSlots(doctorId: String, date: Date) {
        viewModelScope.launch {
            _availableSlots.postValue(Resource.Loading)
            val result = repository.getAvailableSlots(doctorId, date)
            _availableSlots.postValue(result)
        }
    }

    // ─────────────── Booking ───────────────

    fun bookAppointment(
        doctorId: String,
        date: Date,
        startTime: String,
        endTime: String,
        type: AppointmentType = AppointmentType.CONSULTATION,
        notes: String = ""
    ) {
        viewModelScope.launch {
            _bookingResult.postValue(Resource.Loading)
            val result = repository.bookAppointment(doctorId, date, startTime, endTime, type, notes)
            _bookingResult.postValue(result)
        }
    }

    /**
     * Doctor books an appointment for a specific patient.
     */
    fun bookAppointmentAsDoctor(
        patientId: String,
        date: Date,
        startTime: String,
        endTime: String,
        type: AppointmentType = AppointmentType.CONSULTATION,
        notes: String = ""
    ) {
        viewModelScope.launch {
            _bookingResult.postValue(Resource.Loading)
            val result = repository.bookAppointmentAsDoctor(patientId, date, startTime, endTime, type, notes)
            _bookingResult.postValue(result)
        }
    }

    /**
     * Reschedule an existing appointment (cancel old + book new).
     */
    fun rescheduleAppointment(
        oldAppointmentId: String,
        doctorId: String,
        patientId: String,
        newDate: Date,
        newStartTime: String,
        newEndTime: String,
        type: AppointmentType = AppointmentType.CONSULTATION,
        notes: String = ""
    ) {
        viewModelScope.launch {
            _bookingResult.postValue(Resource.Loading)
            val result = repository.rescheduleAppointment(
                oldAppointmentId, doctorId, patientId, newDate, newStartTime, newEndTime, type, notes
            )
            _bookingResult.postValue(result)
        }
    }

    // ─────────────── Status Updates ───────────────

    fun confirmAppointment(appointmentId: String, doctorNotes: String = "") {
        updateStatus(appointmentId, AppointmentStatus.CONFIRMED, doctorNotes = doctorNotes)
    }

    fun rejectAppointment(appointmentId: String, doctorNotes: String = "") {
        updateStatus(appointmentId, AppointmentStatus.REJECTED, doctorNotes = doctorNotes)
    }

    fun cancelAppointment(appointmentId: String, reason: String = "") {
        updateStatus(appointmentId, AppointmentStatus.CANCELLED, cancellationReason = reason)
    }

    fun completeAppointment(appointmentId: String) {
        updateStatus(appointmentId, AppointmentStatus.COMPLETED)
    }

    private fun updateStatus(
        appointmentId: String,
        status: AppointmentStatus,
        doctorNotes: String = "",
        cancellationReason: String = ""
    ) {
        viewModelScope.launch {
            _actionResult.postValue(Resource.Loading)
            val result = repository.updateAppointmentStatus(appointmentId, status, doctorNotes, cancellationReason)
            _actionResult.postValue(result)
        }
    }

    // ─────────────── Doctor Availability ───────────────

    fun loadDoctorAvailability(doctorId: String) {
        viewModelScope.launch {
            repository.getDoctorAvailabilityFlow(doctorId).collectLatest {
                _availability.postValue(it)
            }
        }
    }

    fun saveAvailability(slot: DoctorAvailability) {
        viewModelScope.launch {
            _actionResult.postValue(Resource.Loading)
            val result = repository.saveAvailability(slot)
            _actionResult.postValue(
                if (result is Resource.Success) Resource.Success(Unit)
                else Resource.Error((result as Resource.Error).message)
            )
        }
    }

    fun deleteAvailability(slotId: String) {
        viewModelScope.launch {
            _actionResult.postValue(Resource.Loading)
            val result = repository.deleteAvailability(slotId)
            _actionResult.postValue(result)
        }
    }
}

