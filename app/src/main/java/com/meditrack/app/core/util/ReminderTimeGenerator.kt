package com.meditrack.app.core.util

object ReminderTimeGenerator {

    fun generateFromFrequency(frequency: String): List<String> {
        return when (frequency.lowercase()) {
            "once daily" -> listOf("08:00")
            "twice daily" -> listOf("08:00", "20:00")
            "three times daily" -> listOf("08:00", "14:00", "20:00")
            "four times daily" -> listOf("08:00", "12:00", "16:00", "20:00")
            "every 6 hours" -> listOf("06:00", "12:00", "18:00", "00:00")
            "every 8 hours" -> listOf("08:00", "16:00", "00:00")
            "every 12 hours" -> listOf("08:00", "20:00")
            "at bedtime" -> listOf("22:00")
            "in the morning" -> listOf("08:00")
            "weekly" -> listOf("08:00")
            else -> listOf("08:00")
        }
    }
}
