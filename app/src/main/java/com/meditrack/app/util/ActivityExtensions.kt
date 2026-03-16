package com.meditrack.app.util

import android.app.Activity
import android.os.Build

/**
 * Suppress-safe wrapper around the deprecated [Activity.overridePendingTransition].
 *
 * On API 34+ we forward to [Activity.overrideActivityTransition]; on older devices
 * we fall back to the legacy API so the project compiles without deprecation warnings.
 */
fun Activity.overrideTransitionCompat(enterAnim: Int, exitAnim: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, enterAnim, exitAnim)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(enterAnim, exitAnim)
    }
}

