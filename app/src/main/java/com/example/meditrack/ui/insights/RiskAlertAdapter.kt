package com.example.meditrack.ui.insights

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meditrack.R
import com.example.meditrack.data.analytics.HealthAnalytics

class RiskAlertAdapter : ListAdapter<HealthAnalytics.RiskAlert, RiskAlertAdapter.AlertViewHolder>(AlertDiffCallback()) {

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

        fun bind(alert: HealthAnalytics.RiskAlert) {
            messageView.text = alert.message

            // Set icon based on alert type
            val iconRes = when (alert.type) {
                HealthAnalytics.AlertType.HIGH_BLOOD_PRESSURE,
                HealthAnalytics.AlertType.LOW_BLOOD_PRESSURE -> R.drawable.ic_health
                HealthAnalytics.AlertType.HIGH_HEART_RATE,
                HealthAnalytics.AlertType.LOW_HEART_RATE -> R.drawable.ic_health
                HealthAnalytics.AlertType.HIGH_GLUCOSE,
                HealthAnalytics.AlertType.LOW_GLUCOSE -> R.drawable.ic_health
                HealthAnalytics.AlertType.ABNORMAL_TEMPERATURE -> R.drawable.ic_health
                HealthAnalytics.AlertType.MISSED_MEDICATIONS -> R.drawable.ic_medicine
            }
            iconView.setImageResource(iconRes)

            // Set colors based on severity
            val color = when (alert.severity) {
                HealthAnalytics.Severity.CRITICAL -> ContextCompat.getColor(itemView.context, R.color.status_missed)
                HealthAnalytics.Severity.HIGH -> ContextCompat.getColor(itemView.context, R.color.medicine_orange)
                HealthAnalytics.Severity.MEDIUM -> ContextCompat.getColor(itemView.context, R.color.medicine_yellow)
                HealthAnalytics.Severity.LOW -> ContextCompat.getColor(itemView.context, R.color.medicine_teal)
            }

            iconView.setColorFilter(color)
            severityIndicator.setBackgroundColor(color)
        }
    }

    class AlertDiffCallback : DiffUtil.ItemCallback<HealthAnalytics.RiskAlert>() {
        override fun areItemsTheSame(oldItem: HealthAnalytics.RiskAlert, newItem: HealthAnalytics.RiskAlert): Boolean {
            return oldItem.type == newItem.type && oldItem.message == newItem.message
        }

        override fun areContentsTheSame(oldItem: HealthAnalytics.RiskAlert, newItem: HealthAnalytics.RiskAlert): Boolean {
            return oldItem == newItem
        }
    }
}

