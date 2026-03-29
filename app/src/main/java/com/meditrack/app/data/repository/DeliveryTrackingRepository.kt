package com.meditrack.app.data.repository

import android.util.Log
import com.meditrack.app.data.model.DeliveryTracking
import com.meditrack.app.data.model.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

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
    fun trackDeliveryFlow(orderId: String): Flow<DeliveryTracking?> = callbackFlow {
        if (orderId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = trackingCol
            .whereEqualTo("orderId", orderId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "trackDeliveryFlow error: ${error.message}")
                    trySend(null)
                    return@addSnapshotListener
                }

                val tracking = snapshot?.documents?.firstOrNull()?.let { doc ->
                    doc.data?.let { DeliveryTracking.fromMap(doc.id, it) }
                }
                trySend(tracking)
            }

        awaitClose { listener.remove() }
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
                // Mark tracking as ended by updating a flag would require schema change
                // For now, just log the end
                Log.d(TAG, "Tracking ended for order: $orderId")
                Resource.Success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "endTracking error: ${e.message}")
                Resource.Error(e.message ?: "Failed to end tracking")
            }
        }
}
