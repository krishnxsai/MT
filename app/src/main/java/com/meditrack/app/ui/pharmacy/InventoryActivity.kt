package com.meditrack.app.ui.pharmacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.meditrack.app.R
import com.meditrack.app.data.model.InventoryItem
import com.meditrack.app.data.model.Resource
import com.meditrack.app.databinding.ActivityInventoryBinding
import com.meditrack.app.databinding.DialogAddInventoryBinding
import com.meditrack.app.databinding.ItemInventoryBinding
import com.meditrack.app.util.formatInr
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class InventoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInventoryBinding
    private lateinit var viewModel: PharmacyInventoryViewModel
    private lateinit var inventoryAdapter: InventoryAdapter
    private var pharmacyId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInventoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pharmacyId = intent.getStringExtra("pharmacyId") ?: run {
            Toast.makeText(this, "No pharmacy ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[PharmacyInventoryViewModel::class.java]
        viewModel.init(pharmacyId)

        setupRecyclerView()
        setupSearch()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        inventoryAdapter = InventoryAdapter(
            onEdit = { item -> showAddEditDialog(item) },
            onDelete = { item -> confirmDelete(item) }
        )
        binding.inventoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@InventoryActivity)
            adapter = inventoryAdapter
        }
    }

    private fun setupSearch() {
        binding.searchEditText.setOnEditorActionListener { _, _, _ ->
            val query = binding.searchEditText.text?.toString()?.trim() ?: ""
            viewModel.search(query)
            true
        }
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }

        binding.addItemFab.setOnClickListener {
            showAddEditDialog(null)
        }
    }

    private fun observeViewModel() {
        viewModel.inventory.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.emptyStateText.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    if (result.data.isEmpty()) {
                        binding.emptyStateText.visibility = View.VISIBLE
                        binding.inventoryRecyclerView.visibility = View.GONE
                    } else {
                        binding.emptyStateText.visibility = View.GONE
                        binding.inventoryRecyclerView.visibility = View.VISIBLE
                        inventoryAdapter.submitList(result.data)
                    }
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.lowStockAlerts.observe(this) { result ->
            if (result is Resource.Success && result.data.isNotEmpty()) {
                binding.lowStockBanner.visibility = View.VISIBLE
                binding.lowStockText.text = getString(R.string.low_stock_items, result.data.size)
            } else {
                binding.lowStockBanner.visibility = View.GONE
            }
        }

        viewModel.searchResults.observe(this) { result ->
            if (result is Resource.Success) {
                inventoryAdapter.submitList(result.data)
            }
        }

        viewModel.operationResult.observe(this) { result ->
            when (result) {
                is Resource.Success -> Toast.makeText(this, result.data, Toast.LENGTH_SHORT).show()
                is Resource.Error -> Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                is Resource.Loading -> { /* ignore */ }
            }
        }
    }

    private fun showAddEditDialog(existingItem: InventoryItem?) {
        val dialogBinding = DialogAddInventoryBinding.inflate(layoutInflater)
        val dateFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        var selectedExpiryDate: Date? = existingItem?.expiryDate

        // Pre-fill if editing
        existingItem?.let { item ->
            dialogBinding.nameEditText.setText(item.medicineName)
            dialogBinding.genericNameEditText.setText(item.genericName)
            dialogBinding.categoryEditText.setText(item.category)
            dialogBinding.quantityEditText.setText(item.stockQuantity.toString())
            dialogBinding.priceEditText.setText(item.unitPrice.toString())
            dialogBinding.thresholdEditText.setText(item.lowStockThreshold.toString())
            dialogBinding.unitEditText.setText(item.unit)
            dialogBinding.manufacturerEditText.setText(item.manufacturer)
            dialogBinding.batchEditText.setText(item.batchNumber)
            item.expiryDate?.let { dialogBinding.expiryEditText.setText(dateFormat.format(it)) }
        }

        // Expiry date picker
        dialogBinding.expiryEditText.setOnClickListener {
            val cal = Calendar.getInstance()
            selectedExpiryDate?.let { cal.time = it }
            android.app.DatePickerDialog(this, { _, year, month, day ->
                cal.set(year, month, day)
                selectedExpiryDate = cal.time
                dialogBinding.expiryEditText.setText(dateFormat.format(cal.time))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val title = if (existingItem == null) getString(R.string.add_inventory_item)
                    else getString(R.string.edit_inventory_item)

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.save_profile)) { _, _ ->
                val name = dialogBinding.nameEditText.text?.toString()?.trim() ?: ""
                if (name.isEmpty()) {
                    Toast.makeText(this, getString(R.string.error_empty_medicine_name), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val item = InventoryItem(
                    id = existingItem?.id ?: "",
                    pharmacyId = pharmacyId,
                    medicineName = name,
                    genericName = dialogBinding.genericNameEditText.text?.toString()?.trim() ?: "",
                    category = dialogBinding.categoryEditText.text?.toString()?.trim() ?: "",
                    stockQuantity = dialogBinding.quantityEditText.text?.toString()?.toIntOrNull() ?: 0,
                    unitPrice = dialogBinding.priceEditText.text?.toString()?.toDoubleOrNull() ?: 0.0,
                    lowStockThreshold = dialogBinding.thresholdEditText.text?.toString()?.toIntOrNull() ?: 10,
                    unit = dialogBinding.unitEditText.text?.toString()?.trim() ?: "tablets",
                    manufacturer = dialogBinding.manufacturerEditText.text?.toString()?.trim() ?: "",
                    batchNumber = dialogBinding.batchEditText.text?.toString()?.trim() ?: "",
                    expiryDate = selectedExpiryDate
                )

                if (existingItem != null) {
                    viewModel.updateItem(item)
                } else {
                    viewModel.addItem(item)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmDelete(item: InventoryItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete))
            .setMessage(getString(R.string.confirm_delete_item))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteItem(item.id)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ══════════════════════════════════════════════════════════════
    // Inner Adapter
    // ══════════════════════════════════════════════════════════════

    class InventoryAdapter(
        private val onEdit: (InventoryItem) -> Unit,
        private val onDelete: (InventoryItem) -> Unit
    ) : ListAdapter<InventoryItem, InventoryAdapter.ViewHolder>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemInventoryBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class ViewHolder(
            private val binding: ItemInventoryBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            private val dateFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())

            fun bind(item: InventoryItem) {
                binding.medicineName.text = item.medicineName
                binding.genericName.text = item.genericName.ifEmpty { item.category }

                // Stock chip
                val stockLabel = "Stock: ${item.stockQuantity}"
                binding.stockChip.text = stockLabel
                val ctx = binding.root.context
                if (item.isLowStock) {
                    binding.stockChip.setChipBackgroundColorResource(R.color.warning_container)
                    binding.stockChip.setTextColor(ctx.getColor(R.color.on_warning_container))
                } else {
                    binding.stockChip.setChipBackgroundColorResource(R.color.success_container)
                    binding.stockChip.setTextColor(ctx.getColor(R.color.on_success_container))
                }

                // Price chip
                binding.priceChip.text = formatInr(item.unitPrice)

                // Expiry chip
                if (item.expiryDate != null) {
                    binding.expiryChip.visibility = View.VISIBLE
                    binding.expiryChip.text = "Exp: ${dateFormat.format(item.expiryDate)}"
                    if (item.isExpired) {
                        binding.expiryChip.setChipBackgroundColorResource(R.color.error_container)
                    }
                } else {
                    binding.expiryChip.visibility = View.GONE
                }

                binding.editButton.setOnClickListener { onEdit(item) }
                binding.deleteButton.setOnClickListener { onDelete(item) }
            }
        }

        class DiffCallback : DiffUtil.ItemCallback<InventoryItem>() {
            override fun areItemsTheSame(a: InventoryItem, b: InventoryItem) = a.id == b.id
            override fun areContentsTheSame(a: InventoryItem, b: InventoryItem) = a == b
        }
    }
}
