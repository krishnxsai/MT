package com.meditrack.app.ui.settings

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.model.NotificationPreference
import com.meditrack.app.data.model.NotificationType
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.NotificationPreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for notification settings screen.
 * Manages UI state for notification preferences and quiet hours.
 */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val preferenceRepository: NotificationPreferenceRepository
) : ViewModel() {

    companion object {
        private const val TAG = "NotificationSettingsVM"
    }

    // UI State
    private val _preferences = MutableLiveData<NotificationPreference?>(null)
    val preferences: LiveData<NotificationPreference?> = _preferences

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private val _successMessage = MutableLiveData<String?>(null)
    val successMessage: LiveData<String?> = _successMessage

    init {
        loadPreferences()
    }

    /**
     * Load user's current notification preferences.
     */
    fun loadPreferences() {
        viewModelScope.launch {
            _loading.value = true
            _errorMessage.value = null

            val result = preferenceRepository.getPreferences()
            when (result) {
                is Resource.Success -> {
                    Log.d(TAG, "Preferences loaded")
                    _preferences.value = result.data
                }
                is Resource.Error -> {
                    Log.e(TAG, "Failed to load preferences: ${result.message}")
                    _errorMessage.value = result.message
                }
                is Resource.Loading -> {
                    _loading.value = true
                }
            }
            _loading.value = false
        }
    }

    /**
     * Toggle a specific notification type on/off.
     */
    fun toggleNotificationType(type: NotificationType, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val current = _preferences.value ?: return@launch
                val updated = updateNotificationTypeInPreference(current, type, enabled)
                _preferences.value = updated

                val result = preferenceRepository.updatePreferences(updated)
                if (result !is Resource.Success) {
                    _errorMessage.value = "Failed to save preference"
                    val errorMsg = if (result is Resource.Error) result.message else "Unknown error"
                    Log.e(TAG, "Failed to toggle notification type: $errorMsg")
                } else {
                    Log.d(TAG, "Notification type $type toggled to $enabled")
                }
            } catch (e: Exception) {
                Log.e(TAG, "toggleNotificationType error: ${e.message}")
                _errorMessage.value = e.message
            }
        }
    }

    /**
     * Update quiet hours settings.
     */
    fun updateQuietHours(
        enabled: Boolean,
        startTime: String,
        endTime: String
    ) {
        viewModelScope.launch {
            try {
                val current = _preferences.value ?: return@launch
                val updated = current.copy(
                    quietHoursEnabled = enabled,
                    quietHourStart = startTime,
                    quietHourEnd = endTime
                )
                _preferences.value = updated

                val result = preferenceRepository.updateQuietHours(enabled, startTime, endTime)
                if (result !is Resource.Success) {
                    _errorMessage.value = "Failed to save quiet hours"
                    val errorMsg = if (result is Resource.Error) result.message else "Unknown error"
                    Log.e(TAG, "Failed to update quiet hours: $errorMsg")
                } else {
                    _successMessage.value = "Quiet hours updated"
                    Log.d(TAG, "Quiet hours updated: $startTime - $endTime (enabled: $enabled)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateQuietHours error: ${e.message}")
                _errorMessage.value = e.message
            }
        }
    }

    /**
     * Helper to update notification type in preference object.
     */
    private fun updateNotificationTypeInPreference(
        prefs: NotificationPreference,
        type: NotificationType,
        enabled: Boolean
    ): NotificationPreference {
        val updatedChannels = prefs.notificationChannels.toMutableMap()
        updatedChannels[type.name] = enabled

        return prefs.copy(
            notificationChannels = updatedChannels,
            // Also update legacy fields for backward compatibility
            appointmentReminders = if (type == NotificationType.APPOINTMENT) enabled else prefs.appointmentReminders,
            prescriptionNotifications = if (type == NotificationType.PRESCRIPTION) enabled else prefs.prescriptionNotifications,
            orderStatusUpdates = if (type == NotificationType.ORDER_STATUS) enabled else prefs.orderStatusUpdates,
            promotionalMessages = if (type == NotificationType.PROMOTIONAL) enabled else prefs.promotionalMessages,
            healthAlerts = if (type == NotificationType.HEALTH_ALERT) enabled else prefs.healthAlerts
        )
    }

    /**
     * Clear error messages.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Clear success messages.
     */
    fun clearSuccess() {
        _successMessage.value = null
    }
}
