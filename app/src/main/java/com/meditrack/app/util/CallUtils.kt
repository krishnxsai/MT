package com.meditrack.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.meditrack.app.R

/**
 * Utility object for making phone calls.
 * Uses ACTION_DIAL which opens the dialer with the number pre-filled.
 * This does not require any permissions since the user must press call.
 */
object CallUtils {

    /**
     * Opens the phone dialer with the given phone number.
     * Shows a toast if the phone number is blank.
     *
     * @param context The context to use for starting the activity and showing toast.
     * @param phoneNumber The phone number to dial.
     * @param contactName Optional name for better error messages.
     */
    fun dialPhoneNumber(context: Context, phoneNumber: String, contactName: String? = null) {
        if (phoneNumber.isBlank()) {
            val message = if (contactName != null) {
                context.getString(R.string.no_phone_number_for, contactName)
            } else {
                context.getString(R.string.no_phone_number_available)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${phoneNumber.trim()}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.unable_to_make_call, Toast.LENGTH_SHORT).show()
        }
    }
}
