package com.meditrack.app.ui.pharmacy

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.meditrack.app.databinding.FragmentPharmacyAnalyticsBinding
import com.meditrack.app.util.formatInr

class PharmacyAnalyticsFragment : Fragment() {

    private var _binding: FragmentPharmacyAnalyticsBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance() = PharmacyAnalyticsFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPharmacyAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as PharmacyDashboardActivity

        // Revenue stats
        activity.dashboardViewModel.revenueStats.observe(viewLifecycleOwner) { stats ->
            binding.todayRevenueText.text = formatInr(stats["todayRevenue"] ?: 0.0)
            binding.totalRevenueText.text = formatInr(stats["totalRevenue"] ?: 0.0)
            binding.refundsText.text = formatInr(stats["refundTotal"] ?: 0.0)
        }

        // Total order count
        activity.dashboardViewModel.orderCounts.observe(viewLifecycleOwner) { counts ->
            binding.totalOrdersCount.text = (counts["total"] ?: 0).toString()
        }

        // Top medicines
        activity.dashboardViewModel.topMedicines.observe(viewLifecycleOwner) { medicines ->
            if (medicines.isNullOrEmpty()) {
                binding.topMedicinesText.text = getString(com.meditrack.app.R.string.no_data_yet)
            } else {
                binding.topMedicinesText.text = medicines.joinToString("\n") { (name, count) ->
                    "$name — $count orders"
                }
            }
        }

        // Transactions button
        binding.viewTransactionsButton.setOnClickListener {
            val pharmacyId = activity.dashboardViewModel.getPharmacyId()
            if (pharmacyId != null) {
                val intent = Intent(requireContext(), TransactionHistoryActivity::class.java)
                intent.putExtra("pharmacyId", pharmacyId)
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "Set up pharmacy profile first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
