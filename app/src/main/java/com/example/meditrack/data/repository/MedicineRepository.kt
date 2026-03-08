package com.example.meditrack.data.repository

import com.example.meditrack.data.model.Medicine
import com.example.meditrack.data.model.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MedicineRepository {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val medicinesCollection by lazy { firestore.collection("medicines") }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    suspend fun addMedicine(medicine: Medicine): Resource<Medicine> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

            val medicineWithUser = medicine.copy(userId = userId)
            val docRef = medicinesCollection.document()
            val medicineToSave = medicineWithUser.copy(id = docRef.id)

            val data = medicineToSave.toMap().toMutableMap()
            data["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

            docRef.set(data).await()

            Resource.Success(medicineToSave)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to add medicine", e)
        }
    }

    suspend fun updateMedicine(medicine: Medicine): Resource<Medicine> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

            if (medicine.userId != userId) {
                return@withContext Resource.Error("Permission denied")
            }

            medicinesCollection.document(medicine.id).update(medicine.toMap()).await()

            Resource.Success(medicine)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update medicine", e)
        }
    }

    suspend fun deleteMedicine(medicineId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            medicinesCollection.document(medicineId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to delete medicine", e)
        }
    }

    suspend fun getMedicine(medicineId: String): Resource<Medicine> = withContext(Dispatchers.IO) {
        try {
            val doc = medicinesCollection.document(medicineId).get().await()

            if (doc.exists()) {
                val medicine = doc.toObject(Medicine::class.java)
                if (medicine != null) {
                    Resource.Success(medicine)
                } else {
                    Resource.Error("Failed to parse medicine")
                }
            } else {
                Resource.Error("Medicine not found")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get medicine", e)
        }
    }

    suspend fun getMedicines(): Resource<List<Medicine>> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

            val snapshot = medicinesCollection
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val medicines = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Medicine::class.java)
            }

            Resource.Success(medicines)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get medicines", e)
        }
    }

    fun getMedicinesFlow(): Flow<Resource<List<Medicine>>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            trySend(Resource.Error("User not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = medicinesCollection
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to get medicines"))
                    return@addSnapshotListener
                }

                val medicines = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Medicine::class.java)
                } ?: emptyList()

                trySend(Resource.Success(medicines))
            }

        awaitClose { listener.remove() }
    }

    suspend fun toggleMedicineActive(medicineId: String, isActive: Boolean): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            medicinesCollection.document(medicineId).update(
                mapOf(
                    "isActive" to isActive,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update medicine", e)
        }
    }

    suspend fun updateAlarmIds(medicineId: String, alarmIds: List<Int>): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            medicinesCollection.document(medicineId).update(
                mapOf(
                    "alarmIds" to alarmIds,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update alarm IDs", e)
        }
    }

    /**
     * Get all active medicines with low stock for the current user.
     */
    suspend fun getLowStockMedicines(): Resource<List<Medicine>> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

            val snapshot = medicinesCollection
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val lowStock = snapshot.documents
                .mapNotNull { it.toObject(Medicine::class.java) }
                .filter { it.isActive && it.isLowStock }

            Resource.Success(lowStock)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to check low stock", e)
        }
    }
}

