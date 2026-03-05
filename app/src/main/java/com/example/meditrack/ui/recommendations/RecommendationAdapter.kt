package com.example.meditrack.ui.recommendations

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
import com.example.meditrack.data.model.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying clinical decisions/recommendations to patients.
 * Shows type-specific information with priority indicators.
 */
class RecommendationAdapter(
    private val onItemClick: (ClinicalDecision) -> Unit,
    private val onAcknowledgeClick: (ClinicalDecision) -> Unit
) : ListAdapter<ClinicalDecision, RecommendationAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.recommendationCard)
        val priorityIndicator: View = view.findViewById(R.id.priorityIndicator)
        val typeIcon: ImageView = view.findViewById(R.id.typeIcon)
        val titleText: TextView = view.findViewById(R.id.titleText)
        val doctorText: TextView = view.findViewById(R.id.doctorText)
        val dateText: TextView = view.findViewById(R.id.dateText)
        val typeChip: Chip = view.findViewById(R.id.typeChip)
        val descriptionText: TextView = view.findViewById(R.id.descriptionText)
        val followUpDateText: TextView = view.findViewById(R.id.followUpDateText)
        val prescriptionCountText: TextView = view.findViewById(R.id.prescriptionCountText)
        val acknowledgeButton: MaterialButton = view.findViewById(R.id.acknowledgeButton)
        val acknowledgedBadge: View = view.findViewById(R.id.acknowledgedBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommendation_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val decision = getItem(position)
        val context = holder.itemView.context

        // Set priority indicator color
        val priorityColor = when (decision.priority) {
            Priority.LOW -> R.color.medicine_green
            Priority.NORMAL -> R.color.medicine_blue
            Priority.HIGH -> R.color.medicine_orange
            Priority.CRITICAL -> R.color.medicine_red
        }
        holder.priorityIndicator.setBackgroundColor(ContextCompat.getColor(context, priorityColor))

        // Set type icon
        val typeIcon = when (decision.type) {
            ClinicalDecisionType.PRESCRIPTION -> R.drawable.ic_medicine
            ClinicalDecisionType.FOLLOW_UP -> R.drawable.ic_calendar
            ClinicalDecisionType.VITAL_ALERT -> R.drawable.ic_warning
            ClinicalDecisionType.DIAGNOSIS -> R.drawable.ic_insights
            ClinicalDecisionType.LIFESTYLE -> R.drawable.ic_person
            else -> R.drawable.ic_info
        }
        holder.typeIcon.setImageResource(typeIcon)

        // Basic info
        holder.titleText.text = decision.title
        holder.doctorText.text = "Dr. ${decision.doctorName}"

        // Date
        decision.createdAt?.let {
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            holder.dateText.text = dateFormat.format(it)
        }

        // Type chip
        holder.typeChip.text = getTypeDisplayName(decision.type)

        // Description
        if (decision.description.isNotEmpty()) {
            holder.descriptionText.text = decision.description
            holder.descriptionText.visibility = View.VISIBLE
        } else {
            holder.descriptionText.visibility = View.GONE
        }

        // Follow-up date
        if (decision.type == ClinicalDecisionType.FOLLOW_UP && decision.followUpDate != null) {
            val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
            holder.followUpDateText.text = "📅 ${dateFormat.format(decision.followUpDate)}"
            holder.followUpDateText.visibility = View.VISIBLE
        } else {
            holder.followUpDateText.visibility = View.GONE
        }

        // Prescription count
        if (decision.type == ClinicalDecisionType.PRESCRIPTION && decision.prescriptions.isNotEmpty()) {
            holder.prescriptionCountText.text = "${decision.prescriptions.size} medication(s)"
            holder.prescriptionCountText.visibility = View.VISIBLE
        } else {
            holder.prescriptionCountText.visibility = View.GONE
        }

        // Acknowledge status
        if (decision.patientAcknowledged) {
            holder.acknowledgeButton.visibility = View.GONE
            holder.acknowledgedBadge.visibility = View.VISIBLE
        } else {
            holder.acknowledgeButton.visibility = View.VISIBLE
            holder.acknowledgedBadge.visibility = View.GONE
            holder.acknowledgeButton.setOnClickListener {
                onAcknowledgeClick(decision)
            }
        }

        // Card click
        holder.card.setOnClickListener {
            onItemClick(decision)
        }

        // Highlight critical items
        if (decision.priority == Priority.CRITICAL) {
            holder.card.strokeWidth = 2
            holder.card.strokeColor = ContextCompat.getColor(context, R.color.medicine_red)
        } else {
            holder.card.strokeWidth = 0
        }
    }

    private fun getTypeDisplayName(type: ClinicalDecisionType): String {
        return when (type) {
            ClinicalDecisionType.PRESCRIPTION -> "Prescription"
            ClinicalDecisionType.FOLLOW_UP -> "Follow-up"
            ClinicalDecisionType.VITAL_ALERT -> "Alert"
            ClinicalDecisionType.RECOMMENDATION -> "Advice"
            ClinicalDecisionType.DIAGNOSIS -> "Diagnosis"
            ClinicalDecisionType.LAB_ORDER -> "Lab Order"
            ClinicalDecisionType.LIFESTYLE -> "Lifestyle"
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ClinicalDecision>() {
        override fun areItemsTheSame(oldItem: ClinicalDecision, newItem: ClinicalDecision): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ClinicalDecision, newItem: ClinicalDecision): Boolean {
            return oldItem == newItem
        }
    }
}

