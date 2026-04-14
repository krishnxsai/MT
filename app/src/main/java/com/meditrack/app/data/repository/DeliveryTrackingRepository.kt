package com.meditrack.app.data.repository

import android.util.Log
import com.meditrack.app.data.model.DeliveryTracking
import com.meditrack.app.data.model.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository for real-time delivery tracking data.
 *
 * Handles:
 * • Real-time tracking location updates
 * • Fetching current delivery person location
 * • Updating delivery person location (for delivery app)
 * • Querying tracking history
 */
class DeliveryTrackingRepository {

    companion object {
        private const val TAG = "DeliveryTrackingRepository"
        private const val TRACKING_COLLECTION = "deliveryTracking"
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val trackingCol by lazy { firestore.collection(TRACKING_COLLECTION) }

    // ─────────────── Real-Time Tracking ───────────────

    /**
     * Real-time flow of delivery tracking for an order.
     * Listens to the most recent tracking update for the given order.
     *
     * Used in patient app to track delivery person movement.
     */
    fun trackDeliveryFlow(
        orderId: String,
        providedTrackingId: String? = null
    ): Flow<DeliveryTracking?> = callbackFlow {
        if (orderId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        var fallbackListener: ListenerRegistration? = null
        val fallbackLock = Any()
        val defaultTrackingId = "ongoing_$orderId"
        val trackingId = providedTrackingId?.takeIf { it.isNotBlank() } ?: defaultTrackingId

        fun setupFallbackListener() {
            synchronized(fallbackLock) {
                if (fallbackListener != null) return
                fallbackListener = trackingCol
                    .whereEqualTo("orderId", orderId)
                    .orderBy("updatedAt", Query.Direction.DESCENDING)
                    .limit(1)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            val code = (error as? FirebaseFirestoreException)?.code
                            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                Log.e(TAG, "trackDeliveryFlow fallback denied for $orderId")
                            } else {
                                Log.w(TAG, "trackDeliveryFlow fallback error for $orderId: ${error.message}")
                            }
                            trySend(null)
                            return@addSnapshotListener
                        }

                        val doc = snapshot?.documents?.firstOrNull()
                        val tracking = doc?.let { snapshotDoc ->
                            snapshotDoc.data?.let { DeliveryTracking.fromMap(snapshotDoc.id, it) }
                        }
                        trySend(tracking)
                    }
            }
        }

        val listener = trackingCol.document(trackingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val code = (error as? FirebaseFirestoreException)?.code
                    if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Log.e(TAG, "trackDeliveryFlow denied for $orderId (doc=$trackingId)")
                    } else {
                        Log.w(TAG, "trackDeliveryFlow error for $orderId (doc=$trackingId): ${error.message}")
                    }
                    setupFallbackListener()
                    trySend(null)
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    Log.d(TAG, "trackDeliveryFlow missing doc $trackingId for $orderId, using fallback query")
                    setupFallbackListener()
                    trySend(null)
                    return@addSnapshotListener
                }

                val tracking = snapshot.data?.let {
                    DeliveryTracking.fromMap(snapshot.id, it)
                }
                trySend(tracking)
            }

        awaitClose {
            listener.remove()
            fallbackListener?.remove()
        }
    }

    // ─────────────── Location Update (Delivery App) ───────────────

    /**
     * Update delivery person's current location.
     * Called from delivery app/service every 3-5 seconds.
     *
     * @param tracking The tracking data with new location
     * @return Success if update was successful
     */
    suspend fun updateDeliveryLocation(tracking: DeliveryTracking): Resource<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val trackingId = tracking.id.ifBlank { "ongoing_${tracking.orderId}" }
                val data = tracking.toMap()

                trackingCol.document(trackingId).set(data).await()

                Log.d(TAG, "Location updated: ${tracking.orderId} at (${tracking.latitude}, ${tracking.longitude})")
                Resource.Success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "updateDeliveryLocation error: ${e.message}")
                Resource.Error(e.message ?: "Failed to update location")
            }
        }

    // ─────────────── Fetching ───────────────

    /**
     * Get the most recent tracking document for an order.
     * Used for initial load or as fallback if real-time listener fails.
     */
    suspend fun getLastLocation(orderId: String): Resource<DeliveryTracking?> =
        withContext(Dispatchers.IO) {
            try {
                val snapshot = trackingCol
                    .whereEqualTo("orderId", orderId)
                    .orderBy("updatedAt", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()

                val tracking = snapshot.documents.firstOrNull()?.let { doc ->
                    doc.data?.let { DeliveryTracking.fromMap(doc.id, it) }
                }

                Resource.Success(tracking)
            } catch (e: Exception) {
                Log.e(TAG, "getLastLocation error: ${e.message}")
                Resource.Error(e.message ?: "Failed to get location")
            }
        }

    /**
     * Get all tracking updates for an order (tracking history).
     * Useful for debugging or analytics.
     */
    suspend fun getTrackingHistory(orderId: String, limit: Int = 100): Resource<List<DeliveryTracking>> =
        withContext(Dispatchers.IO) {
            try {
                val snapshot = trackingCol
                    .whereEqualTo("orderId", orderId)
                    .orderBy("updatedAt", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .get()
                    .await()

                val tracking = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { DeliveryTracking.fromMap(doc.id, it) }
                }

                Resource.Success(tracking)
            } catch (e: Exception) {
                Log.e(TAG, "getTrackingHistory error: ${e.message}")
                Resource.Error(e.message ?: "Failed to get tracking history")
            }
        }

    // ─────────────── Cleanup ───────────────

    /**
     * End tracking for an order (called when order is DELIVERED or CANCELLED).
     * Keeps historical data but stops updating.
     */
    suspend fun endTracking(orderId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val trackingId = "ongoing_$orderId"
                val trackingDoc = trackingCol.document(trackingId)
                val snapshot = trackingDoc.get().await()

                if (!snapshot.exists()) {
                    return@withContext Resource.Success(Unit)
                }

                trackingDoc.update(
                    mapOf(
                        "isActive" to false,
                        "trackingStatus" to "ENDED",
                        "endedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                ).await()

                Log.d(TAG, "Tracking ended for order: $orderId")
                Resource.Success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "endTracking error: ${e.message}")
                Resource.Error(e.message ?: "Failed to end tracking")
            }
        }
}
