package com.meditrack.app.util

import java.text.NumberFormat
import java.util.Locale

private val inrLocale = Locale.forLanguageTag("en-IN")

fun formatInr(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(inrLocale)
    return formatter.format(amount)
}
