package com.example.meditrack.ui.order

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meditrack.R
import com.example.meditrack.data.model.Medicine
import com.example.meditrack.data.model.OrderStatus
import com.example.meditrack.data.model.RefillOrder
import com.example.meditrack.data.model.Resource
import com.example.meditrack.databinding.ActivityRefillOrdersBinding
import com.example.meditrack.ui.medicine.MedicineViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.Locale

class RefillOrdersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRefillOrdersBinding
    private val viewModel: OrderViewModel by viewModels()
    private val medicineViewModel: MedicineViewModel by viewModels()
    private lateinit var adapter: OrderAdapter
    private lateinit var quickOrderAdapter: QuickOrderAdapter

    private var allOrders: List<RefillOrder> = emptyList()
    private var allMedicines: List<Medicine> = emptyList()
    private var showingActive = true

    // Pending refill state (held while user picks pharmacy)
    private var pendingRefillMedicine: Medicine? = null
    private var pendingRefillQty: Int = 0
    private var pendingRefillNotes: String = ""

    private val pharmacyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pharmacyId = result.data?.getStringExtra(PharmacyListActivity.RESULT_PHARMACY_ID) ?: ""
            val pharmacyName = result.data?.getStringExtra(PharmacyListActivity.RESULT_PHARMACY_NAME) ?: ""
            val medicine = pendingRefillMedicine
            if (medicine != null && pharmacyId.isNotEmpty()) {
                viewModel.placeRefillOrderWithPharmacy(
                    medicine, pendingRefillQty, pharmacyId, pharmacyName, pendingRefillNotes
                )
            }
            pendingRefillMedicine = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRefillOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupQuickOrderList()
        setupTabs()
        setupFab()
        observeViewModel()

        viewModel.loadOrders()
        viewModel.loadLowStockMedicines()
        medicineViewModel.loadMedicines()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = OrderAdapter(
            onCancel = { order ->
                AlertDialog.Builder(this)
                    .setTitle("Cancel Order")
                    .setMessage("Cancel refill order for ${order.medicineName}?")
                    .setPositiveButton("Cancel Order") { _, _ ->
                        viewModel.cancelOrder(order.id, "Cancelled by user")
                    }
                    .setNegativeButton("Keep", null)
                    .show()
            },
            onMarkDelivered = { order ->
                AlertDialog.Builder(this)
                    .setTitle("Mark as Delivered")
                    .setMessage("Confirm that ${order.quantity} ${order.medicineUnit} of ${order.medicineName} has been received?\n\nYour stock will be automatically updated.")
                    .setPositiveButton("Confirm") { _, _ ->
                        viewModel.markDelivered(order.id)
                    }
                    .setNegativeButton("Not Yet", null)
                    .show()
            },
            onTrack = { order ->
                showOrderTrackingSheet(order)
            }
        )

        binding.ordersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@RefillOrdersActivity)
            adapter = this@RefillOrdersActivity.adapter
        }
    }

    private fun setupQuickOrderList() {
        quickOrderAdapter = QuickOrderAdapter { medicine ->
            showRefillOrderDialog(medicine)
        }
        binding.quickOrderRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@RefillOrdersActivity)
            adapter = quickOrderAdapter
        }
    }

    private fun setupFab() {
        binding.newOrderFab.setOnClickListener {
            if (allMedicines.isEmpty()) {
                Toast.makeText(this, "No medicines added yet. Add medicines first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showMedicinePickerDialog()
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                showingActive = tab?.position == 0
                filterAndDisplay()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun observeViewModel() {
        viewModel.orders.observe(this) { result ->
            when (result) {
                is Resource.Loading -> binding.progressBar.visibility = View.VISIBLE
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    allOrders = result.data
                    filterAndDisplay()
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.lowStockMedicines.observe(this) { result ->
            if (result is Resource.Success && result.data.isNotEmpty()) {
                binding.lowStockCard.visibility = View.VISIBLE
                binding.lowStockCountText.text = "${result.data.size} medicine${if (result.data.size > 1) "s" else ""} running low"
            } else {
                binding.lowStockCard.visibility = View.GONE
            }
        }

        viewModel.actionResult.observe(this) { result ->
            when (result) {
                is Resource.Success -> Toast.makeText(this, getString(R.string.order_updated), Toast.LENGTH_SHORT).show()
                is Resource.Error -> Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                is Resource.Loading -> {}
            }
        }

        viewModel.placeOrderResult.observe(this) { result ->
            when (result) {
                is Resource.Success -> Toast.makeText(this, getString(R.string.order_placed), Toast.LENGTH_SHORT).show()
                is Resource.Error -> Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                is Resource.Loading -> {}
            }
        }

        medicineViewModel.medicines.observe(this) { result ->
            if (result is Resource.Success) {
                allMedicines = result.data.filter { it.isActive }
                updateQuickOrderSection()
            }
        }
    }

    private fun updateQuickOrderSection() {
        if (allMedicines.isEmpty()) {
            binding.quickOrderCard.visibility = View.GONE
            return
        }
        binding.quickOrderCard.visibility = View.VISIBLE
        binding.medicineCountText.text = "${allMedicines.size} active"

        val sorted = allMedicines.sortedWith(
            compareByDescending<Medicine> { it.isOutOfStock }
                .thenByDescending { it.isLowStock }
                .thenBy { it.name.lowercase() }
        )
        quickOrderAdapter.submitList(sorted)
    }

    private fun filterAndDisplay() {
        val activeStatuses = setOf(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.SHIPPED)
        val filtered = if (showingActive) {
            allOrders.filter { it.status in activeStatuses }
        } else {
            allOrders.filter { it.status == OrderStatus.DELIVERED || it.status == OrderStatus.CANCELLED }
        }

        if (filtered.isEmpty()) {
            binding.emptyStateContainer.visibility = View.VISIBLE
            binding.ordersRecyclerView.visibility = View.GONE
            binding.emptyText.text = if (showingActive) getString(R.string.no_active_orders) else getString(R.string.no_order_history)
        } else {
            binding.emptyStateContainer.visibility = View.GONE
            binding.ordersRecyclerView.visibility = View.VISIBLE
            adapter.submitList(filtered)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Medicine Picker Dialog (FAB → pick → refill dialog)
    // ═══════════════════════════════════════════════════════════

    private fun showMedicinePickerDialog() {
        val medicines = allMedicines
        if (medicines.isEmpty()) return
        val names = medicines.map { med ->
            val stock = if (med.isRefillTrackingEnabled) " (${med.currentQuantity} left)" else ""
            "${med.name} — ${med.dosage} ${med.unit}$stock"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Medicine to Order")
            .setItems(names) { _, which -> showRefillOrderDialog(medicines[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ═══════════════════════════════════════════════════════════
    // Refill Order Dialog
    // ═══════════════════════════════════════════════════════════

    private fun showRefillOrderDialog(medicine: Medicine) {
        viewModel.verifyPrescription(medicine)

        val dialogView = layoutInflater.inflate(R.layout.dialog_refill_order, null)
        val medicineInfoText = dialogView.findViewById<TextView>(R.id.medicineInfoText)
        val stockInfoCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.stockInfoCard)
        val currentStockText = dialogView.findViewById<TextView>(R.id.currentStockText)
        val quantityInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.quantityInput)
        val notesInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.notesInput)

        medicineInfoText.text = "${medicine.name} • ${medicine.dosage} ${medicine.unit}"
        if (medicine.isRefillTrackingEnabled) {
            stockInfoCard.visibility = View.VISIBLE
            currentStockText.text = "Current stock: ${medicine.currentQuantity} remaining"
        }
        if (medicine.totalQuantity > 0) quantityInput.setText(medicine.totalQuantity.toString())

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.place_order)) { _, _ ->
                val qty = quantityInput.text?.toString()?.toIntOrNull() ?: 0
                val notes = notesInput.text?.toString()?.trim() ?: ""
                if (qty <= 0) { Toast.makeText(this, "Please enter a valid quantity", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                viewModel.placeRefillOrder(medicine, qty, notes)
            }
            .setNeutralButton(getString(R.string.choose_pharmacy)) { _, _ ->
                val qty = quantityInput.text?.toString()?.toIntOrNull() ?: 0
                val notes = notesInput.text?.toString()?.trim() ?: ""
                if (qty <= 0) { Toast.makeText(this, "Please enter a valid quantity", Toast.LENGTH_SHORT).show(); return@setNeutralButton }
                pendingRefillMedicine = medicine
                pendingRefillQty = qty
                pendingRefillNotes = notes
                val verification = viewModel.prescriptionVerification.value
                val isValid = (verification as? Resource.Success)?.data?.isValid ?: false
                val msg = (verification as? Resource.Success)?.data?.reason ?: ""
                val intent = Intent(this, PharmacyListActivity::class.java).apply {
                    putExtra(PharmacyListActivity.EXTRA_MEDICINE_NAME, medicine.name)
                    putExtra(PharmacyListActivity.EXTRA_MEDICINE_ID, medicine.id)
                    putExtra(PharmacyListActivity.EXTRA_PRESCRIPTION_VALID, isValid)
                    putExtra(PharmacyListActivity.EXTRA_PRESCRIPTION_MSG, msg)
                }
                pharmacyLauncher.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ═══════════════════════════════════════════════════════════
    // Order Tracking Bottom Sheet
    // ═══════════════════════════════════════════════════════════

    private fun showOrderTrackingSheet(order: RefillOrder) {
        val sheet = BottomSheetDialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
        }

        // Title
        container.addView(TextView(this).apply {
            text = "Order Tracking"
            setTextColor(ContextCompat.getColor(this@RefillOrdersActivity, R.color.text_primary))
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })

        // Medicine info
        container.addView(TextView(this).apply {
            text = "${order.medicineName} — ${order.quantity} ${order.medicineUnit}"
            setTextColor(ContextCompat.getColor(this@RefillOrdersActivity, R.color.text_secondary))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) }
        })

        if (order.pharmacyName.isNotEmpty()) {
            container.addView(TextView(this).apply {
                text = "Pharmacy: ${order.pharmacyName}"
                setTextColor(ContextCompat.getColor(this@RefillOrdersActivity, R.color.text_secondary))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) }
            })
        }

        // Divider
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(16); bottomMargin = dp(16) }
            setBackgroundColor(ContextCompat.getColor(this@RefillOrdersActivity, R.color.divider))
        })

        // Timeline steps
        val steps = listOf(
            Triple("Order Placed", OrderStatus.PENDING, R.drawable.ic_check),
            Triple("Confirmed", OrderStatus.CONFIRMED, R.drawable.ic_check),
            Triple("Shipped", OrderStatus.SHIPPED, R.drawable.ic_clock),
            Triple("Delivered", OrderStatus.DELIVERED, R.drawable.ic_check)
        )

        val currentIndex = steps.indexOfFirst { it.second == order.status }
        val dateFmt = SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault())

        for ((i, step) in steps.withIndex()) {
            val (label, status, iconRes) = step
            val isCompleted = i <= currentIndex && order.status != OrderStatus.CANCELLED
            val isCurrent = i == currentIndex

            val stepRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            // Timeline indicator (circle + line)
            val indicatorCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(dp(32), LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val circleColor = when {
                isCompleted -> R.color.primary
                else -> R.color.text_hint
            }
            val circle = ImageView(this).apply {
                setImageResource(if (isCompleted) R.drawable.ic_check else R.drawable.ic_clock)
                setColorFilter(ContextCompat.getColor(this@RefillOrdersActivity, if (isCompleted) R.color.white else R.color.text_hint))
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@RefillOrdersActivity, circleColor))
                background = ContextCompat.getDrawable(this@RefillOrdersActivity, R.drawable.bg_circle)
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            indicatorCol.addView(circle)

            // Connecting line
            if (i < steps.size - 1) {
                indicatorCol.addView(View(this).apply {
                    val lineColor = if (i < currentIndex) R.color.primary else R.color.outline_light
                    layoutParams = LinearLayout.LayoutParams(dp(2), dp(28)).apply { gravity = Gravity.CENTER_HORIZONTAL }
                    setBackgroundColor(ContextCompat.getColor(this@RefillOrdersActivity, lineColor))
                })
            }

            stepRow.addView(indicatorCol)

            // Step text
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
            }

            textCol.addView(TextView(this).apply {
                text = label
                setTextColor(ContextCompat.getColor(this@RefillOrdersActivity, if (isCompleted) R.color.text_primary else R.color.text_hint))
                textSize = if (isCurrent) 15f else 14f
                typeface = if (isCurrent) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            })

            // Show date from status history if available
            val historyEntry = order.statusHistory.find { it.status == status.name }
            if (historyEntry?.changedAt != null) {
                textCol.addView(TextView(this).apply {
                    text = dateFmt.format(historyEntry.changedAt)
                    setTextColor(ContextCompat.getColor(this@RefillOrdersActivity, R.color.text_tertiary))
                    textSize = 12f
                })
            }

            if (historyEntry?.note?.isNotEmpty() == true) {
                textCol.addView(TextView(this).apply {
                    text = historyEntry.note
                    setTextColor(ContextCompat.getColor(this@RefillOrdersActivity, R.color.text_secondary))
                    textSize = 12f
                })
            }

            stepRow.addView(textCol)
            container.addView(stepRow)
        }

        // Handle cancelled state
        if (order.status == OrderStatus.CANCELLED) {
            container.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(16); bottomMargin = dp(8) }
                setBackgroundColor(ContextCompat.getColor(this@RefillOrdersActivity, R.color.divider))
            })
            container.addView(TextView(this).apply {
                text = "⚠ Order Cancelled" + if (order.cancelReason.isNotEmpty()) "\nReason: ${order.cancelReason}" else ""
                setTextColor(ContextCompat.getColor(this@RefillOrdersActivity, R.color.error))
                textSize = 14f
            })
        }

        sheet.setContentView(container)
        sheet.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
