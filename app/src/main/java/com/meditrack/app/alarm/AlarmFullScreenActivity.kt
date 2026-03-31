package com.meditrack.app.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.meditrack.app.databinding.ActivityAlarmFullscreenBinding
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen alarm activity that displays when a medicine reminder triggers.
 * This activity is shown over the lock screen and turns on the display.
 * The user must interact with "Take" or "Skip" to dismiss the alarm.
 */
@AndroidEntryPoint
class AlarmFullScreenActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
        const val EXTRA_MEDICINE_ID = "extra_medicine_id"
        const val EXTRA_DOSAGE = "extra_dosage"
        private const val AUTO_DISMISS_DELAY = 5 * 60 * 1000L // 5 minutes
    }

    private lateinit var binding: ActivityAlarmFullscreenBinding
    private var alarmId: Int = 0
    private var medicineName: String = ""
    private var medicineId: String = ""
    private var dosage: String = ""
    private var reminderTime: String = ""

    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable {
        // Auto-dismiss after timeout (safety measure)
        handleSkip()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup window flags to show over lock screen and turn on screen
        setupWindowFlags()

        binding = ActivityAlarmFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Extract data from intent
        alarmId = intent.getIntExtra(EXTRA_ALARM_ID, 0)
        medicineName = intent.getStringExtra(EXTRA_MEDICINE_NAME) ?: "Medicine"
        medicineId = intent.getStringExtra(EXTRA_MEDICINE_ID) ?: ""
        dosage = intent.getStringExtra(EXTRA_DOSAGE) ?: ""
        reminderTime = intent.getStringExtra(MedicineAlarmReceiver.EXTRA_REMINDER_TIME) ?: ""

        setupUI()
        setupClickListeners()
        setupBackPressedHandler()

        // Schedule auto-dismiss for safety
        autoDismissHandler.postDelayed(autoDismissRunnable, AUTO_DISMISS_DELAY)
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // Keep screen on while alarm is showing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupUI() {
        binding.medicineNameText.text = medicineName
        binding.dosageText.text = if (dosage.isNotEmpty()) {
            "Dosage: $dosage"
        } else {
            "Time to take your medicine!"
        }
        binding.alarmTimeText.text = "Time: ${getCurrentTimeFormatted()}"
    }

    private fun setupClickListeners() {
        binding.takeButton.setOnClickListener {
            handleTake()
        }

        binding.skipButton.setOnClickListener {
            handleSkip()
        }

        binding.snoozeButton.setOnClickListener {
            handleSnooze()
        }
    }

    private fun setupBackPressedHandler() {
        // Prevent back button from dismissing without action
        // User must choose Take, Skip, or Snooze
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - user must interact with buttons
            }
        })
    }

    private fun handleTake() {
        // Stop the alarm
        stopAlarm()

        // Send broadcast to mark as taken
        val takenIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_TAKEN
            putExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_ID, medicineId)
            putExtra(MedicineAlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(MedicineAlarmReceiver.EXTRA_REMINDER_TIME, reminderTime)
        }
        sendBroadcast(takenIntent)

        // Cancel notification
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(alarmId)

        finish()
    }

    private fun handleSkip() {
        // Stop the alarm
        stopAlarm()

        // Send broadcast to dismiss
        val dismissIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SKIP
            putExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_ID, medicineId)
            putExtra(MedicineAlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(MedicineAlarmReceiver.EXTRA_REMINDER_TIME, reminderTime)
        }
        sendBroadcast(dismissIntent)

        // Cancel notification
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(alarmId)

        finish()
    }

    private fun handleSnooze() {
        // Stop the alarm
        stopAlarm()

        // Send broadcast to snooze
        val snoozeIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            putExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_ID, medicineId)
            putExtra(MedicineAlarmReceiver.EXTRA_DOSAGE, dosage)
            putExtra(MedicineAlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(MedicineAlarmReceiver.EXTRA_REMINDER_TIME, reminderTime)
        }
        sendBroadcast(snoozeIntent)

        // Cancel notification
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(alarmId)

        finish()
    }

    private fun stopAlarm() {
        // Stop the foreground alarm service
        if (alarmId > 0) {
            AlarmService.stopAlarm(this, alarmId)
        } else {
            AlarmService.stopAlarm(this)
        }
    }

    private fun getCurrentTimeFormatted(): String {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val amPm = if (hour < 12) "AM" else "PM"
        val hour12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        return String.format(Locale.getDefault(), "%d:%02d %s", hour12, minute, amPm)
    }

    override fun onDestroy() {
        autoDismissHandler.removeCallbacks(autoDismissRunnable)
        super.onDestroy()
    }
}
