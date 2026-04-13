package com.meditrack.app.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.meditrack.app.R
import com.meditrack.app.alarm.AlarmPermissionHelper
import com.meditrack.app.data.model.NotificationType
import com.meditrack.app.databinding.ActivityNotificationSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Notification settings screen.
 * Allows users to control notification types and quiet hours (do not disturb).
 */
@AndroidEntryPoint
class NotificationSettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "NotificationSettings"
    }

    private lateinit var binding: ActivityNotificationSettingsBinding
    private val viewModel: NotificationSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Notification Settings"
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupClickListeners() {
        // Notification type toggles
        binding.toggleAppointments.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleNotificationType(NotificationType.APPOINTMENT, isChecked)
        }

        binding.togglePrescriptions.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleNotificationType(NotificationType.PRESCRIPTION, isChecked)
        }

        binding.toggleOrderUpdates.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleNotificationType(NotificationType.ORDER_STATUS, isChecked)
        }

        binding.togglePromotions.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleNotificationType(NotificationType.PROMOTIONAL, isChecked)
        }

        binding.toggleHealthAlerts.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleNotificationType(NotificationType.HEALTH_ALERT, isChecked)
        }

        // Quiet hours
        binding.toggleQuietHours.setOnCheckedChangeListener { _, isChecked ->
            binding.quietHoursContainer.isVisible = isChecked
            if (isChecked) {
                // Save quiet hours settings
                val startTime = binding.tvQuietHourStart.text.toString()
                val endTime = binding.tvQuietHourEnd.text.toString()
                viewModel.updateQuietHours(isChecked, startTime, endTime)
            } else {
                // Disable quiet hours
                viewModel.updateQuietHours(false, "", "")
            }
        }

        binding.btnSetQuietHourStart.setOnClickListener {
            showTimePickerForStart()
        }

        binding.btnSetQuietHourEnd.setOnClickListener {
            showTimePickerForEnd()
        }

        binding.btnManagePopupAlerts.setOnClickListener {
            try {
                startActivity(AlarmPermissionHelper.createFullScreenSettingsIntent(this))
            } catch (_: Exception) {
                Toast.makeText(this, "Unable to open popup alert settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.preferences.observe(this) { prefs ->
            if (prefs != null) {
                updateUI(prefs)
            }
        }

        viewModel.loading.observe(this) { isLoading ->
            binding.loadingProgressBar.isVisible = isLoading
        }

        viewModel.errorMessage.observe(this) { message ->
            if (!message.isNullOrEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }

        viewModel.successMessage.observe(this) { message ->
            if (!message.isNullOrEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearSuccess()
            }
        }
    }

    /**
     * Update UI with current preferences.
     */
    private fun updateUI(prefs: com.meditrack.app.data.model.NotificationPreference) {
        // Set notification type toggles
        binding.toggleAppointments.isChecked = prefs.appointmentReminders
        binding.togglePrescriptions.isChecked = prefs.prescriptionNotifications
        binding.toggleOrderUpdates.isChecked = prefs.orderStatusUpdates
        binding.togglePromotions.isChecked = prefs.promotionalMessages
        binding.toggleHealthAlerts.isChecked = prefs.healthAlerts

        // Set quiet hours
        binding.toggleQuietHours.isChecked = prefs.quietHoursEnabled
        binding.quietHoursContainer.isVisible = prefs.quietHoursEnabled

        binding.tvQuietHourStart.text = prefs.quietHourStart
        binding.tvQuietHourEnd.text = prefs.quietHourEnd

        // Update description text
        binding.tvQuietHoursDesc.text = if (prefs.quietHoursEnabled) {
            "No notifications between ${prefs.quietHourStart} and ${prefs.quietHourEnd}"
        } else {
            "Quiet hours disabled"
        }

        updateCriticalAlertStatus()
    }

    override fun onResume() {
        super.onResume()
        updateCriticalAlertStatus()
    }

    private fun updateCriticalAlertStatus() {
        val notificationsEnabled = AlarmPermissionHelper.hasNotificationPermission(this)
        val popupEnabled = AlarmPermissionHelper.canUseFullScreenIntent(this)

        binding.tvFullScreenAlertStatus.text = when {
            notificationsEnabled && popupEnabled -> {
                "Notifications and popup alarms are enabled"
            }

            !notificationsEnabled && !popupEnabled -> {
                "Notifications and popup alarms are disabled"
            }

            !notificationsEnabled -> {
                "Notifications are disabled; popup alarms may not appear"
            }

            else -> {
                "Popup alarms are disabled; reminders will use heads-up notifications"
            }
        }
    }

    /**
     * Show time picker for quiet hour start time.
     */
    private fun showTimePickerForStart() {
        val currentTime = binding.tvQuietHourStart.text.toString()
        val (hour, minute) = parseTime(currentTime)

        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(hour)
            .setMinute(minute)
            .build()

        timePicker.addOnPositiveButtonClickListener {
            val startTime = String.format("%02d:%02d", timePicker.hour, timePicker.minute)
            binding.tvQuietHourStart.text = startTime

            // Save the new quiet hours
            val endTime = binding.tvQuietHourEnd.text.toString()
            viewModel.updateQuietHours(binding.toggleQuietHours.isChecked, startTime, endTime)
        }

        timePicker.show(supportFragmentManager, "start_time_picker")
    }

    /**
     * Show time picker for quiet hour end time.
     */
    private fun showTimePickerForEnd() {
        val currentTime = binding.tvQuietHourEnd.text.toString()
        val (hour, minute) = parseTime(currentTime)

        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(hour)
            .setMinute(minute)
            .build()

        timePicker.addOnPositiveButtonClickListener {
            val endTime = String.format("%02d:%02d", timePicker.hour, timePicker.minute)
            binding.tvQuietHourEnd.text = endTime

            // Save the new quiet hours
            val startTime = binding.tvQuietHourStart.text.toString()
            viewModel.updateQuietHours(binding.toggleQuietHours.isChecked, startTime, endTime)
        }

        timePicker.show(supportFragmentManager, "end_time_picker")
    }

    /**
     * Parse time string "HH:mm" into hour and minute.
     */
    private fun parseTime(timeString: String): Pair<Int, Int> {
        return try {
            val parts = timeString.split(":")
            Pair(parts[0].toInt(), parts[1].toInt())
        } catch (e: Exception) {
            Pair(22, 0)  // Default to 22:00
        }
    }
}
