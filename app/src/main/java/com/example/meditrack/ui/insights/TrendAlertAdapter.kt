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
import com.example.meditrack.data.analytics.TrendPredictionEngine

class TrendAlertAdapter :
    ListAdapter<TrendPredictionEngine.TrendAlert, TrendAlertAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trend_alert, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val directionIcon: ImageView = itemView.findViewById(R.id.trendDirectionIcon)
        private val vitalName: TextView = itemView.findViewById(R.id.trendVitalName)
        private val trendMessage: TextView = itemView.findViewById(R.id.trendMessage)
        private val trendValue: TextView = itemView.findViewById(R.id.trendCurrentValue)
        private val trendProjected: TextView = itemView.findViewById(R.id.trendProjectedValue)
        private val priorityIndicator: View = itemView.findViewById(R.id.trendPriorityIndicator)

        fun bind(alert: TrendPredictionEngine.TrendAlert) {
            vitalName.text = alert.vitalType.displayName
            trendMessage.text = alert.message

            // Current value
            trendValue.text = String.format("%.1f %s", alert.latestValue, alert.vitalType.unit)

            // Projected value
            trendProjected.text = String.format("→ %.1f %s", alert.projectedNextValue, alert.vitalType.unit)

            // Direction icon (using rotation on chevron)
            val isRising = alert.direction == TrendPredictionEngine.TrendDirection.RISING ||
                    alert.direction == TrendPredictionEngine.TrendDirection.RISING_FAST
            directionIcon.setImageResource(R.drawable.ic_chevron_right)
            directionIcon.rotation = if (isRising) -90f else 90f

            // Color coding
            val colorRes = when (alert.priority) {
                TrendPredictionEngine.TrendPriority.URGENT -> R.color.medicine_red
                TrendPredictionEngine.TrendPriority.HIGH -> R.color.medicine_orange
                TrendPredictionEngine.TrendPriority.MEDIUM -> R.color.warning
                TrendPredictionEngine.TrendPriority.LOW -> R.color.medicine_green
            }
            val color = ContextCompat.getColor(itemView.context, colorRes)
            directionIcon.setColorFilter(color)
            trendProjected.setTextColor(color)
            priorityIndicator.background?.setTint(color)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TrendPredictionEngine.TrendAlert>() {
        override fun areItemsTheSame(
            oldItem: TrendPredictionEngine.TrendAlert,
            newItem: TrendPredictionEngine.TrendAlert
        ): Boolean = oldItem.vitalType == newItem.vitalType

        override fun areContentsTheSame(
            oldItem: TrendPredictionEngine.TrendAlert,
            newItem: TrendPredictionEngine.TrendAlert
        ): Boolean = oldItem == newItem
    }
}

