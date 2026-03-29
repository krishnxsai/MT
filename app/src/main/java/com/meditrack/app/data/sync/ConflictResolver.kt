package com.meditrack.app.data.sync

import android.util.Log
import com.meditrack.app.data.local.entities.PendingAction
import com.meditrack.app.data.local.entities.PendingActionStatus
import java.util.Date

/**
 * Conflict resolution strategy for offline sync conflicts.
 *
 * **Scenario**: User edits medicine offline (e.g., changes quantity from 50 to 45).
 * Meanwhile, the cloud was updated by another device (quantity: 50 → 60).
 * When sync happens, we have:
 * - Local: quantity = 45, updatedAt = 10:00 AM
 * - Remote: quantity = 60, updatedAt = 9:55 AM
 *
 * **Strategy**: Last-Write-Wins (LWW)
 * - Compare timestamps: 10:00 AM > 9:55 AM
 * - Local update is newer → use local version (45)
 * - Remote update discarded
 *
 * **Rationale**:
 * - User expects their last action to win
 * - Deterministic (no user prompts)
 * - Prevents data loss from server-side updates
 * - Works well for append-only or timestamp-based updates
 */
sealed class SyncConflict {
    /**
     * No conflict - local and remote are compatible.
     */
    object NoConflict : SyncConflict()

    /**
     * Last-write-wins conflict resolution.
     * Most recent update wins based on timestamp.
     */
    data class LastWriteWins(
        val local: Map<String, Any?>,
        val remote: Map<String, Any?>,
        val useLocal: Boolean,  // true = use local version, false = use remote
        val reason: String
    ) : SyncConflict()

    /**
     * Unresolvable conflict - requires user intervention.
     * Not implemented in current version (LWW always decides).
     */
    data class RequiresUserResolve(
        val local: Map<String, Any?>,
        val remote: Map<String, Any?>,
        val description: String
    ) : SyncConflict()
}

/**
 * Conflict resolver for offline sync operations.
 * Implements last-write-wins strategy based on timestamps.
 */
class ConflictResolver {

    companion object {
        private const val TAG = "ConflictResolver"
        private const val TIMESTAMP_FIELD = "updatedAt"
    }

    /**
     * Detect and resolve conflict between local and remote data.
     *
     * @param resourceType Type of resource (MEDICINE, APPOINTMENT, HEALTHLOG, ORDER)
     * @param local Locally queued data (from PendingAction)
     * @param remote Data from Firestore
     * @return Conflict resolution strategy
     */
    fun resolve(
        resourceType: String,
        local: Map<String, Any?>,
        remote: Map<String, Any?>
    ): SyncConflict {
        return try {
            // Extract timestamps
            val localTimestamp = (local[TIMESTAMP_FIELD] as? Long) ?: System.currentTimeMillis()
            val remoteTimestamp = (remote[TIMESTAMP_FIELD] as? Long) ?: 0L

            // Compare timestamps
            return when {
                localTimestamp > remoteTimestamp -> {
                    // Local update is newer
                    Log.d(
                        TAG,
                        "Last-write-wins: Local update wins for $resourceType (local: $localTimestamp > remote: $remoteTimestamp)"
                    )
                    SyncConflict.LastWriteWins(
                        local = local,
                        remote = remote,
                        useLocal = true,
                        reason = "Local update is newer ($localTimestamp > $remoteTimestamp)"
                    )
                }
                remoteTimestamp > localTimestamp -> {
                    // Remote update is newer
                    Log.d(
                        TAG,
                        "Last-write-wins: Remote update wins for $resourceType (remote: $remoteTimestamp > local: $localTimestamp)"
                    )
                    SyncConflict.LastWriteWins(
                        local = local,
                        remote = remote,
                        useLocal = false,
                        reason = "Remote update is newer ($remoteTimestamp > $localTimestamp)"
                    )
                }
                else -> {
                    // Same timestamp - likely same update, no conflict
                    Log.d(TAG, "No conflict: Same timestamps for $resourceType")
                    SyncConflict.NoConflict
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving conflict for $resourceType", e)
            // Default: trust local on error
            SyncConflict.LastWriteWins(
                local = local,
                remote = remote,
                useLocal = true,
                reason = "Error during conflict resolution: ${e.message}"
            )
        }
    }

    /**
     * Merge local and remote data after conflict resolution.
     * Used to fill in fields that are only in remote (not in local pending action).
     *
     * Example: Order has [orderId, totalAmount] locally, but remote has additional fields
     * like [deliveryAddress, status]. After LWW decides to use local, we merge
     * non-conflicting remote fields back in.
     *
     * @param local Local queued data (winner from conflict resolution)
     * @param remote Remote data (loser, but contains other fields)
     * @return Merged data with local values + remote-only fields
     */
    fun mergeAfterConflict(
        local: Map<String, Any?>,
        remote: Map<String, Any?>
    ): Map<String, Any?> {
        return try {
            val merged = local.toMutableMap()

            // Add fields from remote that aren't in local
            remote.forEach { (key, value) ->
                if (!merged.containsKey(key)) {
                    merged[key] = value
                }
            }

            Log.d(TAG, "Merged ${remote.size} fields from remote into local")
            merged
        } catch (e: Exception) {
            Log.e(TAG, "Error merging after conflict", e)
            local  // Return local on error
        }
    }

    /**
     * Check if data has changed (for conflict detection).
     * Compares all fields except timestamps and metadata.
     *
     * @return true if any field (besides timestamps) differs
     */
    fun hasDataChanged(local: Map<String, Any?>, remote: Map<String, Any?>): Boolean {
        val ignoredFields = setOf(TIMESTAMP_FIELD, "createdAt", "syncedAt", "cachedAt")

        local.forEach { (key, localValue) ->
            if (key !in ignoredFields) {
                val remoteValue = remote[key]
                if (localValue != remoteValue) {
                    Log.d(TAG, "Data difference: $key = $localValue (local) vs $remoteValue (remote)")
                    return true
                }
            }
        }

        // Check for fields in remote not in local
        remote.forEach { (key, remoteValue) ->
            if (key !in ignoredFields && key !in local) {
                Log.d(TAG, "Remote has new field: $key = $remoteValue")
                return true
            }
        }

        return false
    }
}

/**
 * Extension function for PendingAction to resolve conflicts with remote data.
 * Usage:
 * ```
 * val conflictResolution = pendingAction.resolveConflict(remoteData, conflictResolver)
 * when (conflictResolution) {
 *     is SyncConflict.NoConflict -> applyToFirestore(pendingAction.data)
 *     is SyncConflict.LastWriteWins -> {
 *         val dataToSync = if (it.useLocal) pendingAction.data else it.remote
 *         applyToFirestore(dataToSync)
 *     }
 * }
 * ```
 */
fun PendingAction.resolveConflict(
    remoteData: Map<String, Any?>,
    resolver: ConflictResolver
): SyncConflict {
    val localData = try {
        org.json.JSONObject(this.dataJson).toMap()
    } catch (e: Exception) {
        Log.w("PendingAction", "Failed to parse dataJson for conflict resolution", e)
        emptyMap()
    }

    return resolver.resolve(this.resourceType, localData, remoteData)
}

/**
 * Helper extension to convert JSONObject to Map.
 */
private fun org.json.JSONObject.toMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    val keys = this.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = this.get(key)
        map[key] = when (value) {
            is org.json.JSONObject -> value.toMap()
            is org.json.JSONArray -> value.toList()
            org.json.JSONObject.NULL -> null
            else -> value
        }
    }
    return map
}

/**
 * Helper extension to convert JSONArray to List.
 */
private fun org.json.JSONArray.toList(): List<Any?> {
    val list = mutableListOf<Any?>()
    for (i in 0 until this.length()) {
        val value = this.get(i)
        list.add(when (value) {
            is org.json.JSONObject -> value.toMap()
            is org.json.JSONArray -> value.toList()
            org.json.JSONObject.NULL -> null
            else -> value
        })
    }
    return list
}
