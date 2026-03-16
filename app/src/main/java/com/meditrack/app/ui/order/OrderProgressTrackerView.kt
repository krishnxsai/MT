package com.meditrack.app.ui.order

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.meditrack.app.data.model.OrderStatus
import com.meditrack.app.data.model.OrderStatusEntry
import com.meditrack.app.data.model.RefillOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom view that displays a timeline of order status changes.
 * Shows a vertical list of status steps with icons, labels, timestamps, and notes.
 */
class OrderProgressTrackerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: OrderTimelineAdapter

    init {
        setupUI()
    }

    private fun setupUI() {
        recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = OrderTimelineAdapter()
        recyclerView.adapter = adapter

        addView(recyclerView)
    }

    /**
     * Bind an order to the tracker, updating the timeline display.
     */
    fun bindOrder(order: RefillOrder) {
        val steps = buildTimelineSteps(order.statusHistory, order.status)
        adapter.submitList(steps)
    }

    /**
     * Build timeline steps from order status history.
     * Marks completed steps based on current order status.
     */
    private fun buildTimelineSteps(
        statusHistory: List<OrderStatusEntry>,
        currentStatus: OrderStatus
    ): List<OrderTimelineAdapter.TimelineStep> {
        // Define expected order of statuses
        val expectedStatuses = listOf(
            OrderStatus.PENDING,
            OrderStatus.CONFIRMED,
            OrderStatus.PREPARING,
            OrderStatus.READY,
            OrderStatus.SHIPPED,
            OrderStatus.DELIVERED
        )

        val completedStatuses = mutableSetOf<OrderStatus>()
        val statusMap = mutableMapOf<OrderStatus, OrderStatusEntry>()

        // Build maps from status history
        statusHistory.forEach { entry ->
            try {
                val status = OrderStatus.valueOf(entry.status)
                statusMap[status] = entry
                completedStatuses.add(status)
            } catch (e: Exception) {
                // Ignore invalid statuses
            }
        }

        // Build timeline steps in order
        return expectedStatuses.map { status ->
            val entry = statusMap[status]
            val isCompleted = completedStatuses.contains(status)
            val isCurrent = status == currentStatus

            OrderTimelineAdapter.TimelineStep(
                status = status,
                displayLabel = status.getDisplayLabel(),
                iconRes = status.getStatusIcon(),
                timestamp = entry?.changedAt?.let { formatTimestamp(it) } ?: "",
                note = entry?.note ?: "",
                isCompleted = isCompleted,
                isCurrent = isCurrent
            )
        }
    }

    private fun formatTimestamp(date: Date): String {
        return try {
            val format = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            format.format(date)
        } catch (e: Exception) {
            ""
        }
    }
}
