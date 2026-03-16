package com.meditrack.app.ui.order

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.meditrack.app.data.model.OrderItem
import com.meditrack.app.databinding.ItemOrderItemSummaryBinding

/**
 * RecyclerView adapter for displaying order items in the order tracking screen.
 */
class OrderItemsAdapter : ListAdapter<OrderItem, OrderItemsAdapter.ViewHolder>(ItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOrderItemSummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemOrderItemSummaryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OrderItem) {
            with(binding) {
                tvMedicineName.text = item.medicineName
                tvQuantity.text = "Qty: ${item.quantity} × ${item.medicineDosage}"
                tvPrice.text = String.format("₹%.2f", item.totalPrice)
            }
        }
    }

    class ItemDiffCallback : DiffUtil.ItemCallback<OrderItem>() {
        override fun areItemsTheSame(oldItem: OrderItem, newItem: OrderItem) =
            oldItem.medicineId == newItem.medicineId

        override fun areContentsTheSame(oldItem: OrderItem, newItem: OrderItem) =
            oldItem == newItem
    }
}
