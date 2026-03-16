package com.meditrack.app.ui.insights

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.meditrack.app.R
import com.meditrack.app.data.analytics.HealthInsightEngine
import com.meditrack.app.data.analytics.UnifiedAlertPrioritizer

class UnifiedAlertAdapter : ListAdapter<UnifiedAlertPrioritizer.UnifiedAlert, UnifiedAlertAdapter.AlertViewHolder>(AlertDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_risk_alert, parent, false)
        return AlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AlertViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.alertIcon)
        private val messageView: TextView = itemView.findViewById(R.id.alertMessage)
        private val severityIndicator: View = itemView.findViewById(R.id.severityIndicator)

        fun bind(alert: UnifiedAlertPrioritizer.UnifiedAlert) {
            messageView.text = "${alert.title}: ${alert.message}"

            val iconRes = when (alert.source) {
                UnifiedAlertPrioritizer.AlertSource.VITAL_READING -> R.drawable.ic_health
                UnifiedAlertPrioritizer.AlertSource.TREND_DETECTION -> R.drawable.ic_insights
                UnifiedAlertPrioritizer.AlertSource.RISK_SCORE -> R.drawable.ic_warning
                UnifiedAlertPrioritizer.AlertSource.INSIGHT_ENGINE -> {
                    when (alert.actionType) {
                        HealthInsightEngine.ActionType.MEDICATION -> R.drawable.ic_medicine
                        HealthInsightEngine.ActionType.DOCTOR_VISIT -> R.drawable.ic_doctor
                        HealthInsightEngine.ActionType.EMERGENCY -> R.drawable.ic_warning
                        else -> R.drawable.ic_health
                    }
                }
                UnifiedAlertPrioritizer.AlertSource.CORRELATION_RULE -> R.drawable.ic_warning
            }
            iconView.setImageResource(iconRes)

            val color = when (alert.priority) {
                UnifiedAlertPrioritizer.UnifiedPriority.EMERGENCY -> ContextCompat.getColor(itemView.context, R.color.status_missed)
                UnifiedAlertPrioritizer.UnifiedPriority.URGENT -> ContextCompat.getColor(itemView.context, R.color.medicine_red)
                UnifiedAlertPrioritizer.UnifiedPriority.HIGH -> ContextCompat.getColor(itemView.context, R.color.medicine_orange)
                UnifiedAlertPrioritizer.UnifiedPriority.MODERATE -> ContextCompat.getColor(itemView.context, R.color.medicine_yellow)
                UnifiedAlertPrioritizer.UnifiedPriority.LOW -> ContextCompat.getColor(itemView.context, R.color.medicine_teal)
                UnifiedAlertPrioritizer.UnifiedPriority.INFORMATIONAL -> ContextCompat.getColor(itemView.context, R.color.info)
            }

            iconView.setColorFilter(color)
            severityIndicator.setBackgroundColor(color)
        }
    }

    class AlertDiffCallback : DiffUtil.ItemCallback<UnifiedAlertPrioritizer.UnifiedAlert>() {
        override fun areItemsTheSame(
            oldItem: UnifiedAlertPrioritizer.UnifiedAlert,
            newItem: UnifiedAlertPrioritizer.UnifiedAlert
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: UnifiedAlertPrioritizer.UnifiedAlert,
            newItem: UnifiedAlertPrioritizer.UnifiedAlert
        ): Boolean {
            return oldItem == newItem
        }
    }
}
