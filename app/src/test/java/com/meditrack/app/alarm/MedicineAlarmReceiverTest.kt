package com.meditrack.app.alarm

import android.content.Context
import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MedicineAlarmReceiverTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val receiver = MedicineAlarmReceiver()

    @Test
    fun `onReceive ignores boot completed broadcasts`() {
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        receiver.onReceive(context, intent)

        assertFalse(AlarmService.isRunning())
    }

    @Test
    fun `onReceive ignores invalid alarm id`() {
        val intent = Intent(context, MedicineAlarmReceiver::class.java).apply {
            putExtra(MedicineAlarmReceiver.EXTRA_ALARM_ID, -1)
            putExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_NAME, "Vitamin C")
        }

        receiver.onReceive(context, intent)

        assertFalse(AlarmService.isRunning())
    }

    @Test
    fun `onReceive ignores missing medicine name`() {
        val intent = Intent(context, MedicineAlarmReceiver::class.java).apply {
            putExtra(MedicineAlarmReceiver.EXTRA_ALARM_ID, 12345)
        }

        receiver.onReceive(context, intent)

        assertFalse(AlarmService.isRunning())
    }
}
