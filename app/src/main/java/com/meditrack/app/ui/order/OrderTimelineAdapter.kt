package com.meditrack.app.ui.order

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.meditrack.app.R
import com.meditrack.app.data.model.OrderStatus

/**
 * RecyclerView adapter for displaying order timeline steps.
 * Each step shows: icon, label, timestamp, and notes.
 */
class OrderTimelineAdapter :
    ListAdapter<OrderTimelineAdapter.TimelineStep, OrderTimelineAdapter.ViewHolder>(DiffCallback()) {

    data class TimelineStep(
        val status: OrderStatus,
        val displayLabel: String,
        val iconRes: Int,
        val timestamp: String,
        val note: String,
        val isCompleted: Boolean,
        val isCurrent: Boolean
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_timeline_step, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val step = getItem(position)
        val isLastStep = position == itemCount - 1
        holder.bind(step, isLastStep)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivStatusIcon)
        private val tvLabel: TextView = itemView.findViewById(R.id.tvStatusLabel)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvNote: TextView = itemView.findViewById(R.id.tvNote)
        private val lineConnector: View = itemView.findViewById(R.id.lineConnector)
        private val iconsContainer: LinearLayout = itemView.findViewById(R.id.iconsContainer)

        fun bind(step: TimelineStep, isLastStep: Boolean) {
            val context = itemView.context

            // Set icon and color based on completion status
            ivIcon.setImageResource(step.iconRes)
            ivIcon.setColorFilter(
                ContextCompat.getColor(context, step.status.getStatusColor()),
                android.graphics.PorterDuff.Mode.SRC_IN
            )

            // Set background color for icon circle
            val bgColor = if (step.isCompleted) {
                ContextCompat.getColor(context, R.color.primary)
            } else {
                ContextCompat.getColor(context, R.color.surface_variant)
            }
            iconsContainer.setBackgroundColor(bgColor)

            // Set label
            tvLabel.text = step.displayLabel
            tvLabel.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (step.isCompleted) R.color.text_primary else R.color.text_secondary
                )
            )

            // Set timestamp if available
            tvTimestamp.text = step.timestamp
            tvTimestamp.isVisible = step.timestamp.isNotEmpty()
            tvTimestamp.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))

            // Set note if available
            tvNote.text = step.note
            tvNote.isVisible = step.note.isNotEmpty()
            tvNote.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))

            // Hide line connector for last step
            lineConnector.isVisible = !isLastStep
            if (!isLastStep) {
                lineConnector.setBackgroundColor(
                    ContextCompat.getColor(
                        context,
                        if (step.isCompleted) R.color.primary else R.color.outline_light
                    )
                )
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TimelineStep>() {
        override fun areItemsTheSame(oldItem: TimelineStep, newItem: TimelineStep): Boolean {
            return oldItem.status == newItem.status
        }

        override fun areContentsTheSame(oldItem: TimelineStep, newItem: TimelineStep): Boolean {
            return oldItem == newItem
        }
    }
}
