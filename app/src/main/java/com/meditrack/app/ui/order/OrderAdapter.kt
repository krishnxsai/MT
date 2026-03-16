package com.meditrack.app.ui.order

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.meditrack.app.R
import com.meditrack.app.data.model.OrderStatus
import com.meditrack.app.data.model.RefillOrder
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class OrderAdapter(
    private val onCancel: ((RefillOrder) -> Unit)? = null,
    private val onMarkDelivered: ((RefillOrder) -> Unit)? = null,
    private val onReturn: ((RefillOrder) -> Unit)? = null,
    private val onTrack: ((RefillOrder) -> Unit)? = null
) : ListAdapter<RefillOrder, OrderAdapter.OrderViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RefillOrder>() {
            override fun areItemsTheSame(a: RefillOrder, b: RefillOrder) = a.id == b.id
            override fun areContentsTheSame(a: RefillOrder, b: RefillOrder) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder =
        OrderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_refill_order, parent, false))

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OrderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val medicineNameText: TextView = view.findViewById(R.id.medicineNameText)
        private val dosageText: TextView = view.findViewById(R.id.dosageText)
        private val statusBadge: TextView = view.findViewById(R.id.statusBadge)
        private val dateText: TextView = view.findViewById(R.id.dateText)
        private val quantityText: TextView = view.findViewById(R.id.quantityText)
        private val notesText: TextView = view.findViewById(R.id.notesText)
        private val actionsContainer: View = view.findViewById(R.id.actionsContainer)
        private val cancelButton: MaterialButton = view.findViewById(R.id.cancelButton)
        private val trackButton: MaterialButton = view.findViewById(R.id.trackButton)
        private val deliveredButton: MaterialButton = view.findViewById(R.id.deliveredButton)
        private val returnButton: MaterialButton = view.findViewById(R.id.returnButton)

        fun bind(order: RefillOrder) {
            val ctx = itemView.context
            val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

            medicineNameText.text = order.medicineName
            val dosageInfo = buildString {
                append("${order.medicineDosage} ${order.medicineUnit} · ${order.quantity} units")
                if (order.pharmacyName.isNotEmpty()) {
                    append(" · ${order.pharmacyName}")
                }
            }
            dosageText.text = dosageInfo

            // Status badge
            statusBadge.text = order.status.displayName()
            val (bgColor, textColor) = when (order.status) {
                OrderStatus.PENDING -> R.color.warning_container to R.color.warning
                OrderStatus.CONFIRMED -> R.color.info_container to R.color.info
                OrderStatus.PREPARING -> R.color.info_container to R.color.info
                OrderStatus.READY -> R.color.secondary_container to R.color.secondary
                OrderStatus.SHIPPED -> R.color.secondary_container to R.color.secondary
                OrderStatus.DELIVERED -> R.color.success_container to R.color.success
                OrderStatus.CANCELLED -> R.color.surface_variant to R.color.text_tertiary
            }
            statusBadge.setBackgroundColor(ContextCompat.getColor(ctx, bgColor))
            statusBadge.setTextColor(ContextCompat.getColor(ctx, textColor))

            // Date
            order.createdAt?.let {
                dateText.text = "Ordered: ${dateFmt.format(it)}"
            } ?: run {
                dateText.text = "Ordered: —"
            }
            quantityText.text = "Qty: ${order.quantity}"

            // Notes
            if (order.notes.isNotEmpty()) {
                notesText.visibility = View.VISIBLE
                notesText.text = order.notes
            } else {
                notesText.visibility = View.GONE
            }

            // Action buttons
            val canCancel = order.status == OrderStatus.PENDING || order.status == OrderStatus.CONFIRMED
            val canMarkDelivered = order.status == OrderStatus.SHIPPED || order.status == OrderStatus.CONFIRMED
            val canReturn = order.status == OrderStatus.DELIVERED
            val canTrack = order.status != OrderStatus.CANCELLED && order.status != OrderStatus.DELIVERED

            if (canCancel || canMarkDelivered || canReturn || canTrack) {
                actionsContainer.visibility = View.VISIBLE
                cancelButton.visibility = if (canCancel) View.VISIBLE else View.GONE
                trackButton.visibility = if (canTrack) View.VISIBLE else View.GONE
                deliveredButton.visibility = if (canMarkDelivered) View.VISIBLE else View.GONE
                returnButton.visibility = if (canReturn) View.VISIBLE else View.GONE

                cancelButton.setOnClickListener { onCancel?.invoke(order) }
                trackButton.setOnClickListener { onTrack?.invoke(order) }
                deliveredButton.setOnClickListener { onMarkDelivered?.invoke(order) }
                returnButton.setOnClickListener { onReturn?.invoke(order) }
            } else {
                actionsContainer.visibility = View.GONE
            }
        }
    }
}
