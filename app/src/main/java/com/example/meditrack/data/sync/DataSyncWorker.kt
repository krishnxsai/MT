package com.example.meditrack.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager Worker for syncing offline data with Firestore.
 * Runs periodically when network is available to ensure data consistency.
 */
class DataSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DataSyncWorker"
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

            // Create sync helper and process pending actions
            val syncHelper = SyncHelper(applicationContext)

            if (!syncHelper.hasPendingActions()) {
                Log.d(TAG, "No pending actions to sync")
                return@withContext Result.success()
            }

            val pendingCount = syncHelper.getPendingCount()
            Log.d(TAG, "Syncing $pendingCount pending actions")

            // Process all pending offline actions
            val result = syncHelper.syncPendingActions()

            if (result.failureCount > 0) {
                Log.w(TAG, "Sync completed with ${result.failureCount} failures, will retry")
                // Retry if there were failures
                return@withContext Result.retry()
            }

            Log.d(TAG, "Sync completed successfully: ${result.successCount} actions synced")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            // Retry on failure
            Result.retry()
        }
    }
}

