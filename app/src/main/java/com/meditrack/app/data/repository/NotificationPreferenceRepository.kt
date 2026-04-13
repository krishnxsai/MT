package com.meditrack.app.data.repository

import android.util.Log
import com.meditrack.app.data.model.NotificationPreference
import com.meditrack.app.data.model.NotificationType
import com.meditrack.app.data.model.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalTime
import javax.inject.Inject

/**
 * Repository for managing user notification preferences.
 *
 * Handles:
 *  • Fetching user's notification preferences
 *  • Updating preferences in Firestore
 *  • Checking if a notification should be sent (respects quiet hours)
 *  • Managing quiet hours (do not disturb)
 */
class NotificationPreferenceRepository @Inject constructor() {

    companion object {
        private const val TAG = "NotificationPrefRepo"
        private const val PREFERENCES_COLLECTION = "notificationPreferences"
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val preferencesCol by lazy { firestore.collection(PREFERENCES_COLLECTION) }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // ─────────────── Preference Management ───────────────

    /**
     * Real-time flow of user's notification preferences.
     * If user doesn't have preferences, returns defaults.
     */
    fun getPreferencesFlow(): Flow<Resource<NotificationPreference>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            trySend(Resource.Error("Not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = preferencesCol.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "getPreferencesFlow error: ${error.message}")
                    // Return default preferences on error
                    trySend(Resource.Success(NotificationPreference.default(userId)))
                    return@addSnapshotListener
                }

                val preferences = if (snapshot != null && snapshot.exists()) {
                    snapshot.data?.let { NotificationPreference.fromMap(it) }
                        ?: NotificationPreference.default(userId)
                } else {
                    NotificationPreference.default(userId)
                }

                trySend(Resource.Success(preferences))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get user's preferences (one-shot).
     */
    suspend fun getPreferences(): Resource<NotificationPreference> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")

            val snapshot = preferencesCol.document(userId).get().await()
            val preferences = if (snapshot.exists()) {
                snapshot.data?.let { NotificationPreference.fromMap(it) }
                    ?: NotificationPreference.default(userId)
            } else {
                NotificationPreference.default(userId)
            }

            Resource.Success(preferences)
        } catch (e: Exception) {
            Log.e(TAG, "getPreferences error: ${e.message}")
            Resource.Error(e.message ?: "Failed to get preferences")
        }
    }

    /**
     * Update user's notification preferences.
     */
    suspend fun updatePreferences(preferences: NotificationPreference): Resource<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val userId = currentUserId ?: return@withContext Resource.Error("Not logged in")

                val data = preferences.copy(userId = userId).toMap().toMutableMap()
                preferencesCol.document(userId).set(data).await()

                Log.d(TAG, "Preferences updated for user: $userId")
                Resource.Success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "updatePreferences error: ${e.message}")
                Resource.Error(e.message ?: "Failed to update preferences")
            }
        }

    // ─────────────── Notification Permission Checking ───────────────

    /**
     * Check if a specific notification type should be sent.
     * Returns false if:
     *  • Notification type is disabled
     *  • Current time is within quiet hours
     *  • User is not logged in
     *
     * @param type The notification type to check
     * @return true if notification should be sent, false otherwise
     */
    suspend fun isNotificationAllowed(
        type: NotificationType,
        bypassQuietHours: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefsResult = getPreferences()
            if (prefsResult !is Resource.Success) {
                return@withContext true  // Default to allow if we can't fetch preferences
            }

            val prefs = prefsResult.data

            // Check if notification type is enabled
            val isTypeEnabled = when (type) {
                NotificationType.APPOINTMENT -> prefs.appointmentReminders
                NotificationType.PRESCRIPTION -> prefs.prescriptionNotifications
                NotificationType.ORDER_STATUS -> prefs.orderStatusUpdates
                NotificationType.PROMOTIONAL -> prefs.promotionalMessages
                NotificationType.HEALTH_ALERT -> prefs.healthAlerts
            }

            if (!isTypeEnabled) {
                Log.d(TAG, "Notification type $type is disabled")
                return@withContext false
            }

            // Critical reminders can intentionally bypass quiet-hour suppression.
            if (bypassQuietHours) {
                return@withContext true
            }

            // Check quiet hours
            if (!isCurrentTimeInQuietHours(prefs)) {
                return@withContext true  // Not in quiet hours, allow
            }

            Log.d(TAG, "Notification suppressed: current time is within quiet hours")
            return@withContext false
        } catch (e: Exception) {
            Log.w(TAG, "isNotificationAllowed error: ${e.message}")
            return@withContext true  // Allow notification if we can't check preferences
        }
    }

    /**
     * Check if current time is within quiet hours.
     *
     * @param preferences User's notification preferences
     * @return true if current time is within quiet hours (notification should NOT be sent)
     */
    private fun isCurrentTimeInQuietHours(preferences: NotificationPreference): Boolean {
        if (!preferences.quietHoursEnabled) {
            return false  // Quiet hours disabled
        }

        try {
            val now = LocalTime.now()
            val startTime = LocalTime.parse(preferences.quietHourStart)
            val endTime = LocalTime.parse(preferences.quietHourEnd)

            // Handle case where quiet hours span midnight (e.g., 22:00 to 08:00)
            return if (startTime.isBefore(endTime)) {
                // Normal case: 08:00 to 22:00
                now.isAfter(startTime) && now.isBefore(endTime)
            } else {
                // Spans midnight: 22:00 to 08:00
                now.isAfter(startTime) || now.isBefore(endTime)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing quiet hours: ${e.message}")
            return false  // If parsing fails, don't suppress notification
        }
    }

    /**
     * Batch update multiple notification types.
     */
    suspend fun updateNotificationTypes(
        types: Map<NotificationType, Boolean>
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val prefsResult = getPreferences()
            if (prefsResult !is Resource.Success) {
                return@withContext Resource.Error("Failed to fetch current preferences")
            }

            val currentPrefs = prefsResult.data
            val updatedChannels = currentPrefs.notificationChannels.toMutableMap()

            types.forEach { (type, enabled) ->
                updatedChannels[type.name] = enabled
            }

            val updated = currentPrefs.copy(notificationChannels = updatedChannels)
            updatePreferences(updated)
        } catch (e: Exception) {
            Log.e(TAG, "updateNotificationTypes error: ${e.message}")
            Resource.Error(e.message ?: "Failed to update notification types")
        }
    }

    /**
     * Update quiet hours.
     */
    suspend fun updateQuietHours(
        enabled: Boolean,
        startTime: String,
        endTime: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val prefsResult = getPreferences()
            if (prefsResult !is Resource.Success) {
                return@withContext Resource.Error("Failed to fetch current preferences")
            }

            val updated = prefsResult.data.copy(
                quietHoursEnabled = enabled,
                quietHourStart = startTime,
                quietHourEnd = endTime
            )

            updatePreferences(updated)
        } catch (e: Exception) {
            Log.e(TAG, "updateQuietHours error: ${e.message}")
            Resource.Error(e.message ?: "Failed to update quiet hours")
        }
    }
}
