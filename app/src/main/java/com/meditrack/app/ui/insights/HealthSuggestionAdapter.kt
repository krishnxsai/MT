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

class HealthSuggestionAdapter :
    ListAdapter<HealthInsightEngine.HealthSuggestion, HealthSuggestionAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_health_suggestion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.suggestionIcon)
        private val iconBackground: View = itemView.findViewById(R.id.suggestionIconBg)
        private val titleView: TextView = itemView.findViewById(R.id.suggestionTitle)
        private val descriptionView: TextView = itemView.findViewById(R.id.suggestionDescription)
        private val priorityBadge: TextView = itemView.findViewById(R.id.suggestionPriorityBadge)
        private val actionTypeBadge: TextView = itemView.findViewById(R.id.suggestionActionType)

        fun bind(suggestion: HealthInsightEngine.HealthSuggestion) {
            titleView.text = suggestion.title
            descriptionView.text = suggestion.description

            // Priority badge
            val (priorityText, priorityColor) = when (suggestion.priority) {
                HealthInsightEngine.SuggestionPriority.URGENT -> "URGENT" to R.color.medicine_red
                HealthInsightEngine.SuggestionPriority.HIGH -> "HIGH" to R.color.medicine_orange
                HealthInsightEngine.SuggestionPriority.MEDIUM -> "MEDIUM" to R.color.warning
                HealthInsightEngine.SuggestionPriority.LOW -> "LOW" to R.color.medicine_green
            }
            priorityBadge.text = priorityText
            priorityBadge.setTextColor(ContextCompat.getColor(itemView.context, priorityColor))
            priorityBadge.background?.setTint(
                ContextCompat.getColor(itemView.context, when (suggestion.priority) {
                    HealthInsightEngine.SuggestionPriority.URGENT -> R.color.medicine_red_surface
                    HealthInsightEngine.SuggestionPriority.HIGH -> R.color.medicine_orange_surface
                    HealthInsightEngine.SuggestionPriority.MEDIUM -> R.color.warning_container
                    HealthInsightEngine.SuggestionPriority.LOW -> R.color.medicine_green_surface
                })
            )

            // Action type badge
            actionTypeBadge.text = when (suggestion.actionType) {
                HealthInsightEngine.ActionType.LIFESTYLE -> "Lifestyle"
                HealthInsightEngine.ActionType.MEDICATION -> "Medication"
                HealthInsightEngine.ActionType.DOCTOR_VISIT -> "Doctor Visit"
                HealthInsightEngine.ActionType.MONITORING -> "Monitoring"
                HealthInsightEngine.ActionType.EMERGENCY -> "Emergency"
            }

            // Icon based on hint
            val (iconRes, tintColor) = when (suggestion.iconHint) {
                "emergency" -> R.drawable.ic_warning to R.color.medicine_red
                "doctor" -> R.drawable.ic_doctor to R.color.medicine_blue
                "medication", "refill" -> R.drawable.ic_pill to R.color.medicine_purple
                "bp" -> R.drawable.ic_heart to R.color.medicine_red
                "heart" -> R.drawable.ic_heart to R.color.medicine_red
                "glucose" -> R.drawable.ic_chart to R.color.medicine_purple
                "temperature" -> R.drawable.ic_health to R.color.medicine_orange
                "weight" -> R.drawable.ic_chart to R.color.medicine_teal
                "monitoring", "add_data" -> R.drawable.ic_insights to R.color.info
                "warning" -> R.drawable.ic_warning to R.color.warning
                "success" -> R.drawable.ic_check_circle to R.color.medicine_green
                else -> R.drawable.ic_insights to R.color.primary
            }

            iconView.setImageResource(iconRes)
            iconView.setColorFilter(ContextCompat.getColor(itemView.context, tintColor))

            val bgTintColor = when (suggestion.iconHint) {
                "emergency" -> R.color.medicine_red_surface
                "doctor" -> R.color.medicine_blue_surface
                "medication", "refill" -> R.color.medicine_purple_surface
                "bp", "heart" -> R.color.medicine_red_surface
                "glucose" -> R.color.medicine_purple_surface
                "temperature" -> R.color.medicine_orange_surface
                "weight" -> R.color.medicine_teal_surface
                "warning" -> R.color.warning_container
                "success" -> R.color.medicine_green_surface
                else -> R.color.primary_container
            }
            iconBackground.background?.setTint(ContextCompat.getColor(itemView.context, bgTintColor))
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<HealthInsightEngine.HealthSuggestion>() {
        override fun areItemsTheSame(
            oldItem: HealthInsightEngine.HealthSuggestion,
            newItem: HealthInsightEngine.HealthSuggestion
        ): Boolean = oldItem.title == newItem.title

        override fun areContentsTheSame(
            oldItem: HealthInsightEngine.HealthSuggestion,
            newItem: HealthInsightEngine.HealthSuggestion
        ): Boolean = oldItem == newItem
    }
}

