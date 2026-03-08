package com.example.meditrack.ui.order

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meditrack.R
import com.example.meditrack.data.model.Medicine
import com.google.android.material.button.MaterialButton

/**
 * Adapter for showing the patient's current medicines with a quick "Order" button.
 * Each row shows: medicine name, dosage, stock level, and an order button.
 */
class QuickOrderAdapter(
    private val onOrderClick: (Medicine) -> Unit
) : ListAdapter<Medicine, QuickOrderAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quick_order_medicine, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colorIndicator: View = itemView.findViewById(R.id.colorIndicator)
        private val medicineName: TextView = itemView.findViewById(R.id.medicineName)
        private val dosageText: TextView = itemView.findViewById(R.id.dosageText)
        private val stockDot: View = itemView.findViewById(R.id.stockDot)
        private val stockText: TextView = itemView.findViewById(R.id.stockText)
        private val orderButton: MaterialButton = itemView.findViewById(R.id.orderButton)

        fun bind(medicine: Medicine) {
            val context = itemView.context

            medicineName.text = medicine.name
            dosageText.text = "${medicine.dosage} ${medicine.unit}"

            // Color indicator
            val colorRes = getColorForMedicine(medicine.color)
            colorIndicator.backgroundTintList = ContextCompat.getColorStateList(context, colorRes)

            // Stock info
            if (medicine.isRefillTrackingEnabled) {
                stockDot.visibility = View.VISIBLE
                stockText.visibility = View.VISIBLE

                if (medicine.isOutOfStock) {
                    stockText.text = context.getString(R.string.out_of_stock_short)
                    stockText.setTextColor(ContextCompat.getColor(context, R.color.error))
                } else if (medicine.isLowStock) {
                    stockText.text = "${medicine.currentQuantity} left"
                    stockText.setTextColor(ContextCompat.getColor(context, R.color.warning))
                } else {
                    stockText.text = "${medicine.currentQuantity} left"
                    stockText.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                }

                // Make order button more prominent for low stock
                if (medicine.isLowStock || medicine.isOutOfStock) {
                    orderButton.text = context.getString(R.string.refill_now)
                    orderButton.setTextColor(ContextCompat.getColor(context, R.color.white))
                    orderButton.setBackgroundColor(ContextCompat.getColor(context, R.color.primary))
                    orderButton.iconTint = ContextCompat.getColorStateList(context, R.color.white)
                } else {
                    orderButton.text = context.getString(R.string.order)
                    orderButton.setTextColor(ContextCompat.getColor(context, R.color.primary))
                    orderButton.setBackgroundColor(0) // transparent
                    orderButton.iconTint = ContextCompat.getColorStateList(context, R.color.primary)
                }
            } else {
                stockDot.visibility = View.GONE
                stockText.visibility = View.GONE
                orderButton.text = context.getString(R.string.order)
                orderButton.setTextColor(ContextCompat.getColor(context, R.color.primary))
                orderButton.setBackgroundColor(0)
                orderButton.iconTint = ContextCompat.getColorStateList(context, R.color.primary)
            }

            orderButton.setOnClickListener { onOrderClick(medicine) }
            itemView.setOnClickListener { onOrderClick(medicine) }
        }

        private fun getColorForMedicine(color: String): Int {
            return when (color.lowercase()) {
                "red" -> R.color.medicine_red
                "orange" -> R.color.medicine_orange
                "green" -> R.color.medicine_green
                "blue" -> R.color.medicine_blue
                "purple" -> R.color.medicine_purple
                "teal" -> R.color.medicine_teal
                else -> R.color.primary
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Medicine>() {
        override fun areItemsTheSame(oldItem: Medicine, newItem: Medicine): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Medicine, newItem: Medicine): Boolean =
            oldItem == newItem
    }
}

