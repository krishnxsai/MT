package com.meditrack.app.ui.order

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.meditrack.app.R
import com.meditrack.app.data.model.OrderItem
import java.text.NumberFormat
import java.util.Locale

/**
 * Adapter for displaying order items on the Order Tracking screen.
 */
class OrderItemSummaryAdapter : RecyclerView.Adapter<OrderItemSummaryAdapter.ViewHolder>() {

    private var items: List<OrderItem> = emptyList()

    fun submitList(newItems: List<OrderItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_item_summary, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvMedicineName)
        private val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)

        private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build())

        fun bind(item: OrderItem) {
            tvName.text = buildString {
                append(item.medicineName)
                if (item.medicineDosage.isNotEmpty()) {
                    append(" ${item.medicineDosage}")
                }
            }
            tvQuantity.text = "Qty: ${item.quantity}"
            tvPrice.text = if (item.totalPrice > 0) {
                currencyFormat.format(item.totalPrice)
            } else {
                "—"
            }
        }
    }
}
