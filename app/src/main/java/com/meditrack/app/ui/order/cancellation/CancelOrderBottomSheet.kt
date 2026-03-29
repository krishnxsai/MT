package com.meditrack.app.ui.order.cancellation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.meditrack.app.databinding.BottomSheetCancelOrderBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Bottom sheet for requesting order cancellation with reason.
 */
@AndroidEntryPoint
class CancelOrderBottomSheet(
    private val orderId: String = "",
    private val onCancellationRequested: (reason: String) -> Unit = {}
) : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetCancelOrderBinding
    private val viewModel: OrderCancellationViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetCancelOrderBinding.inflate(inflater, container, false)
        setupUI()
        observeViewModel()
        return binding.root
    }

    private fun setupUI() {
        // Cancel button
        binding.btnCancelOrder.setOnClickListener {
            val reason = binding.etCancellationReason.text.toString().trim()

            if (reason.isEmpty()) {
                Toast.makeText(requireContext(), "Please provide a reason for cancellation", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.requestCancellation(orderId, reason)
        }

        // Close button
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        // Set predefined reasons
        binding.chipUnauthorized.setOnClickListener {
            binding.etCancellationReason.setText("Ordered by mistake or unauthorized access")
        }

        binding.chipDelay.setOnClickListener {
            binding.etCancellationReason.setText("Delivery is taking too long")
        }

        binding.chipWrongItem.setOnClickListener {
            binding.etCancellationReason.setText("Wrong item or wrong quantity ordered")
        }

        binding.chipOther.setOnClickListener {
            binding.etCancellationReason.setText("")
            binding.etCancellationReason.requestFocus()
        }
    }

    private fun observeViewModel() {
        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.btnCancelOrder.isEnabled = !isLoading
            binding.loadingProgressBar.isVisible = isLoading
        }

        viewModel.successMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                onCancellationRequested(binding.etCancellationReason.text.toString())
                viewModel.clearSuccess()
                dismiss()
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }
}
