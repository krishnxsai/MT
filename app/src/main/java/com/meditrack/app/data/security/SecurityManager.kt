package com.meditrack.app.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Security Manager for handling local data protection and security operations.
 */
class SecurityManager(private val context: Context) {

    companion object {
        private const val TAG = "SecurityManager"
        private const val SECURE_PREFS_NAME = "meditrack_secure_prefs"
        private const val KEY_LAST_LOGIN = "last_login"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_ROLE = "user_role"
    }

    private val securePreferences: SharedPreferences by lazy {
        context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Store session information
     */
    fun storeSessionInfo(userId: String, userRole: String) {
        securePreferences.edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_ROLE, userRole)
            putLong(KEY_LAST_LOGIN, System.currentTimeMillis())
            apply()
        }
        Log.d(TAG, "Session info stored")
    }

    /**
     * Get stored user ID
     */
    fun getStoredUserId(): String? {
        return securePreferences.getString(KEY_USER_ID, null)
    }

    /**
     * Get stored user role
     */
    fun getStoredUserRole(): String? {
        return securePreferences.getString(KEY_USER_ROLE, null)
    }

    /**
     * Check if stored session is valid
     */
    fun isSessionValid(): Boolean {
        val lastLogin = securePreferences.getLong(KEY_LAST_LOGIN, 0)
        val sessionTimeout = 30L * 24 * 60 * 60 * 1000 // 30 days
        return System.currentTimeMillis() - lastLogin < sessionTimeout
    }

    /**
     * Clear all stored session data - called on logout
     */
    fun clearSessionData() {
        securePreferences.edit().clear().apply()
        Log.d(TAG, "Session data cleared")
    }

    /**
     * Perform secure logout
     */
    suspend fun secureLogout(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Clear secure preferences
            clearSessionData()

            // Clear Firebase auth
            FirebaseAuth.getInstance().signOut()

            // Clear app cache (non-encrypted data)
            clearAppCache()

            Log.d(TAG, "Secure logout completed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error during secure logout: ${e.message}")
            false
        }
    }

    /**
     * Clear application cache
     */
    private fun clearAppCache() {
        try {
            context.cacheDir.deleteRecursively()
            Log.d(TAG, "App cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache: ${e.message}")
        }
    }

    /**
     * Verify current user role matches stored role
     */
    fun verifyUserRole(expectedRole: String): Boolean {
        val storedRole = getStoredUserRole()
        return storedRole == expectedRole
    }

    /**
     * Store a value
     */
    fun storeSecureValue(key: String, value: String) {
        securePreferences.edit().putString(key, value).apply()
    }

    /**
     * Get a value
     */
    fun getSecureValue(key: String): String? {
        return securePreferences.getString(key, null)
    }

    /**
     * Remove a value
     */
    fun removeSecureValue(key: String) {
        securePreferences.edit().remove(key).apply()
    }

    /**
     * Check if device has security features enabled
     */
    fun isDeviceSecure(): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE)
            as android.app.KeyguardManager
        return keyguardManager.isDeviceSecure
    }

    /**
     * Log security event for audit
     */
    fun logSecurityEvent(event: String, details: Map<String, Any?> = emptyMap()) {
        val userId = getStoredUserId() ?: "unknown"
        Log.i(TAG, "Security Event [$userId]: $event - $details")
    }
}

