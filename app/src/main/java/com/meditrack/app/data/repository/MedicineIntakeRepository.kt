package com.meditrack.app.data.repository

import com.meditrack.app.data.analytics.HealthAnalytics
import com.meditrack.app.data.analytics.ReminderOptimizer
import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

/**
 * Repository for medicine intake tracking.
 * Queries the `medicineIntakes` Firestore collection.
 */
class MedicineIntakeRepository {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val intakesCollection by lazy { firestore.collection("medicineIntakes") }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    /**
     * Get the overall adherence percentage for a date range.
     * @return 0..100 percentage, or -1 if no data
     */
    suspend fun getAdherencePercentage(daysBack: Int = 7): Resource<Float> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, -daysBack)
            val startDate = calendar.time

            val snapshot = intakesCollection
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("takenAt", startDate)
                .orderBy("takenAt", Query.Direction.DESCENDING)
                .get()
                .await()

            if (snapshot.isEmpty) return@withContext Resource.Success(-1f)

            val totalIntakes = snapshot.size()
            val takenIntakes = snapshot.documents.count { doc ->
                doc.getBoolean("taken") ?: (doc.getDate("takenAt") != null)
            }

            if (totalIntakes > 0) {
                Resource.Success((takenIntakes.toFloat() / totalIntakes) * 100f)
            } else Resource.Success(-1f)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get adherence data")
        }
    }

    /**
     * Get intake records formatted for the ReminderOptimizer.
     */
    suspend fun getIntakeRecords(daysBack: Int = 30): Resource<List<ReminderOptimizer.IntakeRecord>> =
        withContext(Dispatchers.IO) {
            try {
                val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")

                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_MONTH, -daysBack)
                val startDate = calendar.time

                val snapshot = intakesCollection
                    .whereEqualTo("userId", userId)
                    .whereGreaterThanOrEqualTo("takenAt", startDate)
                    .orderBy("takenAt", Query.Direction.DESCENDING)
                    .get()
                    .await()

                val records = snapshot.documents.mapNotNull { doc ->
                    try {
                        val medicineId = doc.getString("medicineId") ?: return@mapNotNull null
                        val medicineName = doc.getString("medicineName") ?: ""
                        val scheduledTime = doc.getString("scheduledTime") ?: ""
                        val takenAt = (doc.get("takenAt") as? com.google.firebase.Timestamp)?.toDate()
                        val wasTaken = doc.getBoolean("taken") ?: (takenAt != null)

                        ReminderOptimizer.IntakeRecord(
                            medicineId = medicineId,
                            medicineName = medicineName,
                            scheduledTime = scheduledTime,
                            actualTakenTime = if (wasTaken) takenAt else null,
                            wasOnTime = doc.getBoolean("wasOnTime") ?: false
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                Resource.Success(records)
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Failed to get intake records")
            }
        }

    /**
     * Get intake count for a specific medicine.
     */
    suspend fun getIntakeCountForMedicine(
        medicineId: String,
        daysBack: Int = 7
    ): Resource<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, -daysBack)
            val startDate = calendar.time

            val snapshot = intakesCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("medicineId", medicineId)
                .whereGreaterThanOrEqualTo("takenAt", startDate)
                .orderBy("takenAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val total = snapshot.size()
            val taken = snapshot.documents.count { doc ->
                doc.getBoolean("taken") ?: true
            }

            Resource.Success(Pair(taken, total))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get intake data")
        }
    }

    /**
     * Build adherence stats for today's scheduled reminders using persisted intake actions.
     * A skipped reminder counts as missed, a taken reminder counts as taken,
     * and unanswered reminders remain pending.
     */
    suspend fun getTodayAdherenceStats(medicines: List<Medicine>): Resource<HealthAnalytics.AdherenceStats> =
        withContext(Dispatchers.IO) {
            try {
                val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")

                val startOfDay = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startDate = startOfDay.time

                val now = Calendar.getInstance()
                val currentTimeMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

                val snapshot = intakesCollection
                    .whereEqualTo("userId", userId)
                    .whereGreaterThanOrEqualTo("takenAt", startDate)
                    .orderBy("takenAt", Query.Direction.DESCENDING)
                    .get()
                    .await()

                val intakeBySlot = linkedMapOf<String, Boolean>()
                snapshot.documents.forEach { doc ->
                    val medicineId = doc.getString("medicineId") ?: return@forEach
                    val scheduledTime = doc.getString("scheduledTime") ?: return@forEach
                    val slotKey = "$medicineId|$scheduledTime"
                    if (!intakeBySlot.containsKey(slotKey)) {
                        val wasTaken = doc.getBoolean("taken") ?: (doc.getDate("takenAt") != null)
                        intakeBySlot[slotKey] = wasTaken
                    }
                }

                var totalScheduled = 0
                var taken = 0
                var missed = 0
                var pending = 0

                medicines.filter { it.isActive }.forEach { medicine ->
                    medicine.reminderTimes.forEach { timeStr ->
                        totalScheduled++
                        val parts = timeStr.split(":")
                        if (parts.size != 2) {
                            pending++
                            return@forEach
                        }

                        val hour = parts[0].toIntOrNull()
                        val minute = parts[1].toIntOrNull()
                        if (hour == null || minute == null) {
                            pending++
                            return@forEach
                        }

                        val slotKey = "${medicine.id}|$timeStr"
                        when (intakeBySlot[slotKey]) {
                            true -> taken++
                            false -> missed++
                            null -> {
                                val scheduledMinutes = hour * 60 + minute
                                if (scheduledMinutes > currentTimeMinutes) {
                                    pending++
                                } else {
                                    pending++
                                }
                            }
                        }
                    }
                }

                val adherencePercentage = if ((taken + missed) > 0) {
                    (taken.toFloat() / (taken + missed).toFloat()) * 100f
                } else if (totalScheduled > 0) {
                    (taken.toFloat() / totalScheduled.toFloat()) * 100f
                } else {
                    100f
                }

                Resource.Success(
                    HealthAnalytics.AdherenceStats(
                        totalScheduled = totalScheduled,
                        taken = taken,
                        missed = missed,
                        pending = pending,
                        adherencePercentage = adherencePercentage
                    )
                )
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Failed to get adherence stats")
            }
        }
}

