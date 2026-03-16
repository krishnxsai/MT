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
import com.meditrack.app.data.model.OrderTransaction
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.TransactionType
import com.meditrack.app.databinding.ActivityTransactionHistoryBinding
import com.meditrack.app.databinding.ItemTransactionBinding
import com.meditrack.app.util.formatInr
import java.text.SimpleDateFormat
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TransactionHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionHistoryBinding
    private lateinit var viewModel: TransactionHistoryViewModel
    private lateinit var txnAdapter: TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pharmacyId = intent.getStringExtra("pharmacyId") ?: run {
            Toast.makeText(this, "No pharmacy ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[TransactionHistoryViewModel::class.java]
        viewModel.init(pharmacyId)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        txnAdapter = TransactionAdapter()
        binding.transactionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@TransactionHistoryActivity)
            adapter = txnAdapter
        }
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }
    }

    private fun observeViewModel() {
        viewModel.transactions.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.emptyStateText.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    if (result.data.isEmpty()) {
                        binding.emptyStateText.visibility = View.VISIBLE
                        binding.transactionsRecyclerView.visibility = View.GONE
                    } else {
                        binding.emptyStateText.visibility = View.GONE
                        binding.transactionsRecyclerView.visibility = View.VISIBLE
                        txnAdapter.submitList(result.data)
                    }
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.revenueSummary.observe(this) { stats ->
            binding.totalRevenueText.text = formatInr(stats["totalRevenue"] ?: 0.0)
            binding.todayRevenueText.text = formatInr(stats["todayRevenue"] ?: 0.0)
            binding.totalTransactionsText.text = (stats["totalTransactions"]?.toInt() ?: 0).toString()
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Inner Adapter
    // ══════════════════════════════════════════════════════════════

    class TransactionAdapter : ListAdapter<OrderTransaction, TransactionAdapter.ViewHolder>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemTransactionBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        class ViewHolder(
            private val binding: ItemTransactionBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            private val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())

            fun bind(txn: OrderTransaction) {
                binding.medicineNameText.text = txn.medicineName
                binding.amountText.text = formatInr(txn.amount)
                binding.orderIdText.text = "Order: #${txn.orderId.take(8)}"
                binding.dateText.text = txn.createdAt?.let { dateFormat.format(it) } ?: ""

                val ctx = binding.root.context

                // Type chip
                when (txn.type) {
                    TransactionType.PURCHASE -> {
                        binding.typeChip.text = ctx.getString(R.string.purchase)
                        binding.typeChip.setChipBackgroundColorResource(R.color.success_container)
                        binding.typeChip.setTextColor(ctx.getColor(R.color.on_success_container))
                        binding.amountText.setTextColor(ctx.getColor(R.color.primary))
                    }
                    TransactionType.REFUND -> {
                        binding.typeChip.text = ctx.getString(R.string.refund)
                        binding.typeChip.setChipBackgroundColorResource(R.color.error_container)
                        binding.typeChip.setTextColor(ctx.getColor(R.color.on_error_container))
                        binding.amountText.setTextColor(ctx.getColor(R.color.error))
                    }
                    TransactionType.INSURANCE_CLAIM -> {
                        binding.typeChip.text = "Insurance"
                        binding.typeChip.setChipBackgroundColorResource(R.color.info_container)
                        binding.typeChip.setTextColor(ctx.getColor(R.color.on_info_container))
                        binding.amountText.setTextColor(ctx.getColor(R.color.info))
                    }
                }

                // Status chip - prescription verified indicator
                if (txn.prescriptionVerified) {
                    binding.statusChip.visibility = View.VISIBLE
                    binding.statusChip.text = ctx.getString(R.string.prescription_verified)
                    binding.statusChip.setChipBackgroundColorResource(R.color.success_container)
                    binding.statusChip.setChipIconResource(R.drawable.ic_check_circle)
                } else {
                    binding.statusChip.visibility = View.VISIBLE
                    binding.statusChip.text = txn.status.name
                    binding.statusChip.setChipBackgroundColorResource(R.color.surface_variant)
                }
            }
        }

        class DiffCallback : DiffUtil.ItemCallback<OrderTransaction>() {
            override fun areItemsTheSame(a: OrderTransaction, b: OrderTransaction) = a.id == b.id
            override fun areContentsTheSame(a: OrderTransaction, b: OrderTransaction) = a == b
        }
    }
}
