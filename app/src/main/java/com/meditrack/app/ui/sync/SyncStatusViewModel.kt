package com.meditrack.app.ui.sync

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.sync.SyncHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel for sync status UI feedback.
 *
 * **Responsibilities**:
 * - Monitor pending action count (reactive via Flow)
 * - Track last sync timestamp
 * - Expose current sync state (IDLE, SYNCING, ERROR)
 * - Provide manual sync trigger
 *
 * **UI Integration**:
 * - Observes pendingCount for badge display
 * - Exposes syncState for indicator
 * - Displays lastSyncTime formatted
 *
 * **Usage**:
 * ```kotlin
 * viewModel = SyncStatusViewModel(context)
 *
 * // Observe pending count
 * lifecycleScope.launch {
 *     viewModel.pendingCount.collect { count ->
 *         badgeView.setPendingCount(count)
 *     }
 * }
 *
 * // Observe sync state
 * lifecycleScope.launch {
 *     viewModel.syncState.collect { state ->
 *         indicatorView.setSyncState(state)
 *     }
 * }
 *
 * // Manual sync
 * viewModel.syncNow()
 * ```
 */
class SyncStatusViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = "SyncStatusViewModel"
        private const val PREFS_LAST_SYNC = "last_sync_timestamp"
    }

    private val syncHelper = SyncHelper(context)
    private val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    // ─────────────────────────────────────────────────────────────────────────────
    // STATE
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Pending actions count for badge display.
     * Hardcoded example - in real app, observe from PendingActionRepository
     */
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    /**
     * Current sync state for UI indicator.
     * IDLE → SYNCING → IDLE/ERROR
     */
    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /**
     * Last successful sync timestamp.
     * Formatted for display (e.g., "2 mins ago", "Yesterday at 3:45 PM").
     */
    private val _lastSyncTime = MutableStateFlow("Never")
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    /**
     * Error message when sync fails.
     * Empty when no error.
     */
    private val _syncError = MutableStateFlow("")
    val syncError: StateFlow<String> = _syncError.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────────────────────────────────────

    init {
        Log.d(TAG, "Initializing SyncStatusViewModel")
        updateLastSyncTime()
        updatePendingCount()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PUBLIC METHODS
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Trigger manual sync from UI.
     * Updates syncState to SYNCING during operation.
     */
    fun syncNow() {
        Log.d(TAG, "Manual sync triggered by user")
        viewModelScope.launch {
            _syncState.value = SyncState.SYNCING
            _syncError.value = ""

            try {
                val result = withContext(Dispatchers.IO) {
                    syncHelper.syncPendingActions()
                }

                Log.d(TAG, "Sync completed: ${result.successCount} success, ${result.failureCount} failures")

                if (result.failureCount > 0) {
                    _syncState.value = SyncState.ERROR
                    _syncError.value = "Sync failed: ${result.failureCount} actions"
                } else {
                    _syncState.value = SyncState.IDLE
                    updateLastSyncTime()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync error", e)
                _syncState.value = SyncState.ERROR
                _syncError.value = e.message ?: "Unknown error"
            }
        }
    }

    /**
     * Clear sync status (usually called after user dismisses error).
     */
    fun clearStatus() {
        Log.d(TAG, "Clearing sync status")
        _syncError.value = ""
        _syncState.value = SyncState.IDLE
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PRIVATE METHODS
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Update pending action count.
     * In production, this would observe from PendingActionRepository.
     */
    private fun updatePendingCount() {
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    syncHelper.getPendingCount()
                }
                Log.d(TAG, "Pending count updated: $count")
                _pendingCount.value = count
            } catch (e: Exception) {
                Log.w(TAG, "Error getting pending count", e)
            }
        }
    }

    /**
     * Update last sync time display.
     * Retrieves from SharedPreferences and formats for display.
     */
    private fun updateLastSyncTime() {
        viewModelScope.launch {
            try {
                val lastSyncMs = withContext(Dispatchers.IO) {
                    prefs.getLong(PREFS_LAST_SYNC, 0L)
                }

                val formatted = if (lastSyncMs == 0L) {
                    "Never"
                } else {
                    formatRelativeTime(lastSyncMs)
                }

                Log.d(TAG, "Last sync time: $formatted")
                _lastSyncTime.value = formatted

                // Update SharedPreferences with current time
                withContext(Dispatchers.IO) {
                    prefs.edit().putLong(PREFS_LAST_SYNC, System.currentTimeMillis()).apply()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error updating last sync time", e)
            }
        }
    }

    /**
     * Format timestamp to relative time (e.g., "5 mins ago", "Yesterday at 3:45 PM").
     */
    private fun formatRelativeTime(timestampMs: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = now - timestampMs

        return when {
            diffMs < 60_000 -> "Just now"
            diffMs < 3_600_000 -> {
                val minutes = diffMs / 60_000
                "$minutes min${if (minutes > 1) "s" else ""} ago"
            }
            diffMs < 86_400_000 -> {
                val hours = diffMs / 3_600_000
                "$hours hour${if (hours > 1) "s" else ""} ago"
            }
            diffMs < 604_800_000 -> {
                val days = diffMs / 86_400_000
                "$days day${if (days > 1) "s" else ""} ago"
            }
            else -> {
                val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                dateFormat.format(Date(timestampMs))
            }
        }
    }
}

/**
 * Sync state enum for UI indicator.
 * IDLE: No sync in progress
 * SYNCING: Currently syncing
 * ERROR: Last sync failed
 */
enum class SyncState {
    IDLE,
    SYNCING,
    ERROR
}
