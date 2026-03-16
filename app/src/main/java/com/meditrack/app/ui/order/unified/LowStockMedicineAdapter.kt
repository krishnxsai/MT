package com.meditrack.app.ui.order.unified

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.meditrack.app.R
import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.RefillUrgency

/**
 * Adapter for displaying low-stock medicines in a horizontal scrollable list.
 */
class LowStockMedicineAdapter(
    private val onAddClick: (Medicine) -> Unit,
    private val onItemClick: (Medicine) -> Unit
) : ListAdapter<LowStockMedicineAdapter.LowStockMedicineItem, LowStockMedicineAdapter.ViewHolder>(DiffCallback()) {

    data class LowStockMedicineItem(
        val medicine: Medicine,
        val urgency: RefillUrgency,
        val daysRemaining: Int,
        val stockPercentage: Int,
        val statusText: String,
        val isInCart: Boolean
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_low_stock_medicine, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.lowStockCard)
        private val tvUrgencyBadge: TextView = itemView.findViewById(R.id.tvUrgencyBadge)
        private val ivMedicine: ImageView = itemView.findViewById(R.id.ivMedicine)
        private val tvMedicineName: TextView = itemView.findViewById(R.id.tvMedicineName)
        private val tvDosage: TextView = itemView.findViewById(R.id.tvDosage)
        private val tvStockStatus: TextView = itemView.findViewById(R.id.tvStockStatus)
        private val progressStock: ProgressBar = itemView.findViewById(R.id.progressStock)
        private val btnAdd: MaterialButton = itemView.findViewById(R.id.btnAdd)
        private val addedIndicator: View = itemView.findViewById(R.id.addedIndicator)

        fun bind(item: LowStockMedicineItem) {
            val context = itemView.context
            val medicine = item.medicine

            // Medicine info
            tvMedicineName.text = medicine.name
            tvDosage.text = medicine.dosage

            // Stock status
            val stockText = when {
                item.urgency == RefillUrgency.OUT_OF_STOCK -> "Out of stock"
                medicine.currentQuantity <= 0 -> "Out of stock"
                else -> "${medicine.currentQuantity} left"
            }
            tvStockStatus.text = stockText

            // Progress bar
            progressStock.progress = item.stockPercentage.coerceIn(0, 100)

            // Urgency badge
            when (item.urgency) {
                RefillUrgency.OUT_OF_STOCK -> {
                    tvUrgencyBadge.isVisible = true
                    tvUrgencyBadge.text = "OUT"
                    tvUrgencyBadge.setBackgroundResource(R.drawable.bg_badge_error)
                    tvUrgencyBadge.setTextColor(ContextCompat.getColor(context, R.color.error))
                    tvStockStatus.setTextColor(ContextCompat.getColor(context, R.color.error))
                    card.strokeColor = ContextCompat.getColor(context, R.color.error)
                }
                RefillUrgency.URGENT -> {
                    tvUrgencyBadge.isVisible = true
                    tvUrgencyBadge.text = "URGENT"
                    tvUrgencyBadge.setBackgroundResource(R.drawable.bg_badge_warning)
                    tvUrgencyBadge.setTextColor(ContextCompat.getColor(context, R.color.warning))
                    tvStockStatus.setTextColor(ContextCompat.getColor(context, R.color.warning))
                    card.strokeColor = ContextCompat.getColor(context, R.color.warning)
                }
                RefillUrgency.LOW -> {
                    tvUrgencyBadge.isVisible = false
                    tvStockStatus.setTextColor(ContextCompat.getColor(context, R.color.warning))
                    card.strokeColor = ContextCompat.getColor(context, R.color.outline_light)
                }
                RefillUrgency.NONE -> {
                    tvUrgencyBadge.isVisible = false
                    tvStockStatus.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    card.strokeColor = ContextCompat.getColor(context, R.color.outline_light)
                }
            }

            // Cart state
            if (item.isInCart) {
                btnAdd.isVisible = false
                addedIndicator.isVisible = true
            } else {
                btnAdd.isVisible = true
                addedIndicator.isVisible = false
            }

            // Click listeners
            btnAdd.setOnClickListener { onAddClick(medicine) }
            card.setOnClickListener { onItemClick(medicine) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<LowStockMedicineItem>() {
        override fun areItemsTheSame(oldItem: LowStockMedicineItem, newItem: LowStockMedicineItem): Boolean {
            return oldItem.medicine.id == newItem.medicine.id
        }

        override fun areContentsTheSame(oldItem: LowStockMedicineItem, newItem: LowStockMedicineItem): Boolean {
            return oldItem == newItem
        }
    }
}
