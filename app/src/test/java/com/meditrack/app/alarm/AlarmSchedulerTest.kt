package com.meditrack.app.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class AlarmSchedulerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val scheduler = AlarmScheduler(context)

    @Test
    fun `scheduleMedicineAlarms returns empty for inactive and as needed medicines`() {
        val inactiveMedicine = sampleMedicine(
            id = "med-inactive",
            repeatType = RepeatType.DAILY,
            isActive = false,
            reminderTimes = listOf(nextTimeString(2))
        )
        val asNeededMedicine = sampleMedicine(
            id = "med-as-needed",
            repeatType = RepeatType.AS_NEEDED,
            isActive = true,
            reminderTimes = listOf(nextTimeString(2))
        )

        val inactiveResult = scheduler.scheduleMedicineAlarms(inactiveMedicine)
        val asNeededResult = scheduler.scheduleMedicineAlarms(asNeededMedicine)

        assertTrue(inactiveResult.isEmpty())
        assertTrue(asNeededResult.isEmpty())
    }

    @Test
    fun `scheduleMedicineAlarms uses deterministic ids and creates alarm pending intents`() {
        val reminderTime = nextTimeString(2)
        val medicine = sampleMedicine(
            id = "med-deterministic",
            repeatType = RepeatType.DAILY,
            isActive = true,
            reminderTimes = listOf(reminderTime)
        )

        val firstIds = scheduler.scheduleMedicineAlarms(medicine)
        val secondIds = scheduler.scheduleMedicineAlarms(medicine)

        assertTrue(firstIds.isNotEmpty())
        assertEquals(firstIds, secondIds)

        firstIds.forEach { alarmId ->
            assertNotNull(getMedicineAlarmPendingIntent(alarmId))
        }

        scheduler.cancelMedicineAlarms(firstIds)
    }

    @Test
    fun `scheduleNextDailyAlarm creates pending intent for returned alarm id`() {
        val reminderTime = nextTimeString(3)

        val alarmId = scheduler.scheduleNextDailyAlarm(
            medicineName = "Vitamin C",
            medicineId = "med-next-daily",
            dosage = "1 tablet",
            reminderTime = reminderTime
        )

        assertTrue(alarmId > 0)
        assertNotNull(getMedicineAlarmPendingIntent(alarmId))

        scheduler.cancelMedicineAlarms(listOf(alarmId))
    }

    private fun getMedicineAlarmPendingIntent(alarmId: Int): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            alarmId,
            Intent(context, MedicineAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun sampleMedicine(
        id: String,
        repeatType: RepeatType,
        isActive: Boolean,
        reminderTimes: List<String>
    ): Medicine {
        return Medicine(
            id = id,
            userId = "user-1",
            name = "Vitamin C",
            dosage = "500",
            unit = "mg",
            reminderTimes = reminderTimes,
            repeatType = repeatType,
            isActive = isActive
        )
    }

    private fun nextTimeString(hoursAhead: Int): String {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, hoursAhead)
        }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }
}
