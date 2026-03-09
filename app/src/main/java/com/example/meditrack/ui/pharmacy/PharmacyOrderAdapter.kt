package com.example.meditrack.ui.pharmacy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meditrack.R
import com.example.meditrack.data.model.OrderStatus
import com.example.meditrack.data.model.RefillOrder
import com.example.meditrack.databinding.ItemPharmacyOrderBinding
import java.text.SimpleDateFormat
import java.util.Locale

class PharmacyOrderAdapter(
    private val onAccept: (RefillOrder) -> Unit,
    private val onReject: (RefillOrder) -> Unit
) : ListAdapter<RefillOrder, PharmacyOrderAdapter.OrderViewHolder>(OrderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemPharmacyOrderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OrderViewHolder(
        private val binding: ItemPharmacyOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        fun bind(order: RefillOrder) {
            binding.medicineName.text = buildString {
                append(order.medicineName)
                if (order.medicineDosage.isNotEmpty()) {
                    append(" ${order.medicineDosage}")
                    if (order.medicineUnit.isNotEmpty()) append(order.medicineUnit)
                }
            }
            binding.patientName.text = "Patient ID: ${order.userId.take(8)}…"
            binding.orderQuantity.text = "Quantity: ${order.quantity}"
            binding.orderDate.text = order.createdAt?.let { "Ordered: ${dateFormat.format(it)}" } ?: ""

            val context = binding.root.context
            when (order.status) {
                OrderStatus.PENDING -> {
                    binding.statusChip.text = context.getString(R.string.order_status_pending)
                    binding.statusChip.setChipBackgroundColorResource(R.color.warning_container)
                    binding.actionButtonsLayout.visibility = View.VISIBLE
                    binding.acceptButton.text = context.getString(R.string.accept_order)
                }
                OrderStatus.CONFIRMED -> {
                    binding.statusChip.text = context.getString(R.string.order_status_processing)
                    binding.statusChip.setChipBackgroundColorResource(R.color.primary_container)
                    binding.actionButtonsLayout.visibility = View.VISIBLE
                    binding.acceptButton.text = "Prepare"
                    binding.rejectButton.visibility = View.GONE
                }
                OrderStatus.PREPARING -> {
                    binding.statusChip.text = "Preparing"
                    binding.statusChip.setChipBackgroundColorResource(R.color.primary_container)
                    binding.actionButtonsLayout.visibility = View.VISIBLE
                    binding.acceptButton.text = context.getString(R.string.mark_ready)
                    binding.rejectButton.visibility = View.GONE
                }
                OrderStatus.SHIPPED -> {
                    binding.statusChip.text = context.getString(R.string.order_status_ready)
                    binding.statusChip.setChipBackgroundColorResource(R.color.success_container)
                    binding.actionButtonsLayout.visibility = View.VISIBLE
                    binding.acceptButton.text = context.getString(R.string.mark_completed)
                    binding.rejectButton.visibility = View.GONE
                }
                OrderStatus.DELIVERED -> {
                    binding.statusChip.text = context.getString(R.string.order_status_completed)
                    binding.statusChip.setChipBackgroundColorResource(R.color.success_container)
                    binding.actionButtonsLayout.visibility = View.GONE
                }
                OrderStatus.CANCELLED -> {
                    binding.statusChip.text = "Cancelled"
                    binding.statusChip.setChipBackgroundColorResource(R.color.error_container)
                    binding.actionButtonsLayout.visibility = View.GONE
                }
            }

            binding.acceptButton.setOnClickListener { onAccept(order) }
            binding.rejectButton.setOnClickListener { onReject(order) }
        }
    }

    class OrderDiffCallback : DiffUtil.ItemCallback<RefillOrder>() {
        override fun areItemsTheSame(oldItem: RefillOrder, newItem: RefillOrder) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: RefillOrder, newItem: RefillOrder) = oldItem == newItem
    }
}

