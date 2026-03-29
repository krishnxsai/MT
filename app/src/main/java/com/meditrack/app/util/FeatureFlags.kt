package com.meditrack.app.util

import android.util.Log

/**
 * Feature flags for staged rollout of new features.
 * Can be toggled via Firebase Remote Config for A/B testing.
 */
object FeatureFlags {
    private const val TAG = "FeatureFlags"

    // Feature flag names (match Firebase Remote Config keys)
    const val PAYMENT_ENABLED = "payment_enabled"
    const val NOTIFICATION_PREFS_ENABLED = "notification_prefs_enabled"
    const val INVENTORY_VALIDATION_ENABLED = "inventory_validation_enabled"
    const val DELIVERY_WINDOWS_ENABLED = "delivery_windows_enabled"
    const val RETENTION_CLEANUP_ENABLED = "retention_cleanup_enabled"

    // Local state (can be overridden by Remote Config)
    private val flagOverrides = mutableMapOf<String, Boolean>()

    /**
     * Check if a feature flag is enabled.
     * First checks local overrides, then falls back to default values.
     * In production, should check Firebase Remote Config.
     */
    fun isEnabled(flagName: String): Boolean {
        // Check local override first
        flagOverrides[flagName]?.let { return it }

        // Default values for each feature
        return when (flagName) {
            PAYMENT_ENABLED -> true  // Enabled - Phase 1 complete
            NOTIFICATION_PREFS_ENABLED -> false
            INVENTORY_VALIDATION_ENABLED -> false
            DELIVERY_WINDOWS_ENABLED -> false
            RETENTION_CLEANUP_ENABLED -> false
            else -> false
        }
    }

    /**
     * Override a feature flag locally (for testing).
     */
    fun setFlag(flagName: String, enabled: Boolean) {
        Log.d(TAG, "Setting $flagName = $enabled")
        flagOverrides[flagName] = enabled
    }

    /**
     * Reset all local overrides.
     */
    fun resetOverrides() {
        Log.d(TAG, "Resetting all feature flag overrides")
        flagOverrides.clear()
    }

    /**
     * Get all current flag states for debugging.
     */
    fun getAllFlags(): Map<String, Boolean> {
        return mapOf(
            PAYMENT_ENABLED to isEnabled(PAYMENT_ENABLED),
            NOTIFICATION_PREFS_ENABLED to isEnabled(NOTIFICATION_PREFS_ENABLED),
            INVENTORY_VALIDATION_ENABLED to isEnabled(INVENTORY_VALIDATION_ENABLED),
            DELIVERY_WINDOWS_ENABLED to isEnabled(DELIVERY_WINDOWS_ENABLED),
            RETENTION_CLEANUP_ENABLED to isEnabled(RETENTION_CLEANUP_ENABLED)
        )
    }
}
