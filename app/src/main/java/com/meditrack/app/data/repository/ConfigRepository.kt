package com.meditrack.app.data.repository

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
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
class ConfigRepository @Inject constructor() {

    companion object {
        private const val TAG = "ConfigRepository"

        // Firebase Remote Config keys (correspond to Cloud Secret Manager secrets)
        const val KEY_RAZORPAY_KEY_ID = "razorpay_key_id"
    }

    private val remoteConfig: FirebaseRemoteConfig by lazy {
        FirebaseRemoteConfig.getInstance()
    }

    /**
     * Get Razorpay API Key from Firebase Remote Config.
     *
     * @return Razorpay Key ID if available, empty string if not found
     *
     * Priority:
     * 1. Firebase Remote Config (server-side, from Cloud Secret Manager)
     * 2. Manifest meta-data (build-time fallback)
     * 3. Empty string (error case - will use SDK's fallback)
     */
    suspend fun getRazorpayKeyId(): String = withContext(Dispatchers.IO) {
        try {
            // Fetch latest from Firebase Remote Config
            remoteConfig.fetchAndActivate().await()
            Log.d(TAG, "Firebase Remote Config fetched successfully")

            // Get the key
            val key = remoteConfig.getString(KEY_RAZORPAY_KEY_ID)

            if (key.isNotEmpty()) {
                Log.d(TAG, "✅ Razorpay key loaded from Remote Config (length: ${key.length})")
                return@withContext key
            } else {
                Log.w(TAG, "⚠️ Remote Config: $KEY_RAZORPAY_KEY_ID not found - SDK will use manifest meta-data")
                return@withContext ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching Razorpay key from Remote Config: ${e.message}")
            Log.w(TAG, "⚠️ Will use manifest meta-data as fallback")
            return@withContext ""
        }
    }

    /**
     * Check availability of Razorpay key (for diagnostics/monitoring).
     *
     * Non-blocking check - doesn't wait for network.
     * Useful for analytics and monitoring.
     */
    fun isRazorpayKeyAvailable(): Boolean {
        return try {
            val key = remoteConfig.getString(KEY_RAZORPAY_KEY_ID)
            key.isNotEmpty().also {
                Log.d(TAG, "Razorpay key availability: $it")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Razorpay key availability: ${e.message}")
            false
        }
    }
}
