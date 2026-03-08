package com.example.meditrack.data.sync

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.meditrack.R
import com.example.meditrack.data.analytics.PredictiveAlertManager
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.repository.HealthLogRepository
import com.example.meditrack.data.repository.MedicineIntakeRepository
import com.example.meditrack.data.repository.MedicineRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager Worker for syncing offline data with Firestore
 * and running predictive health alerts.
 */
class DataSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DataSyncWorker"
        private const val NOTIFICATION_CHANNEL_GENERAL = "meditrack_general"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting data sync work")

        try {
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser

            if (currentUser == null) {
                Log.d(TAG, "No user logged in, skipping sync")
                return@withContext Result.success()
            }

            // ── Offline sync ──
            val syncHelper = SyncHelper(applicationContext)

            if (syncHelper.hasPendingActions()) {
                val pendingCount = syncHelper.getPendingCount()
                Log.d(TAG, "Syncing $pendingCount pending actions")

                val result = syncHelper.syncPendingActions()

                if (result.failureCount > 0) {
                    Log.w(TAG, "Sync completed with ${result.failureCount} failures, will retry")
                    return@withContext Result.retry()
                }

                Log.d(TAG, "Sync completed successfully: ${result.successCount} actions synced")
            } else {
                Log.d(TAG, "No pending actions to sync")
            }

            // ── Predictive Health Alerts ──
            runPredictiveAlerts()

            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            Result.retry()
        }
    }

    /**
     * Run the predictive alert engine and post notifications for new alerts.
     */
    private suspend fun runPredictiveAlerts() {
        try {
            val healthLogRepo = HealthLogRepository()
            val medicineRepo = MedicineRepository()
            val intakeRepo = MedicineIntakeRepository()
            val alertManager = PredictiveAlertManager(applicationContext)

            val logsResult = healthLogRepo.getHealthLogs()
            val medsResult = medicineRepo.getMedicines()

            if (logsResult is Resource.Success && medsResult is Resource.Success) {
                val adherence = intakeRepo.getAdherencePercentage(7)

                val newAlerts = alertManager.evaluateAndGetNewAlerts(
                    logs = logsResult.data,
                    medicines = medsResult.data,
                    adherencePercentage = adherence
                )

                for (alert in newAlerts) {
                    postAlertNotification(alert)
                    alertManager.markAlertFired(alert.id)
                }

                Log.d(TAG, "Predictive alerts: ${newAlerts.size} new alerts posted")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Predictive alerts failed (non-critical): ${e.message}")
        }
    }

    private fun postAlertNotification(alert: PredictiveAlertManager.PredictiveAlert) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val priority = when (alert.severity) {
            PredictiveAlertManager.AlertLevel.CRITICAL -> NotificationCompat.PRIORITY_HIGH
            PredictiveAlertManager.AlertLevel.WARNING -> NotificationCompat.PRIORITY_DEFAULT
            PredictiveAlertManager.AlertLevel.INFO -> NotificationCompat.PRIORITY_LOW
        }

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_health)
            .setContentTitle(alert.title)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setPriority(priority)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(alert.id.hashCode(), notification)
    }
}

