package com.meditrack.app.data.analytics

import com.meditrack.app.data.model.Medicine
import java.util.Date

/**
 * Reminder Optimizer — analyzes medicine intake patterns to suggest
 * optimal reminder times based on user behavior.
 *
 * Logic:
 *   1. Compares actual intake timestamps with scheduled reminder times
 *   2. Identifies consistently-missed time slots (>50% skip rate)
 *   3. Finds high-adherence time windows
 *   4. Suggests shifting reminders to better-performing times
 */
object ReminderOptimizer {

    // ── Data classes ────────────────────────────────────────────

    data class IntakeRecord(
        val medicineId: String,
        val medicineName: String,
        val scheduledTime: String,   // "08:00"
        val actualTakenTime: Date?,  // null = missed
        val wasOnTime: Boolean = false
    )

    data class ReminderSuggestion(
        val medicineId: String,
        val medicineName: String,
        val currentTime: String,         // "08:00"
        val suggestedTime: String,       // "08:30"
        val reason: String,
        val currentAdherenceRate: Float,  // 0–100
        val expectedImprovement: Float   // estimated improvement %
    )

    data class TimeSlotAnalysis(
        val timeSlot: String,
        val totalScheduled: Int,
        val totalTaken: Int,
        val adherenceRate: Float,
        val avgDelayMinutes: Int
    )

    data class OptimizationResult(
        val suggestions: List<ReminderSuggestion>,
        val overallAdherenceRate: Float,
        val bestTimeSlot: String?,
        val worstTimeSlot: String?,
        val analysis: List<TimeSlotAnalysis>
    )

    // ── Public API ──────────────────────────────────────────────

    /**
     * Analyze intake records and suggest optimized reminder times.
     *
     * @param medicines Active medicines with reminder times
     * @param intakeRecords Historical intake records (from medicineIntakes collection)
     */
    fun optimizeReminders(
        medicines: List<Medicine>,
        intakeRecords: List<IntakeRecord>
    ): OptimizationResult {
        val activeMedicines = medicines.filter { it.isActive && it.reminderTimes.isNotEmpty() }
        if (activeMedicines.isEmpty()) {
            return OptimizationResult(
                suggestions = emptyList(),
                overallAdherenceRate = 100f,
                bestTimeSlot = null,
                worstTimeSlot = null,
                analysis = emptyList()
            )
        }

        // Analyze each time slot across all medicines
        val slotAnalyses = analyzeTimeSlots(activeMedicines, intakeRecords)

        // Find best-performing time windows
        val bestSlot = slotAnalyses.maxByOrNull { it.adherenceRate }
        val worstSlot = slotAnalyses.minByOrNull { it.adherenceRate }

        // Generate suggestions for poorly-performing slots
        val suggestions = mutableListOf<ReminderSuggestion>()

        for (medicine in activeMedicines) {
            for (reminderTime in medicine.reminderTimes) {
                val slotRecords = intakeRecords.filter {
                    it.medicineId == medicine.id && it.scheduledTime == reminderTime
                }
                if (slotRecords.size < 3) continue // Need enough data

                val takenCount = slotRecords.count { it.actualTakenTime != null }
                val adherenceRate = (takenCount.toFloat() / slotRecords.size) * 100f

                if (adherenceRate < 60) {
                    // This slot has poor adherence — suggest alternative
                    val suggestion = suggestBetterTime(
                        medicine, reminderTime, adherenceRate, slotRecords, bestSlot
                    )
                    if (suggestion != null) {
                        suggestions.add(suggestion)
                    }
                }
            }
        }

        // Calculate overall adherence
        val totalRecords = intakeRecords.size
        val takenRecords = intakeRecords.count { it.actualTakenTime != null }
        val overallAdherence = if (totalRecords > 0) {
            (takenRecords.toFloat() / totalRecords) * 100f
        } else 100f

        return OptimizationResult(
            suggestions = suggestions.sortedBy { it.currentAdherenceRate },
            overallAdherenceRate = overallAdherence,
            bestTimeSlot = bestSlot?.timeSlot,
            worstTimeSlot = worstSlot?.timeSlot,
            analysis = slotAnalyses
        )
    }

    /**
     * Simple analysis without intake records — uses medicine schedule patterns.
     * Useful when no intake history is available yet.
     */
    fun analyzeSchedulePatterns(medicines: List<Medicine>): List<ReminderSuggestion> {
        val activeMedicines = medicines.filter { it.isActive }
        val suggestions = mutableListOf<ReminderSuggestion>()

        // Detect conflicting times (multiple meds at same time)
        val timeGroups = activeMedicines.flatMap { med ->
            med.reminderTimes.map { time -> time to med }
        }.groupBy { it.first }

        for ((time, medsAtTime) in timeGroups) {
            if (medsAtTime.size > 3) {
                // Too many medications at the same time
                for ((_, med) in medsAtTime.drop(3)) {
                    val suggestedTime = shiftTime(time, 30)
                    suggestions.add(
                        ReminderSuggestion(
                            medicineId = med.id,
                            medicineName = med.name,
                            currentTime = time,
                            suggestedTime = suggestedTime,
                            reason = "Too many medications (${medsAtTime.size}) at $time. Spreading them out improves adherence.",
                            currentAdherenceRate = -1f,
                            expectedImprovement = 15f
                        )
                    )
                }
            }
        }

        // Detect very early or very late reminders
        for (med in activeMedicines) {
            for (time in med.reminderTimes) {
                val minutes = timeToMinutes(time)
                if (minutes < 360) { // Before 6:00 AM
                    suggestions.add(
                        ReminderSuggestion(
                            medicineId = med.id,
                            medicineName = med.name,
                            currentTime = time,
                            suggestedTime = "07:00",
                            reason = "Very early reminders are often missed. Moving to 7:00 AM may improve adherence.",
                            currentAdherenceRate = -1f,
                            expectedImprovement = 20f
                        )
                    )
                } else if (minutes > 1380) { // After 11:00 PM
                    suggestions.add(
                        ReminderSuggestion(
                            medicineId = med.id,
                            medicineName = med.name,
                            currentTime = time,
                            suggestedTime = "21:00",
                            reason = "Late-night reminders are often missed. Consider moving to 9:00 PM.",
                            currentAdherenceRate = -1f,
                            expectedImprovement = 20f
                        )
                    )
                }
            }
        }

        return suggestions
    }

    // ── Internal Analysis ───────────────────────────────────────

    private fun analyzeTimeSlots(
        medicines: List<Medicine>,
        intakeRecords: List<IntakeRecord>
    ): List<TimeSlotAnalysis> {
        val allTimeSlots = medicines.flatMap { it.reminderTimes }.distinct()

        return allTimeSlots.map { slot ->
            val slotRecords = intakeRecords.filter { it.scheduledTime == slot }
            val taken = slotRecords.count { it.actualTakenTime != null }
            val total = slotRecords.size
            val adherenceRate = if (total > 0) (taken.toFloat() / total) * 100f else 100f

            // Calculate average delay for taken medications
            val delays = slotRecords.filter { it.actualTakenTime != null && it.scheduledTime.isNotEmpty() }
            val avgDelay = if (delays.isNotEmpty()) {
                delays.sumOf { record ->
                    val scheduledMinutes = timeToMinutes(record.scheduledTime)
                    val calendar = java.util.Calendar.getInstance().apply { time = record.actualTakenTime!! }
                    val actualMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                            calendar.get(java.util.Calendar.MINUTE)
                    kotlin.math.abs(actualMinutes - scheduledMinutes)
                } / delays.size
            } else 0

            TimeSlotAnalysis(
                timeSlot = slot,
                totalScheduled = total,
                totalTaken = taken,
                adherenceRate = adherenceRate,
                avgDelayMinutes = avgDelay
            )
        }
    }

    private fun suggestBetterTime(
        medicine: Medicine,
        currentTime: String,
        currentAdherence: Float,
        records: List<IntakeRecord>,
        bestSlot: TimeSlotAnalysis?
    ): ReminderSuggestion? {
        // Strategy 1: Shift to nearest meal-aligned time
        val currentMinutes = timeToMinutes(currentTime)
        val mealTimes = listOf(480, 780, 1200) // 8:00, 13:00, 20:00

        val nearestMealTime = mealTimes.minByOrNull { kotlin.math.abs(it - currentMinutes) }
        val suggestedMinutes = nearestMealTime ?: (currentMinutes + 30)
        val suggestedTime = minutesToTime(suggestedMinutes)

        if (suggestedTime == currentTime) return null

        return ReminderSuggestion(
            medicineId = medicine.id,
            medicineName = medicine.name,
            currentTime = currentTime,
            suggestedTime = suggestedTime,
            reason = "Adherence at $currentTime is only ${currentAdherence.toInt()}%. Aligning with meal times often improves consistency.",
            currentAdherenceRate = currentAdherence,
            expectedImprovement = 20f
        )
    }

    // ── Helpers ─────────────────────────────────────────────────

    private fun timeToMinutes(time: String): Int {
        val parts = time.split(":")
        if (parts.size != 2) return 480 // Default 8:00
        val hours = parts[0].toIntOrNull() ?: 8
        val minutes = parts[1].toIntOrNull() ?: 0
        return hours * 60 + minutes
    }

    private fun minutesToTime(totalMinutes: Int): String {
        val clamped = totalMinutes.coerceIn(0, 1439)
        val h = clamped / 60
        val m = clamped % 60
        return String.format("%02d:%02d", h, m)
    }

    private fun shiftTime(time: String, shiftMinutes: Int): String {
        val current = timeToMinutes(time)
        return minutesToTime(current + shiftMinutes)
    }
}

