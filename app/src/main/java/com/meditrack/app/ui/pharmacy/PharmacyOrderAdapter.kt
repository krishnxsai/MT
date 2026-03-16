package com.meditrack.app.ui.pharmacy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.meditrack.app.R
import com.meditrack.app.data.model.OrderStatus
import com.meditrack.app.data.model.RefillOrder
import com.meditrack.app.databinding.ItemPharmacyOrderBinding
import java.text.SimpleDateFormat
import java.util.Locale

class PharmacyOrderAdapter(
    private val onAccept: (RefillOrder) -> Unit,
    private val onReject: (RefillOrder) -> Unit
) : ListAdapter<RefillOrder, PharmacyOrderAdapter.OrderViewHolder>(OrderDiffCallback()) {

    private var patientNames: Map<String, String> = emptyMap()

    fun updatePatientNames(names: Map<String, String>) {
        patientNames = names
        notifyDataSetChanged()
    }

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

            // Show patient display name instead of truncated UID
            val displayName = patientNames[order.userId]
            binding.patientName.text = if (displayName != null) {
                binding.root.context.getString(R.string.patient_label, displayName)
            } else {
                "Patient: ${order.userId.take(8)}…"
            }

            // Show Rx badge if prescription-backed
            binding.rxBadge.visibility = if (order.prescriptionId.isNotEmpty()) View.VISIBLE else View.GONE

            binding.orderQuantity.text = "Quantity: ${order.quantity}"
            binding.orderDate.text = order.createdAt?.let { "Ordered: ${dateFormat.format(it)}" } ?: ""

            val context = binding.root.context
            when (order.status) {
                OrderStatus.PENDING -> {
                    binding.statusChip.text = context.getString(R.string.order_status_pending)
                    binding.statusChip.setChipBackgroundColorResource(R.color.warning_container)
                    binding.statusChip.setTextColor(context.getColor(R.color.on_warning_container))
                    binding.actionButtonsLayout.visibility = View.VISIBLE
                    binding.acceptButton.text = context.getString(R.string.accept_order)
                    binding.rejectButton.visibility = View.VISIBLE
                }
                OrderStatus.CONFIRMED -> {
                    binding.statusChip.text = context.getString(R.string.order_status_processing)
                    binding.statusChip.setChipBackgroundColorResource(R.color.confirmed_blue_container)
                    binding.statusChip.setTextColor(context.getColor(R.color.on_confirmed_blue_container))
                    binding.actionButtonsLayout.visibility = View.VISIBLE
                    binding.acceptButton.text = "Prepare"
                    binding.rejectButton.visibility = View.GONE
                }
                OrderStatus.PREPARING -> {
                    binding.statusChip.text = "Preparing"
                    binding.statusChip.setChipBackgroundColorResource(R.color.preparing_orange_container)
                    binding.statusChip.setTextColor(context.getColor(R.color.on_preparing_orange_container))
                    binding.actionButtonsLayout.visibility = View.VISIBLE
                    binding.acceptButton.text = context.getString(R.string.mark_ready)
                    binding.rejectButton.visibility = View.GONE
                }
                OrderStatus.READY -> {
                    binding.statusChip.text = "Ready"
                    binding.statusChip.setChipBackgroundColorResource(R.color.ready_purple_container)
                    binding.statusChip.setTextColor(context.getColor(R.color.on_ready_purple_container))
                    binding.actionButtonsLayout.visibility = View.VISIBLE
                    binding.acceptButton.text = context.getString(R.string.ship_order)
                    binding.rejectButton.visibility = View.GONE
                }
                OrderStatus.SHIPPED -> {
                    binding.statusChip.text = context.getString(R.string.order_status_ready)
                    binding.statusChip.setChipBackgroundColorResource(R.color.ready_purple_container)
                    binding.statusChip.setTextColor(context.getColor(R.color.on_ready_purple_container))
                    binding.actionButtonsLayout.visibility = View.VISIBLE
                    binding.acceptButton.text = context.getString(R.string.mark_completed)
                    binding.rejectButton.visibility = View.GONE
                }
                OrderStatus.DELIVERED -> {
                    binding.statusChip.text = context.getString(R.string.order_status_completed)
                    binding.statusChip.setChipBackgroundColorResource(R.color.success_container)
                    binding.statusChip.setTextColor(context.getColor(R.color.on_success_container))
                    binding.actionButtonsLayout.visibility = View.GONE
                }
                OrderStatus.CANCELLED -> {
                    binding.statusChip.text = "Cancelled"
                    binding.statusChip.setChipBackgroundColorResource(R.color.error_container)
                    binding.statusChip.setTextColor(context.getColor(R.color.on_error_container))
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

