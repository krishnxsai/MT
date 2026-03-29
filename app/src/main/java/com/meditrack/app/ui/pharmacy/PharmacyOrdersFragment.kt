package com.meditrack.app.ui.pharmacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.meditrack.app.data.model.RefillOrder
import com.meditrack.app.data.model.Resource
import com.meditrack.app.databinding.FragmentPharmacyOrdersBinding

class PharmacyOrdersFragment : Fragment() {

    private var _binding: FragmentPharmacyOrdersBinding? = null
    private val binding get() = _binding!!

    private lateinit var orderAdapter: PharmacyOrderAdapter
    private var statusFilter: String? = null

    companion object {
        private const val ARG_STATUS_FILTER = "status_filter"

        fun newInstance(statusFilter: String? = null): PharmacyOrdersFragment {
            return PharmacyOrdersFragment().apply {
                arguments = Bundle().apply {
                    statusFilter?.let { putString(ARG_STATUS_FILTER, it) }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusFilter = arguments?.getString(ARG_STATUS_FILTER)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPharmacyOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as PharmacyDashboardActivity
        orderAdapter = PharmacyOrderAdapter(
            onAccept = { order -> activity.handleAcceptOrder(order) },
            onReject = { order -> activity.handleRejectOrder(order) },
            onCall = { order -> activity.handleCallPatient(order) }
        )
        binding.ordersRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = orderAdapter
        }

        activity.dashboardViewModel.orders.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.ordersProgress.visibility = View.VISIBLE
                    binding.emptyOrdersText.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.ordersProgress.visibility = View.GONE
                    val filtered = filterOrders(result.data)
                    if (filtered.isEmpty()) {
                        binding.emptyOrdersText.visibility = View.VISIBLE
                        binding.ordersRecyclerView.visibility = View.GONE
                        binding.ordersLabel.visibility = View.GONE
                    } else {
                        binding.emptyOrdersText.visibility = View.GONE
                        binding.ordersRecyclerView.visibility = View.VISIBLE
                        binding.ordersLabel.visibility = View.VISIBLE
                        orderAdapter.submitList(null)
                        orderAdapter.submitList(filtered.toList())
                    }
                }
                is Resource.Error -> {
                    binding.ordersProgress.visibility = View.GONE
                    binding.emptyOrdersText.visibility = View.VISIBLE
                    binding.emptyOrdersText.text = result.message
                }
            }
        }

        activity.dashboardViewModel.patientNames.observe(viewLifecycleOwner) { names ->
            orderAdapter.updatePatientNames(names)
        }
    }

    private fun filterOrders(orders: List<RefillOrder>): List<RefillOrder> {
        val filter = statusFilter ?: return orders
        return orders.filter { it.status.name.equals(filter, ignoreCase = true) }
    }

    fun setStatusFilter(filter: String?) {
        statusFilter = filter
        // Re-apply filter from current data
        val activity = activity as? PharmacyDashboardActivity ?: return
        val current = activity.dashboardViewModel.orders.value
        if (current is Resource.Success) {
            val filtered = filterOrders(current.data)
            if (filtered.isEmpty()) {
                binding.emptyOrdersText.visibility = View.VISIBLE
                binding.ordersRecyclerView.visibility = View.GONE
            } else {
                binding.emptyOrdersText.visibility = View.GONE
                binding.ordersRecyclerView.visibility = View.VISIBLE
                orderAdapter.submitList(null)
                orderAdapter.submitList(filtered.toList())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
