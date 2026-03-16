package com.meditrack.app.ui.order.history

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
import com.meditrack.app.data.model.OrderStatus
import com.meditrack.app.data.model.RefillOrder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying orders in the Order History screen.
 * Shows order summary with status badge and click-to-track action.
 */
class OrderHistoryAdapter(
    private val onOrderClick: (RefillOrder) -> Unit
) : ListAdapter<RefillOrder, OrderHistoryAdapter.OrderViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RefillOrder>() {
            override fun areItemsTheSame(oldItem: RefillOrder, newItem: RefillOrder): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: RefillOrder, newItem: RefillOrder): Boolean =
                oldItem == newItem
        }

        private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 2
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_history, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val statusIcon: ImageView = itemView.findViewById(R.id.statusIcon)
        private val orderTitleText: TextView = itemView.findViewById(R.id.orderTitleText)
        private val pharmacyText: TextView = itemView.findViewById(R.id.pharmacyText)
        private val statusBadge: TextView = itemView.findViewById(R.id.statusBadge)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val amountText: TextView = itemView.findViewById(R.id.amountText)

        fun bind(order: RefillOrder) {
            val ctx = itemView.context

            // Build order title (medicine names or item count)
            orderTitleText.text = getOrderTitle(order)

            // Pharmacy name
            pharmacyText.text = if (order.pharmacyName.isNotEmpty()) {
                order.pharmacyName
            } else {
                "—"
            }

            // Status icon and badge
            val status = order.status
            statusIcon.setImageResource(status.getStatusIcon())
            statusIcon.setColorFilter(ContextCompat.getColor(ctx, status.getStatusColor()))

            statusBadge.text = status.getDisplayLabel()
            val (bgColor, textColor) = getStatusColors(status)
            statusBadge.setBackgroundColor(ContextCompat.getColor(ctx, bgColor))
            statusBadge.setTextColor(ContextCompat.getColor(ctx, textColor))

            // Date
            dateText.text = order.createdAt?.let { dateFormat.format(it) } ?: "—"

            // Amount
            val amount = if (order.totalAmount > 0) {
                order.totalAmount
            } else {
                order.subtotal
            }
            amountText.text = if (amount > 0) {
                currencyFormat.format(amount)
            } else {
                "—"
            }

            // Click listener
            itemView.setOnClickListener { onOrderClick(order) }
        }

        private fun getOrderTitle(order: RefillOrder): String {
            return if (order.isMultiItemOrder) {
                // Multi-item order: show first item + count
                val firstItem = order.items.firstOrNull()?.medicineName ?: "Order"
                if (order.items.size > 1) {
                    "$firstItem +${order.items.size - 1} more"
                } else {
                    firstItem
                }
            } else {
                // Single item order
                if (order.medicineName.isNotEmpty()) {
                    buildString {
                        append(order.medicineName)
                        if (order.quantity > 0) {
                            append(" × ${order.quantity}")
                        }
                    }
                } else {
                    "Order #${order.id.takeLast(6)}"
                }
            }
        }

        private fun getStatusColors(status: OrderStatus): Pair<Int, Int> {
            return when (status) {
                OrderStatus.PENDING -> R.color.warning_container to R.color.warning
                OrderStatus.CONFIRMED -> R.color.info_container to R.color.info
                OrderStatus.PREPARING -> R.color.info_container to R.color.info
                OrderStatus.READY -> R.color.secondary_container to R.color.secondary
                OrderStatus.SHIPPED -> R.color.secondary_container to R.color.secondary
                OrderStatus.DELIVERED -> R.color.success_container to R.color.success
                OrderStatus.CANCELLED -> R.color.surface_variant to R.color.text_tertiary
            }
        }
    }
}
