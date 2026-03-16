package com.meditrack.app.ui.order.history

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.meditrack.app.R
import com.meditrack.app.data.model.RefillOrder
import com.meditrack.app.ui.order.OrderTrackingActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Patient Order History screen.
 * Shows Active Orders (in progress) and Past Orders (delivered/cancelled).
 */
@AndroidEntryPoint
class OrderHistoryActivity : AppCompatActivity() {

    private val viewModel: OrderHistoryViewModel by viewModels()

    // Views
    private lateinit var toolbar: MaterialToolbar
    private lateinit var loadingContainer: FrameLayout
    private lateinit var emptyStateContainer: LinearLayout
    private lateinit var scrollView: NestedScrollView
    private lateinit var activeOrdersSection: LinearLayout
    private lateinit var activeOrdersRecycler: RecyclerView
    private lateinit var pastOrdersSection: LinearLayout
    private lateinit var pastOrdersRecycler: RecyclerView

    // Adapters
    private lateinit var activeOrdersAdapter: OrderHistoryAdapter
    private lateinit var pastOrdersAdapter: OrderHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_history)

        initViews()
        setupToolbar()
        setupRecyclerViews()
        observeViewModel()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        loadingContainer = findViewById(R.id.loadingContainer)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)
        scrollView = findViewById(R.id.scrollView)
        activeOrdersSection = findViewById(R.id.activeOrdersSection)
        activeOrdersRecycler = findViewById(R.id.activeOrdersRecycler)
        pastOrdersSection = findViewById(R.id.pastOrdersSection)
        pastOrdersRecycler = findViewById(R.id.pastOrdersRecycler)
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerViews() {
        // Active orders adapter
        activeOrdersAdapter = OrderHistoryAdapter { order ->
            navigateToOrderTracking(order)
        }
        activeOrdersRecycler.apply {
            layoutManager = LinearLayoutManager(this@OrderHistoryActivity)
            adapter = activeOrdersAdapter
            isNestedScrollingEnabled = false
        }

        // Past orders adapter
        pastOrdersAdapter = OrderHistoryAdapter { order ->
            navigateToOrderTracking(order)
        }
        pastOrdersRecycler.apply {
            layoutManager = LinearLayoutManager(this@OrderHistoryActivity)
            adapter = pastOrdersAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            // Observe loading state
            viewModel.isLoading.collectLatest { isLoading ->
                loadingContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
                if (isLoading) {
                    scrollView.visibility = View.GONE
                    emptyStateContainer.visibility = View.GONE
                }
            }
        }

        lifecycleScope.launch {
            // Observe has orders
            viewModel.hasOrders.collectLatest { hasOrders ->
                if (!viewModel.isLoading.value) {
                    scrollView.visibility = if (hasOrders) View.VISIBLE else View.GONE
                    emptyStateContainer.visibility = if (hasOrders) View.GONE else View.VISIBLE
                }
            }
        }

        lifecycleScope.launch {
            // Observe active orders
            viewModel.activeOrders.collectLatest { orders ->
                activeOrdersAdapter.submitList(orders)
                activeOrdersSection.visibility = if (orders.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            // Observe past orders
            viewModel.pastOrders.collectLatest { orders ->
                pastOrdersAdapter.submitList(orders)
                pastOrdersSection.visibility = if (orders.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            // Observe error messages
            viewModel.errorMessage.collectLatest { errorMsg ->
                errorMsg?.let {
                    Toast.makeText(this@OrderHistoryActivity, it, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }
    }

    private fun navigateToOrderTracking(order: RefillOrder) {
        val intent = Intent(this, OrderTrackingActivity::class.java).apply {
            putExtra(OrderTrackingActivity.EXTRA_ORDER_ID, order.id)
        }
        startActivity(intent)
    }
}
