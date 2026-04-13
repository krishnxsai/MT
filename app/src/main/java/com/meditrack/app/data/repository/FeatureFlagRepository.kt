package com.meditrack.app.data.repository

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.meditrack.app.data.model.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Repository for managing feature flags via Firebase Remote Config.
 *
 * Provides:
 *  • Real-time feature flag loading
 *  • Local caching (1 hour TTL)
 *  • Observable flag changes
 *  • A/B testing support
 */
class FeatureFlagRepository @Inject constructor() {

    companion object {
        private const val TAG = "FeatureFlagRepository"
        private const val CACHE_TTL_SECONDS = 3600L  // 1 hour cache
    }

    private val remoteConfig: FirebaseRemoteConfig by lazy {
        FirebaseRemoteConfig.getInstance()
    }

    private val flagCache = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /**
     * Check if a feature flag is enabled.
     * First checks local cache, then Firebase Remote Config.
     */
    suspend fun isEnabled(flagName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            flagCache.value[flagName]?.let { return@withContext it }

            // Fetch from Remote Config
            remoteConfig.fetchAndActivate().await()

            val value = remoteConfig.getBoolean(flagName)
            Log.d(TAG, "Feature flag $flagName = $value")

            // Update cache
            flagCache.update { map ->
                map + (flagName to value)
            }

            value
        } catch (e: Exception) {
            Log.e(TAG, "Error checking feature flag $flagName: ${e.message}")
            false  // Default to disabled on error
        }
    }

    /**
     * Observe a feature flag with real-time updates.
     */
    fun observeFlag(flagName: String): Flow<Boolean> = kotlinx.coroutines.flow.flow {
        try {
            while (true) {
                val enabled = isEnabled(flagName)
                emit(enabled)

                // Check again after TTL
                kotlinx.coroutines.delay(CACHE_TTL_SECONDS * 1000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error observing flag $flagName: ${e.message}")
            emit(false)
        }
    }

    /**
     * Get all configured flags from Remote Config.
     */
    suspend fun getAllFlags(): Resource<Map<String, Boolean>> = withContext(Dispatchers.IO) {
        try {
            remoteConfig.fetchAndActivate().await()

            val flags = mapOf(
                "payment_enabled" to remoteConfig.getBoolean("payment_enabled"),
                "notification_prefs_enabled" to remoteConfig.getBoolean("notification_prefs_enabled"),
                "inventory_validation_enabled" to remoteConfig.getBoolean("inventory_validation_enabled"),
                "delivery_windows_enabled" to remoteConfig.getBoolean("delivery_windows_enabled"),
                "retention_cleanup_enabled" to remoteConfig.getBoolean("retention_cleanup_enabled"),
                "cloud_risk_scoring_enabled" to remoteConfig.getBoolean("cloud_risk_scoring_enabled")
            )

            Log.d(TAG, "Fetched ${flags.size} feature flags from Remote Config")
            Resource.Success(flags)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch feature flags: ${e.message}")
            Resource.Error(e.message ?: "Failed to fetch feature flags")
        }
    }

    /**
     * Force refresh feature flags from Remote Config.
     */
    suspend fun refreshFlags(): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            remoteConfig.fetchAndActivate().await()
            flagCache.value = emptyMap()  // Clear cache to force reload
            Log.d(TAG, "Feature flags refreshed")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh feature flags: ${e.message}")
            Resource.Error(e.message ?: "Failed to refresh feature flags")
        }
    }
}
