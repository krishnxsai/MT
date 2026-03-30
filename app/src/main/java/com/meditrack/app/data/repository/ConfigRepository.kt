package com.meditrack.app.data.repository

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Repository for managing sensitive configuration from Cloud Secret Manager via Firebase Remote Config.
 *
 * Architecture:
 * 1. Backend retrieves secrets from Google Cloud Secret Manager
 * 2. Backend populates Firebase Remote Config with encrypted/masked values
 * 3. Android app fetches from Firebase Remote Config
 * 4. Falls back to manifest meta-data if Remote Config unavailable
 *
 * This pattern ensures:
 * ✅ Secrets never stored in source code or strings.xml
 * ✅ Dynamic secret rotation without app updates
 * ✅ Server-side audit trail for secret access
 * ✅ Offline fallback capability
 */
class ConfigRepository @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
) {

    companion object {
        private const val TAG = "ConfigRepository"
        private const val DEFAULT_FETCH_INTERVAL_SECONDS = 3600L
        private const val MIN_KEY_LENGTH = 15

        // Firebase Remote Config keys (correspond to Cloud Secret Manager secrets)
        const val KEY_RAZORPAY_KEY_ID = "razorpay_key_id"
    }

    enum class RazorpayKeySource {
        REMOTE_CONFIG,
        RESOURCE_FALLBACK,
        NONE
    }

    data class RazorpayKeyResolution(
        val key: String?,
        val source: RazorpayKeySource,
        val reason: String
    )

    init {
        configureRemoteConfigDefaults()
    }

    private fun configureRemoteConfigDefaults() {
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(DEFAULT_FETCH_INTERVAL_SECONDS)
            .build()

        remoteConfig.setConfigSettingsAsync(settings)
        remoteConfig.setDefaultsAsync(
            mapOf(
                KEY_RAZORPAY_KEY_ID to ""
            )
        )
    }

    /**
     * Get Razorpay API Key from Firebase Remote Config.
     *
      * @return Valid Razorpay Key ID if available, empty string if missing/invalid
     *
     * Priority:
     * 1. Firebase Remote Config (server-side, from Cloud Secret Manager)
      * 2. Empty string (caller handles fallback strategy)
     */
    suspend fun getRazorpayKeyId(): String = withContext(Dispatchers.IO) {
        try {
            // Fetch latest from Firebase Remote Config
            remoteConfig.fetchAndActivate().await()
            Log.d(TAG, "Firebase Remote Config fetched successfully")

            // Get the key
            val key = remoteConfig.getString(KEY_RAZORPAY_KEY_ID).trim()

            if (isValidRazorpayKey(key)) {
                Log.d(TAG, "✅ Razorpay key loaded from Remote Config (length: ${key.length})")
                return@withContext key
            } else if (key.isNotEmpty()) {
                Log.w(TAG, "⚠️ Remote Config key format invalid, ignoring value")
                return@withContext ""
            } else {
                Log.w(TAG, "⚠️ Remote Config: $KEY_RAZORPAY_KEY_ID not found")
                return@withContext ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching Razorpay key from Remote Config: ${e.message}")
            Log.w(TAG, "⚠️ Caller will use configured fallback source")
            return@withContext ""
        }
    }

    /**
     * Resolve checkout key with runtime priority:
     * 1) Firebase Remote Config
     * 2) Resource fallback from current build variant
     */
    suspend fun resolveCheckoutKey(resourceFallbackKey: String?): RazorpayKeyResolution =
        withContext(Dispatchers.IO) {
            val remoteConfigKey = getRazorpayKeyId()
            if (remoteConfigKey.isNotEmpty()) {
                return@withContext RazorpayKeyResolution(
                    key = remoteConfigKey,
                    source = RazorpayKeySource.REMOTE_CONFIG,
                    reason = "Using Razorpay key from Firebase Remote Config"
                )
            }

            val fallbackKey = resourceFallbackKey?.trim().orEmpty()
            if (isValidRazorpayKey(fallbackKey)) {
                return@withContext RazorpayKeyResolution(
                    key = fallbackKey,
                    source = RazorpayKeySource.RESOURCE_FALLBACK,
                    reason = "Using build-time fallback Razorpay key"
                )
            }

            return@withContext RazorpayKeyResolution(
                key = null,
                source = RazorpayKeySource.NONE,
                reason = "No valid Razorpay key available from Remote Config or resources"
            )
        }

    /**
     * Check availability of Razorpay key (for diagnostics/monitoring).
     *
     * Non-blocking check - doesn't wait for network.
     * Useful for analytics and monitoring.
     */
    fun isRazorpayKeyAvailable(): Boolean {
        return try {
            val key = remoteConfig.getString(KEY_RAZORPAY_KEY_ID).trim()
            isValidRazorpayKey(key).also {
                Log.d(TAG, "Razorpay key availability: $it")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Razorpay key availability: ${e.message}")
            false
        }
    }

    private fun isValidRazorpayKey(key: String): Boolean {
        if (key.isBlank()) return false
        if (!(key.startsWith("rzp_test_") || key.startsWith("rzp_live_"))) return false
        if (key.length < MIN_KEY_LENGTH) return false

        val lower = key.lowercase()
        if (lower.contains("xxxx") || lower.contains("placeholder") || lower.contains("dummy")) {
            return false
        }

        return true
    }
}
