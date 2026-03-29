package com.meditrack.app.ui.order.unified

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.meditrack.app.R
import com.meditrack.app.data.model.Pharmacy

/**
 * Adapter for displaying pharmacies in the map screen's bottom sheet.
 * Compact card design optimized for list viewing.
 */
class PharmacyMapListAdapter(
    private val onPharmacyClick: (Pharmacy) -> Unit,
    private val onSelectClick: (Pharmacy) -> Unit,
    private val onCallClick: (Pharmacy) -> Unit
) : ListAdapter<PharmacyMapListAdapter.PharmacyListItem, PharmacyMapListAdapter.ViewHolder>(DiffCallback()) {

    data class PharmacyListItem(
        val pharmacy: Pharmacy,
        val distanceText: String,
        val isSelected: Boolean
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pharmacy_map_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.pharmacyCard)
        private val ivPharmacy: ImageView = itemView.findViewById(R.id.ivPharmacy)
        private val ivSelected: ImageView = itemView.findViewById(R.id.ivSelected)
        private val tvPharmacyName: TextView = itemView.findViewById(R.id.tvPharmacyName)
        private val tvRating: TextView = itemView.findViewById(R.id.tvRating)
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        private val tvDeliveryTime: TextView = itemView.findViewById(R.id.tvDeliveryTime)
        private val tvOpenStatus: TextView = itemView.findViewById(R.id.tvOpenStatus)
        private val btnCall: ImageView = itemView.findViewById(R.id.btnCall)
        private val btnSelect: MaterialButton = itemView.findViewById(R.id.btnSelect)

        fun bind(item: PharmacyListItem) {
            val context = itemView.context
            val pharmacy = item.pharmacy

            // Pharmacy info
            tvPharmacyName.text = pharmacy.name
            tvRating.text = String.format("%.1f", pharmacy.rating)
            tvDistance.text = item.distanceText
            tvDeliveryTime.text = pharmacy.estimatedDeliveryTime.ifEmpty { "30 min" }

            // Open status
            tvOpenStatus.text = if (pharmacy.isActive) "Open" else "Closed"
            tvOpenStatus.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (pharmacy.isActive) R.color.success else R.color.error
                )
            )

            // Selection state
            if (item.isSelected) {
                ivSelected.isVisible = true
                card.strokeColor = ContextCompat.getColor(context, R.color.primary)
                card.strokeWidth = 2
                card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.primary_light))
                btnSelect.text = "Selected"
                btnSelect.isEnabled = false
            } else {
                ivSelected.isVisible = false
                card.strokeColor = ContextCompat.getColor(context, R.color.outline_light)
                card.strokeWidth = 1
                card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_background))
                btnSelect.text = "Select"
                btnSelect.isEnabled = true
            }

            // Click listeners
            card.setOnClickListener { onPharmacyClick(pharmacy) }
            btnSelect.setOnClickListener { onSelectClick(pharmacy) }
            btnCall.setOnClickListener { onCallClick(pharmacy) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<PharmacyListItem>() {
        override fun areItemsTheSame(oldItem: PharmacyListItem, newItem: PharmacyListItem): Boolean {
            return oldItem.pharmacy.id == newItem.pharmacy.id
        }

        override fun areContentsTheSame(oldItem: PharmacyListItem, newItem: PharmacyListItem): Boolean {
            return oldItem == newItem
        }
    }
}
