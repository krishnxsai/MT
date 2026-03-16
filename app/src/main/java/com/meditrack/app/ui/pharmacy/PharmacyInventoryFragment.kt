package com.meditrack.app.ui.pharmacy

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.meditrack.app.R
import com.meditrack.app.data.model.InventoryItem
import com.meditrack.app.databinding.FragmentPharmacyInventoryBinding

class PharmacyInventoryFragment : Fragment() {

    private var _binding: FragmentPharmacyInventoryBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance() = PharmacyInventoryFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPharmacyInventoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as PharmacyDashboardActivity

        // Low stock observer
        activity.dashboardViewModel.lowStockItems.observe(viewLifecycleOwner) { items ->
            if (items.isEmpty()) {
                binding.inventoryAlertsCard.visibility = View.GONE
            } else {
                binding.inventoryAlertsCard.visibility = View.VISIBLE
                binding.lowStockCountBadge.text =
                    getString(R.string.items_low_stock, items.size)
                populateLowStockItems(items.take(5))
            }
        }

        // Button listeners
        binding.viewInventoryButton.setOnClickListener { navigateToInventory(activity) }
        binding.openInventoryButton.setOnClickListener { navigateToInventory(activity) }
        binding.addMedicineButton.setOnClickListener { navigateToInventory(activity) }
    }

    private fun navigateToInventory(activity: PharmacyDashboardActivity) {
        val pharmacyId = activity.dashboardViewModel.getPharmacyId()
        if (pharmacyId != null) {
            val intent = Intent(requireContext(), InventoryActivity::class.java)
            intent.putExtra("pharmacyId", pharmacyId)
            startActivity(intent)
        } else {
            Toast.makeText(requireContext(), "Set up pharmacy profile first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun populateLowStockItems(items: List<InventoryItem>) {
        binding.lowStockContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        for (item in items) {
            val itemView = inflater.inflate(R.layout.item_low_stock, binding.lowStockContainer, false)
            itemView.findViewById<TextView>(R.id.lowStockMedicineName).text = item.medicineName
            itemView.findViewById<TextView>(R.id.lowStockDetails).text =
                "Stock: ${item.stockQuantity} · Threshold: ${item.lowStockThreshold}"
            itemView.findViewById<TextView>(R.id.lowStockQty).text = item.stockQuantity.toString()
            binding.lowStockContainer.addView(itemView)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
