package com.meditrack.app.ui.refill

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.meditrack.app.R
import com.meditrack.app.data.analytics.RefillDetectionEngine
import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.RefillUrgency

/**
 * Adapter for displaying refill alert cards in a RecyclerView.
 */
class RefillAlertAdapter(
    private val onOrderClick: (Medicine) -> Unit,
    private val onDismissClick: (String) -> Unit
) : ListAdapter<RefillAlertViewModel.MedicineWithAlert, RefillAlertAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_refill_alert, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.refillAlertCard)
        private val headerContainer: View = itemView.findViewById(R.id.headerContainer)
        private val ivAlertIcon: ImageView = itemView.findViewById(R.id.ivAlertIcon)
        private val tvAlertTitle: TextView = itemView.findViewById(R.id.tvAlertTitle)
        private val btnDismissHeader: ImageButton = itemView.findViewById(R.id.btnDismissHeader)
        private val ivMedicineIcon: ImageView = itemView.findViewById(R.id.ivMedicineIcon)
        private val tvMedicineName: TextView = itemView.findViewById(R.id.tvMedicineName)
        private val tvStockStatus: TextView = itemView.findViewById(R.id.tvStockStatus)
        private val progressStock: ProgressBar = itemView.findViewById(R.id.progressStock)
        private val tvStockPercent: TextView = itemView.findViewById(R.id.tvStockPercent)
        private val tvDaysRemaining: TextView = itemView.findViewById(R.id.tvDaysRemaining)
        private val btnDismiss: MaterialButton = itemView.findViewById(R.id.btnDismiss)
        private val btnOrderRefill: MaterialButton = itemView.findViewById(R.id.btnOrderRefill)

        fun bind(item: RefillAlertViewModel.MedicineWithAlert) {
            val context = itemView.context
            val medicine = item.medicine

            // Set medicine info
            tvMedicineName.text = "${medicine.name} ${medicine.dosage}".trim()
            tvStockStatus.text = item.statusText

            // Set progress
            val percentage = item.stockPercentage.coerceIn(0, 100)
            progressStock.progress = percentage
            tvStockPercent.text = "$percentage%"

            // Set days remaining
            tvDaysRemaining.text = when {
                item.daysRemaining <= 0 -> "Order immediately"
                item.daysRemaining == 1 -> "~1 day remaining"
                else -> "~${item.daysRemaining} days remaining"
            }

            // Style based on urgency
            styleByUrgency(item.urgency)

            // Set action button text
            btnOrderRefill.text = RefillDetectionEngine.getActionButtonText(item.urgency)

            // Click listeners
            btnOrderRefill.setOnClickListener { onOrderClick(medicine) }
            btnDismiss.setOnClickListener { onDismissClick(medicine.id) }
            btnDismissHeader.setOnClickListener { onDismissClick(medicine.id) }

            card.setOnClickListener { onOrderClick(medicine) }
        }

        private fun styleByUrgency(urgency: RefillUrgency) {
            val context = itemView.context

            when (urgency) {
                RefillUrgency.OUT_OF_STOCK -> {
                    // Red/urgent styling
                    tvAlertTitle.text = "URGENT — OUT OF STOCK"
                    tvAlertTitle.setTextColor(ContextCompat.getColor(context, R.color.error))
                    ivAlertIcon.setColorFilter(ContextCompat.getColor(context, R.color.error))
                    headerContainer.setBackgroundColor(ContextCompat.getColor(context, R.color.error_light))
                    card.strokeColor = ContextCompat.getColor(context, R.color.error)
                    tvStockPercent.setTextColor(ContextCompat.getColor(context, R.color.error))
                    btnOrderRefill.setBackgroundColor(ContextCompat.getColor(context, R.color.error))
                }
                RefillUrgency.URGENT -> {
                    // Orange/urgent styling
                    tvAlertTitle.text = "URGENT — LOW STOCK"
                    tvAlertTitle.setTextColor(ContextCompat.getColor(context, R.color.warning))
                    ivAlertIcon.setColorFilter(ContextCompat.getColor(context, R.color.warning))
                    headerContainer.setBackgroundColor(ContextCompat.getColor(context, R.color.warning_light))
                    card.strokeColor = ContextCompat.getColor(context, R.color.warning)
                    tvStockPercent.setTextColor(ContextCompat.getColor(context, R.color.warning))
                }
                RefillUrgency.LOW -> {
                    // Yellow/warning styling
                    tvAlertTitle.text = "LOW STOCK ALERT"
                    tvAlertTitle.setTextColor(ContextCompat.getColor(context, R.color.warning))
                    ivAlertIcon.setColorFilter(ContextCompat.getColor(context, R.color.warning))
                    headerContainer.setBackgroundColor(ContextCompat.getColor(context, R.color.warning_light))
                    card.strokeColor = ContextCompat.getColor(context, R.color.warning)
                    tvStockPercent.setTextColor(ContextCompat.getColor(context, R.color.warning))
                }
                RefillUrgency.NONE -> {
                    // Should not appear in this adapter, but handle gracefully
                    tvAlertTitle.text = "STOCK STATUS"
                    tvAlertTitle.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    ivAlertIcon.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary))
                    headerContainer.setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
                    card.strokeColor = ContextCompat.getColor(context, R.color.outline_light)
                    tvStockPercent.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<RefillAlertViewModel.MedicineWithAlert>() {
        override fun areItemsTheSame(
            oldItem: RefillAlertViewModel.MedicineWithAlert,
            newItem: RefillAlertViewModel.MedicineWithAlert
        ): Boolean {
            return oldItem.medicine.id == newItem.medicine.id
        }

        override fun areContentsTheSame(
            oldItem: RefillAlertViewModel.MedicineWithAlert,
            newItem: RefillAlertViewModel.MedicineWithAlert
        ): Boolean {
            return oldItem == newItem
        }
    }
}
