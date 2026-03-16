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
 * Adapter for displaying pharmacies in the unified order screen.
 */
class PharmacyUnifiedAdapter(
    private val onSelectClick: (Pharmacy) -> Unit,
    private val onItemClick: (Pharmacy) -> Unit
) : ListAdapter<PharmacyUnifiedAdapter.PharmacyItem, PharmacyUnifiedAdapter.ViewHolder>(DiffCallback()) {

    data class PharmacyItem(
        val pharmacy: Pharmacy,
        val distanceText: String,
        val isSelected: Boolean
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pharmacy_unified, parent, false)
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
        private val tvAddress: TextView = itemView.findViewById(R.id.tvAddress)
        private val tvOpenStatus: TextView = itemView.findViewById(R.id.tvOpenStatus)
        private val tvClosingTime: TextView = itemView.findViewById(R.id.tvClosingTime)
        private val deliveryRow: View = itemView.findViewById(R.id.deliveryRow)
        private val tvDeliveryInfo: TextView = itemView.findViewById(R.id.tvDeliveryInfo)
        private val btnSelect: MaterialButton = itemView.findViewById(R.id.btnSelect)

        fun bind(item: PharmacyItem) {
            val context = itemView.context
            val pharmacy = item.pharmacy

            // Pharmacy info
            tvPharmacyName.text = pharmacy.name
            tvRating.text = String.format("%.1f", pharmacy.rating)
            tvDistance.text = item.distanceText
            tvAddress.text = "${pharmacy.address}, ${pharmacy.city}"

            // Open status (simplified - in real app, parse operating hours)
            tvOpenStatus.text = if (pharmacy.isActive) "Open" else "Closed"
            tvOpenStatus.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (pharmacy.isActive) R.color.success else R.color.error
                )
            )
            tvClosingTime.text = pharmacy.operatingHours.takeIf { it.isNotEmpty() } ?: ""

            // Delivery info
            if (pharmacy.isDeliveryAvailable) {
                deliveryRow.isVisible = true
                tvDeliveryInfo.text = "Delivery available ~ ${pharmacy.estimatedDeliveryTime.ifEmpty { "30 min" }}"
            } else {
                deliveryRow.isVisible = false
            }

            // Selection state
            if (item.isSelected) {
                ivSelected.isVisible = true
                card.strokeColor = ContextCompat.getColor(context, R.color.primary)
                card.strokeWidth = 2
                btnSelect.text = "Selected"
                btnSelect.setBackgroundColor(ContextCompat.getColor(context, R.color.primary))
                btnSelect.setTextColor(ContextCompat.getColor(context, R.color.white))
                btnSelect.strokeWidth = 0
            } else {
                ivSelected.isVisible = false
                card.strokeColor = ContextCompat.getColor(context, R.color.outline_light)
                card.strokeWidth = 1
                btnSelect.text = "Select"
                btnSelect.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
                btnSelect.setTextColor(ContextCompat.getColor(context, R.color.primary))
                btnSelect.strokeWidth = 1
                btnSelect.strokeColor = ContextCompat.getColorStateList(context, R.color.primary)
            }

            // Click listeners
            btnSelect.setOnClickListener { onSelectClick(pharmacy) }
            card.setOnClickListener { onItemClick(pharmacy) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<PharmacyItem>() {
        override fun areItemsTheSame(oldItem: PharmacyItem, newItem: PharmacyItem): Boolean {
            return oldItem.pharmacy.id == newItem.pharmacy.id
        }

        override fun areContentsTheSame(oldItem: PharmacyItem, newItem: PharmacyItem): Boolean {
            return oldItem == newItem
        }
    }
}
