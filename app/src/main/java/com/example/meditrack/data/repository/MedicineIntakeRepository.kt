package com.example.meditrack.data.repository

import com.example.meditrack.data.analytics.ReminderOptimizer
import com.example.meditrack.data.model.Resource
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
    suspend fun getAdherencePercentage(daysBack: Int = 7): Float = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext -1f

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, -daysBack)
            val startDate = calendar.time

            val snapshot = intakesCollection
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("takenAt", startDate)
                .orderBy("takenAt", Query.Direction.DESCENDING)
                .get()
                .await()

            if (snapshot.isEmpty) return@withContext -1f

            val totalIntakes = snapshot.size()
            val takenIntakes = snapshot.documents.count { doc ->
                doc.getBoolean("taken") ?: (doc.getDate("takenAt") != null)
            }

            if (totalIntakes > 0) {
                (takenIntakes.toFloat() / totalIntakes) * 100f
            } else -1f
        } catch (e: Exception) {
            -1f
        }
    }

    /**
     * Get intake records formatted for the ReminderOptimizer.
     */
    suspend fun getIntakeRecords(daysBack: Int = 30): List<ReminderOptimizer.IntakeRecord> =
        withContext(Dispatchers.IO) {
            try {
                val userId = currentUserId ?: return@withContext emptyList()

                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_MONTH, -daysBack)
                val startDate = calendar.time

                val snapshot = intakesCollection
                    .whereEqualTo("userId", userId)
                    .whereGreaterThanOrEqualTo("takenAt", startDate)
                    .orderBy("takenAt", Query.Direction.DESCENDING)
                    .get()
                    .await()

                snapshot.documents.mapNotNull { doc ->
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
            } catch (e: Exception) {
                emptyList()
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
}

