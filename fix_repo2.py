import os
import sys

repo_path = os.path.join(r"C:\Users\wujjw\Music\MediTrack-main\MediTrack-main\app\src\main\java\com\example\meditrack\data\repository", "AppointmentRepository.kt")
out_path = os.path.join(r"C:\Users\wujjw\Music\MediTrack-main\MediTrack-main", "fix_result.txt")

try:
    with open(repo_path, 'r', encoding='utf-8') as f:
        content = f.read()

    result_lines = []
    result_lines.append(f"File size: {len(content)}")
    result_lines.append(f"Contains bookAppointmentAsDoctor: {'bookAppointmentAsDoctor' in content}")
    result_lines.append(f"Contains Appointment Management: {'Appointment Management' in content}")

    if 'bookAppointmentAsDoctor' not in content and 'Appointment Management' in content:
        new_code = '''
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
            Log.e(TAG, "bookAppointmentAsDoctor error: $" + "{e.message}")
            Resource.Error(e.message ?: "Failed to book appointment")
        }
    }

    /**
     * Reschedule an appointment (either party can do this).
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
            val cancelResult = updateAppointmentStatus(
                oldAppointmentId,
                AppointmentStatus.CANCELLED,
                cancellationReason = "Rescheduled"
            )
            if (cancelResult is Resource.Error) {
                return@withContext Resource.Error(cancelResult.message)
            }

            val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")
            val userDoc = usersCol.document(userId).get().await()
            val role = userDoc.getString("role") ?: "PATIENT"

            val doctorDoc = usersCol.document(doctorId).get().await()
            val patientDoc = usersCol.document(patientId).get().await()
            val doctorName = doctorDoc.getString("displayName") ?: "Doctor"
            val patientName = patientDoc.getString("displayName") ?: "Patient"

            val dayStart = Calendar.getInstance().apply {
                time = newDate; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.time

            val appointment = Appointment(
                doctorId = doctorId,
                patientId = patientId,
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
            Log.e(TAG, "rescheduleAppointment error: $" + "{e.message}")
            Resource.Error(e.message ?: "Failed to reschedule appointment")
        }
    }

'''
        # Find and replace
        lines = content.split('\n')
        insert_idx = None
        for i, line in enumerate(lines):
            if 'Appointment Management' in line:
                insert_idx = i
                break

        if insert_idx is not None:
            new_content = '\n'.join(lines[:insert_idx]) + new_code + '\n'.join(lines[insert_idx:])
            with open(repo_path, 'w', encoding='utf-8') as f:
                f.write(new_content)
            result_lines.append(f"SUCCESS: Inserted at line {insert_idx}")
        else:
            result_lines.append("ERROR: Could not find insertion point")
    elif 'bookAppointmentAsDoctor' in content:
        result_lines.append("Already has the new methods")
    else:
        result_lines.append("ERROR: Appointment Management marker not found")

    with open(out_path, 'w') as f:
        f.write('\n'.join(result_lines))

except Exception as e:
    with open(out_path, 'w') as f:
        f.write(f"ERROR: {str(e)}")

