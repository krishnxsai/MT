package com.example.meditrack.data.repository

import android.util.Log
import com.example.meditrack.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository for doctor availability & appointment booking.
 *
 * Workflow:
 *  1. Doctor defines recurring availability slots (dayOfWeek + time range).
 *  2. Patient picks a date → system maps to dayOfWeek → fetches availability →
 *     computes open time-slots by subtracting already-booked appointments.
 *  3. Patient books → status = PENDING.
 *  4. Doctor confirms / rejects.
 *  5. Either party can cancel.
 */
class AppointmentRepository {

    companion object {
        private const val TAG = "AppointmentRepository"
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val availabilityCol by lazy { firestore.collection("doctorAvailability") }
    private val appointmentsCol by lazy { firestore.collection("appointments") }
    private val usersCol by lazy { firestore.collection("users") }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // ─────────────── Doctor Availability ───────────────

    /**
     * Real-time flow of the current doctor's availability slots.
     */
    fun getDoctorAvailabilityFlow(doctorId: String): Flow<Resource<List<DoctorAvailability>>> = callbackFlow {
        trySend(Resource.Loading)

        val listener = availabilityCol
            .whereEqualTo("doctorId", doctorId)
            .orderBy("dayOfWeek", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to load availability"))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { DoctorAvailability.fromMap(doc.id, it) }
                } ?: emptyList()
                trySend(Resource.Success(list))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Save or update a doctor availability slot.
     */
    suspend fun saveAvailability(slot: DoctorAvailability): Resource<DoctorAvailability> =
        withContext(Dispatchers.IO) {
            try {
                val doctorId = currentUserId ?: return@withContext Resource.Error("Not logged in")

                // Get doctor name
                val doctorDoc = usersCol.document(doctorId).get().await()
                val doctorName = doctorDoc.getString("displayName") ?: "Doctor"

                val data = slot.copy(doctorId = doctorId, doctorName = doctorName)
                if (slot.id.isEmpty()) {
                    val docRef = availabilityCol.add(
                        data.toMap().plus("createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
                    ).await()
                    Resource.Success(data.copy(id = docRef.id))
                } else {
                    availabilityCol.document(slot.id).update(data.toMap()).await()
                    Resource.Success(data)
                }
            } catch (e: Exception) {
                Log.e(TAG, "saveAvailability error: ${e.message}")
                Resource.Error(e.message ?: "Failed to save availability")
            }
        }

    /**
     * Delete an availability slot.
     */
    suspend fun deleteAvailability(slotId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            try {
                availabilityCol.document(slotId).delete().await()
                Resource.Success(Unit)
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Failed to delete availability")
            }
        }

    // ─────────────── Available Slots Computation ───────────────

    /**
     * Compute open slots for a given [doctorId] on [date].
     *
     * Steps:
     *  1. Map [date] to ISO day-of-week.
     *  2. Fetch active availability for that day.
     *  3. Fetch existing appointments for that date.
     *  4. Subtract booked slots from all possible slots.
     */
    suspend fun getAvailableSlots(doctorId: String, date: Date): Resource<List<TimeSlot>> =
        withContext(Dispatchers.IO) {
            try {
                // Ensure user is authenticated before querying
                val user = auth.currentUser
                if (user == null) {
                    return@withContext Resource.Error("Not logged in")
                }

                // Force refresh token to avoid stale token PERMISSION_DENIED errors
                try {
                    user.getIdToken(false).await()
                } catch (e: Exception) {
                    Log.w(TAG, "Token refresh failed, continuing with current token", e)
                }

                val cal = Calendar.getInstance().apply { time = date }
                // Calendar.DAY_OF_WEEK: Sun=1 … Sat=7  →  ISO: Mon=1 … Sun=7
                val calDow = cal.get(Calendar.DAY_OF_WEEK)
                val isoDow = if (calDow == Calendar.SUNDAY) 7 else calDow - 1

                // 1. Fetch availability for this day
                val avail = availabilityCol
                    .whereEqualTo("doctorId", doctorId)
                    .whereEqualTo("dayOfWeek", isoDow)
                    .whereEqualTo("isActive", true)
                    .get().await()
                    .documents
                    .mapNotNull { it.data?.let { d -> DoctorAvailability.fromMap(it.id, d) } }

                if (avail.isEmpty()) {
                    return@withContext Resource.Success(emptyList())
                }

                // 2. Fetch existing appointments on this date
                val dayStart = Calendar.getInstance().apply {
                    time = date; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.time
                val dayEnd = Calendar.getInstance().apply {
                    time = dayStart; add(Calendar.DAY_OF_MONTH, 1)
                }.time

                // Use separate queries for date range to avoid composite index issues
                val booked = appointmentsCol
                    .whereEqualTo("doctorId", doctorId)
                    .whereGreaterThanOrEqualTo("date", dayStart)
                    .whereLessThan("date", dayEnd)
                    .get().await()
                    .documents
                    .mapNotNull { it.data?.let { d -> Appointment.fromMap(it.id, d) } }
                    .filter { it.status != AppointmentStatus.CANCELLED && it.status != AppointmentStatus.REJECTED }

                val bookedTimes = booked.map { it.startTime }.toSet()

                // 3. Generate all possible slots and subtract booked ones
                val allSlots = mutableListOf<TimeSlot>()
                for (a in avail) {
                    val slots = generateTimeSlots(a.startTime, a.endTime, a.slotDurationMinutes)
                    for (s in slots) {
                        allSlots.add(
                            TimeSlot(
                                startTime = s.first,
                                endTime = s.second,
                                isAvailable = s.first !in bookedTimes
                            )
                        )
                    }
                }

                Resource.Success(allSlots.sortedBy { it.startTime })
            } catch (e: Exception) {
                Log.e(TAG, "getAvailableSlots error", e)
                Resource.Error(e.message ?: "Failed to compute available slots")
            }
        }

    /**
     * Generate time-slot pairs from [start] to [end] with given [durationMinutes].
     */
    private fun generateTimeSlots(start: String, end: String, durationMinutes: Int): List<Pair<String, String>> {
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        val slots = mutableListOf<Pair<String, String>>()
        val cal = Calendar.getInstance().apply { time = fmt.parse(start)!! }
        val endTime = fmt.parse(end)!!

        while (true) {
            val slotStart = fmt.format(cal.time)
            cal.add(Calendar.MINUTE, durationMinutes)
            if (cal.time.after(endTime)) break
            val slotEnd = fmt.format(cal.time)
            slots.add(slotStart to slotEnd)
        }
        return slots
    }

    // ─────────────── Appointment Booking ───────────────

    /**
     * Book an appointment (patient action).
     */
    suspend fun bookAppointment(
        doctorId: String,
        date: Date,
        startTime: String,
        endTime: String,
        type: AppointmentType = AppointmentType.CONSULTATION,
        notes: String = ""
    ): Resource<Appointment> = withContext(Dispatchers.IO) {
        try {
            val patientId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            // Fetch names
            val patientDoc = usersCol.document(patientId).get().await()
            val doctorDoc = usersCol.document(doctorId).get().await()
            val patientName = patientDoc.getString("displayName") ?: "Patient"
            val doctorName = doctorDoc.getString("displayName") ?: "Doctor"

            // Check for double-booking
            val dayStart = Calendar.getInstance().apply {
                time = date; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.time
            val dayEnd = Calendar.getInstance().apply {
                time = dayStart; add(Calendar.DAY_OF_MONTH, 1)
            }.time

            val existingSlot = appointmentsCol
                .whereEqualTo("doctorId", doctorId)
                .whereEqualTo("startTime", startTime)
                .whereGreaterThanOrEqualTo("date", dayStart)
                .whereLessThan("date", dayEnd)
                .get().await()
                .documents
                .mapNotNull { it.data?.let { d -> Appointment.fromMap(it.id, d) } }
                .any { it.status != AppointmentStatus.CANCELLED && it.status != AppointmentStatus.REJECTED }

            if (existingSlot) {
                return@withContext Resource.Error("This time slot is already booked")
            }

            val appointment = Appointment(
                doctorId = doctorId,
                patientId = patientId,
                doctorName = doctorName,
                patientName = patientName,
                date = dayStart, // normalize to start of day
                startTime = startTime,
                endTime = endTime,
                status = AppointmentStatus.PENDING,
                type = type,
                notes = notes
            )

            val docRef = appointmentsCol.add(
                appointment.toMap().plus("createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
            ).await()

            Resource.Success(appointment.copy(id = docRef.id))
        } catch (e: Exception) {
            Log.e(TAG, "bookAppointment error: ${e.message}")
            Resource.Error(e.message ?: "Failed to book appointment")
        }
    }

    /**
     * Book an appointment (doctor action on behalf of a patient).
     */
    suspend fun bookAppointmentAsDoctor(
        patientId: String,
        date: Date,
        startTime: String,
        endTime: String,
        type: AppointmentType = AppointmentType.CONSULTATION,
        notes: String = ""
    ): Resource<Appointment> = withContext(Dispatchers.IO) {
        try {
            val doctorId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            val doctorDoc = usersCol.document(doctorId).get().await()
            val patientDoc = usersCol.document(patientId).get().await()
            val doctorName = doctorDoc.getString("displayName") ?: "Doctor"
            val patientName = patientDoc.getString("displayName") ?: "Patient"

            val dayStart = Calendar.getInstance().apply {
                time = date; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.time
            val dayEnd = Calendar.getInstance().apply {
                time = dayStart; add(Calendar.DAY_OF_MONTH, 1)
            }.time

            val existingSlot = appointmentsCol
                .whereEqualTo("doctorId", doctorId)
                .whereEqualTo("startTime", startTime)
                .whereGreaterThanOrEqualTo("date", dayStart)
                .whereLessThan("date", dayEnd)
                .get().await()
                .documents
                .mapNotNull { it.data?.let { d -> Appointment.fromMap(it.id, d) } }
                .any { it.status != AppointmentStatus.CANCELLED && it.status != AppointmentStatus.REJECTED }

            if (existingSlot) {
                return@withContext Resource.Error("This time slot is already booked")
            }

            val appointment = Appointment(
                doctorId = doctorId,
                patientId = patientId,
                doctorName = doctorName,
                patientName = patientName,
                date = dayStart,
                startTime = startTime,
                endTime = endTime,
                status = AppointmentStatus.CONFIRMED,
                type = type,
                notes = notes
            )

            val docRef = appointmentsCol.add(
                appointment.toMap().plus("createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
            ).await()

            Resource.Success(appointment.copy(id = docRef.id))
        } catch (e: Exception) {
            Log.e(TAG, "bookAppointmentAsDoctor error: ${e.message}")
            Resource.Error(e.message ?: "Failed to book appointment")
        }
    }

    /**
     * Reschedule an appointment (either party can do this).
     * Cancels the old appointment and creates a new one.
     */
    suspend fun rescheduleAppointment(
        oldAppointmentId: String,
        doctorId: String,
        patientId: String,
        newDate: Date,
        newStartTime: String,
        newEndTime: String,
        type: AppointmentType = AppointmentType.CONSULTATION,
        notes: String = ""
    ): Resource<Appointment> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            // Fetch the old appointment to get correct doctorId/patientId
            val oldDoc = appointmentsCol.document(oldAppointmentId).get().await()
            val oldData = oldDoc.data
            if (oldData == null) {
                return@withContext Resource.Error("Original appointment not found")
            }
            val oldAppt = Appointment.fromMap(oldDoc.id, oldData)

            // Verify the current user is either the doctor or patient on this appointment
            if (userId != oldAppt.doctorId && userId != oldAppt.patientId) {
                return@withContext Resource.Error("Access denied: Not your appointment")
            }

            val cancelResult = updateAppointmentStatus(
                oldAppointmentId,
                AppointmentStatus.CANCELLED,
                cancellationReason = "Rescheduled"
            )
            if (cancelResult is Resource.Error) {
                return@withContext Resource.Error(cancelResult.message)
            }

            val userDoc = usersCol.document(userId).get().await()
            val role = userDoc.getString("role") ?: "PATIENT"

            // Use IDs from the old appointment (more reliable)
            val actualDoctorId = oldAppt.doctorId
            val actualPatientId = oldAppt.patientId

            val doctorDoc = usersCol.document(actualDoctorId).get().await()
            val patientDoc = usersCol.document(actualPatientId).get().await()
            val doctorName = doctorDoc.getString("displayName") ?: "Doctor"
            val patientName = patientDoc.getString("displayName") ?: "Patient"

            val dayStart = Calendar.getInstance().apply {
                time = newDate; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.time

            val appointment = Appointment(
                doctorId = actualDoctorId,
                patientId = actualPatientId,
                doctorName = doctorName,
                patientName = patientName,
                date = dayStart,
                startTime = newStartTime,
                endTime = newEndTime,
                status = if (role == "DOCTOR") AppointmentStatus.CONFIRMED else AppointmentStatus.PENDING,
                type = type,
                notes = notes
            )

            val docRef = appointmentsCol.add(
                appointment.toMap().plus("createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
            ).await()

            Resource.Success(appointment.copy(id = docRef.id))
        } catch (e: Exception) {
            Log.e(TAG, "rescheduleAppointment error: ${e.message}")
            Resource.Error(e.message ?: "Failed to reschedule appointment")
        }
    }

    // ─────────────── Appointment Management ───────────────

    /**
     * Real-time flow of appointments for the current user (works for both roles).
     * Both doctorId and patientId are always stored on every appointment,
     * so the query correctly returns appointments regardless of who created them.
     */
    fun getAppointmentsFlow(upcoming: Boolean = true): Flow<Resource<List<Appointment>>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        // Determine role with error handling
        val field = try {
            val userDoc = usersCol.document(userId).get().await()
            val role = userDoc.getString("role") ?: "PATIENT"
            if (role == "DOCTOR") "doctorId" else "patientId"
        } catch (e: Exception) {
            Log.e(TAG, "getAppointmentsFlow: failed to determine role, defaulting to patientId", e)
            "patientId" // safe default: patient sees appointments where they're the patient
        }

        // Use start of today (midnight) so that today's appointments are included in "upcoming"
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val direction = if (upcoming) Query.Direction.ASCENDING else Query.Direction.DESCENDING

        var query = appointmentsCol
            .whereEqualTo(field, userId)
            .orderBy("date", direction)

        if (upcoming) {
            query = query.whereGreaterThanOrEqualTo("date", todayStart)
        } else {
            query = query.whereLessThan("date", todayStart)
        }

        val listener = query.limit(50).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "getAppointmentsFlow error", error)
                trySend(Resource.Error(error.message ?: "Failed to load appointments"))
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.data?.let { Appointment.fromMap(doc.id, it) }
            } ?: emptyList()
            trySend(Resource.Success(list))
        }

        awaitClose { listener.remove() }
    }

    /**
     * Update appointment status (doctor: confirm/reject, patient: cancel).
     */
    suspend fun updateAppointmentStatus(
        appointmentId: String,
        newStatus: AppointmentStatus,
        doctorNotes: String = "",
        cancellationReason: String = ""
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val updates = mutableMapOf<String, Any?>(
                "status" to newStatus.name,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            if (doctorNotes.isNotEmpty()) updates["doctorNotes"] = doctorNotes
            if (cancellationReason.isNotEmpty()) updates["cancellationReason"] = cancellationReason

            appointmentsCol.document(appointmentId).update(updates).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateAppointmentStatus error: ${e.message}")
            Resource.Error(e.message ?: "Failed to update appointment")
        }
    }
}

/**
 * A computed time-slot for appointment booking UI.
 */
data class TimeSlot(
    val startTime: String,
    val endTime: String,
    val isAvailable: Boolean
)

