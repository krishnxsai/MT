package com.meditrack.app.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.meditrack.app.R
import com.meditrack.app.databinding.ItemAuditLogBinding
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuditLogAdapter :
    ListAdapter<Map<String, Any?>, AuditLogAdapter.LogViewHolder>(LogDiffCallback()) {

    private val dateFormat = SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemAuditLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LogViewHolder(
        private val binding: ItemAuditLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(log: Map<String, Any?>) {
            val context = binding.root.context
            val action = log["action"] as? String ?: "UNKNOWN"
            val userId = log["userId"] as? String ?: ""
            val targetId = log["targetId"] as? String
            val targetType = log["targetType"] as? String
            val success = log["success"] as? Boolean ?: true
            val errorMessage = log["errorMessage"] as? String
            @Suppress("UNCHECKED_CAST")
            val details = log["details"] as? Map<String, Any?>
            val timestamp = when (val ts = log["timestamp"]) {
                is Timestamp -> ts.toDate()
                is Date -> ts
                else -> null
            }

            // Action name
            binding.actionText.text = action.replace("_", " ")

            // User
            binding.userText.text = if (userId.length > 12) {
                "User: ${userId.take(12)}…"
            } else {
                "User: $userId"
            }

            // Target
            if (!targetId.isNullOrEmpty() || !targetType.isNullOrEmpty()) {
                binding.targetText.visibility = View.VISIBLE
                binding.targetText.text = buildString {
                    append("Target: ")
                    if (!targetType.isNullOrEmpty()) append("$targetType")
                    if (!targetId.isNullOrEmpty()) {
                        if (!targetType.isNullOrEmpty()) append(" / ")
                        append(if (targetId.length > 12) "${targetId.take(12)}…" else targetId)
                    }
                }
            } else {
                binding.targetText.visibility = View.GONE
            }

            // Details
            if (!details.isNullOrEmpty()) {
                binding.detailsText.visibility = View.VISIBLE
                binding.detailsText.text = details.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            } else {
                binding.detailsText.visibility = View.GONE
            }

            // Status chip
            if (success) {
                binding.statusChip.text = context.getString(R.string.audit_success)
                binding.statusChip.setChipBackgroundColorResource(R.color.success_container)
                binding.errorText.visibility = View.GONE
            } else {
                binding.statusChip.text = context.getString(R.string.audit_failed)
                binding.statusChip.setChipBackgroundColorResource(R.color.error_container)
                if (!errorMessage.isNullOrEmpty()) {
                    binding.errorText.visibility = View.VISIBLE
                    binding.errorText.text = errorMessage
                } else {
                    binding.errorText.visibility = View.GONE
                }
            }

            // Timestamp
            binding.timestampText.text = timestamp?.let { dateFormat.format(it) } ?: "—"
        }
    }

    class LogDiffCallback : DiffUtil.ItemCallback<Map<String, Any?>>() {
        override fun areItemsTheSame(
            oldItem: Map<String, Any?>,
            newItem: Map<String, Any?>
        ): Boolean = oldItem["id"] == newItem["id"]

        @Suppress("DiffUtilEquals")
        override fun areContentsTheSame(
            oldItem: Map<String, Any?>,
            newItem: Map<String, Any?>
        ): Boolean {
            if (oldItem.size != newItem.size) return false
            return oldItem.all { (key, value) -> newItem[key] == value }
        }
    }
}

