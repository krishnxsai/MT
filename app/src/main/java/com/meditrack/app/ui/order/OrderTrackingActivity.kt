package com.meditrack.app.ui.order

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.meditrack.app.R
import com.meditrack.app.data.model.RefillOrder
import com.meditrack.app.databinding.ActivityOrderTrackingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

/**
 * Real-time order tracking screen showing live status updates.
 * Displays a Zomato-style progress tracker with pharmacy details.
 */
@AndroidEntryPoint
class OrderTrackingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ORDER_ID = "order_id"
    }

    private lateinit var binding: ActivityOrderTrackingBinding
    private val viewModel: OrderTrackingViewModel by viewModels()
    private lateinit var orderItemsAdapter: OrderItemSummaryAdapter
    private var previousStatus: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get order ID from intent
        val orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: run {
            finish()
            return
        }

        setupToolbar()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        orderItemsAdapter = OrderItemSummaryAdapter()
        binding.rvOrderItems.apply {
            layoutManager = LinearLayoutManager(this@OrderTrackingActivity)
            adapter = orderItemsAdapter
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe order data
                launch {
                    viewModel.order.collectLatest { order ->
                        if (order == null) {
                            binding.loadingOverlay.isVisible = true
                            return@collectLatest
                        }

                        binding.loadingOverlay.isVisible = false

                        // Update order ID in toolbar
                        binding.tvOrderId.text = "Order #${order.id.take(8)}"

                        // Update pharmacy details
                        binding.tvPharmacyName.text = order.pharmacyName.ifEmpty { "Pharmacy" }
                        binding.tvDeliveryAddress.text = formatDeliveryAddress(order)

                        // Update estimated delivery time
                        binding.tvEstimatedDelivery.text = formatEstimatedDelivery(order)

                        // Update progress tracker
                        binding.progressTracker.bindOrder(order)

                        // Update pricing
                        binding.tvTotalLabel.text = String.format("Total: ₹%.2f", order.totalAmount)

                        // Populate order items list
                        if (order.isMultiItemOrder) {
                            orderItemsAdapter.submitList(order.items)
                        } else if (order.medicineName.isNotEmpty()) {
                            orderItemsAdapter.submitList(listOf(
                                com.meditrack.app.data.model.OrderItem(
                                    medicineId = order.medicineId,
                                    medicineName = order.medicineName,
                                    medicineDosage = order.medicineDosage,
                                    quantity = order.quantity,
                                    unitPrice = order.totalAmount,
                                    totalPrice = order.totalAmount
                                )
                            ))
                        }

                        // Show status change toast
                        val currentStatus = order.status.getDisplayLabel()
                        if (previousStatus != null && currentStatus != previousStatus) {
                            showStatusChangeToast(order.status.getDisplayLabel())
                        }
                        previousStatus = currentStatus
                    }
                }

                // Observe status label
                launch {
                    viewModel.statusLabel.collectLatest { label ->
                        // Can be used to update a status display if needed
                    }
                }
            }
        }
    }

    private fun formatDeliveryAddress(order: RefillOrder): String {
        val address = order.deliveryAddress ?: return "No delivery address"
        val parts = mutableListOf<String>()

        if (address.fullAddress.isNotBlank()) {
            parts.add(address.fullAddress)
        }
        if (address.landmark.isNotBlank()) {
            parts.add("Near ${address.landmark}")
        }

        return parts.joinToString(", ").ifBlank { "No delivery address" }
    }

    private fun formatEstimatedDelivery(order: com.meditrack.app.data.model.RefillOrder): String {
        return if (order.estimatedDelivery != null) {
            val format = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault())
            "Estimated delivery: ${format.format(order.estimatedDelivery)}"
        } else if (order.estimatedDeliveryMinutes > 0) {
            "Delivery in ~${order.estimatedDeliveryMinutes} minutes"
        } else {
            "Calculating delivery time..."
        }
    }

    private fun showStatusChangeToast(status: String) {
        val message = "Order status: $status"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
