package com.meditrack.app.ui.order.unified

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.meditrack.app.R
import com.meditrack.app.data.model.Pharmacy

/**
 * Bottom sheet dialog for confirming an order before placement.
 * Shows order items, pharmacy info, and pricing breakdown.
 */
class OrderConfirmationBottomSheet : BottomSheetDialogFragment() {

    data class OrderItemDisplay(
        val medicineId: String,
        val medicineName: String,
        val quantity: Int,
        val unitPrice: Double,
        val totalPrice: Double,
        val prescriptionRequired: Boolean
    )

    interface OnOrderConfirmListener {
        fun onOrderConfirmed()
        fun onChangePharmacy()
    }

    private var listener: OnOrderConfirmListener? = null
    private var orderItems: List<OrderItemDisplay> = emptyList()
    private var pharmacy: Pharmacy? = null
    private var distanceText: String = ""
    private var subtotal: Double = 0.0
    private var deliveryFee: Double = 0.0
    private var total: Double = 0.0

    private lateinit var tvItemCount: TextView
    private lateinit var rvOrderItems: RecyclerView
    private lateinit var tvPharmacyName: TextView
    private lateinit var tvPharmacyInfo: TextView
    private lateinit var tvChangePharmacy: TextView
    private lateinit var tvSubtotal: TextView
    private lateinit var tvDeliveryFee: TextView
    private lateinit var tvTotal: TextView
    private lateinit var prescriptionWarning: MaterialCardView
    private lateinit var tvPrescriptionWarning: TextView
    private lateinit var btnPlaceOrder: MaterialButton

    private val itemAdapter = OrderItemAdapter()

    companion object {
        fun newInstance(
            items: List<OrderItemDisplay>,
            pharmacy: Pharmacy,
            distanceText: String,
            subtotal: Double,
            deliveryFee: Double,
            total: Double
        ): OrderConfirmationBottomSheet {
            return OrderConfirmationBottomSheet().apply {
                this.orderItems = items
                this.pharmacy = pharmacy
                this.distanceText = distanceText
                this.subtotal = subtotal
                this.deliveryFee = deliveryFee
                this.total = total
            }
        }
    }

    fun setOnOrderConfirmListener(listener: OnOrderConfirmListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_order_confirm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind views
        tvItemCount = view.findViewById(R.id.tvItemCount)
        rvOrderItems = view.findViewById(R.id.rvOrderItems)
        tvPharmacyName = view.findViewById(R.id.tvPharmacyName)
        tvPharmacyInfo = view.findViewById(R.id.tvPharmacyInfo)
        tvChangePharmacy = view.findViewById(R.id.tvChangePharmacy)
        tvSubtotal = view.findViewById(R.id.tvSubtotal)
        tvDeliveryFee = view.findViewById(R.id.tvDeliveryFee)
        tvTotal = view.findViewById(R.id.tvTotal)
        prescriptionWarning = view.findViewById(R.id.prescriptionWarning)
        tvPrescriptionWarning = view.findViewById(R.id.tvPrescriptionWarning)
        btnPlaceOrder = view.findViewById(R.id.btnPlaceOrder)

        setupRecyclerView()
        populateData()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        rvOrderItems.apply {
            adapter = itemAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun populateData() {
        // Items
        tvItemCount.text = "(${orderItems.size} items)"
        itemAdapter.submitList(orderItems)

        // Pharmacy
        pharmacy?.let { p ->
            tvPharmacyName.text = p.name
            tvPharmacyInfo.text = "$distanceText ~ ${p.estimatedDeliveryTime.ifEmpty { "30 min" }} delivery"
        }

        // Pricing
        tvSubtotal.text = formatPrice(subtotal)
        tvDeliveryFee.text = formatPrice(deliveryFee)
        tvTotal.text = formatPrice(total)

        // Prescription warning
        val prescriptionItems = orderItems.filter { it.prescriptionRequired }
        if (prescriptionItems.isNotEmpty()) {
            prescriptionWarning.isVisible = true
            tvPrescriptionWarning.text = "Prescription required for ${prescriptionItems.size} item${if (prescriptionItems.size > 1) "s" else ""}"
        } else {
            prescriptionWarning.isVisible = false
        }
    }

    private fun setupClickListeners() {
        tvChangePharmacy.setOnClickListener {
            listener?.onChangePharmacy()
            dismiss()
        }

        btnPlaceOrder.setOnClickListener {
            listener?.onOrderConfirmed()
            dismiss()
        }
    }

    private fun formatPrice(amount: Double): String {
        return String.format("Rs. %.2f", amount)
    }

    // ==================== ADAPTER ====================

    private class OrderItemAdapter : ListAdapter<OrderItemDisplay, OrderItemAdapter.ViewHolder>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_order_confirm, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvMedicineName: TextView = itemView.findViewById(R.id.tvMedicineName)
            private val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
            private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)

            fun bind(item: OrderItemDisplay) {
                tvMedicineName.text = item.medicineName
                tvQuantity.text = "Qty: ${item.quantity}"
                tvPrice.text = String.format("Rs. %.2f", item.totalPrice)
            }
        }

        private class DiffCallback : DiffUtil.ItemCallback<OrderItemDisplay>() {
            override fun areItemsTheSame(oldItem: OrderItemDisplay, newItem: OrderItemDisplay): Boolean {
                return oldItem.medicineId == newItem.medicineId
            }

            override fun areContentsTheSame(oldItem: OrderItemDisplay, newItem: OrderItemDisplay): Boolean {
                return oldItem == newItem
            }
        }
    }
}
