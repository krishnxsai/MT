package com.meditrack.app.ui.order.unified

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.meditrack.app.R
import com.meditrack.app.data.model.DeliveryWindow
import com.meditrack.app.data.model.WindowType
import com.meditrack.app.databinding.BottomSheetDeliveryWindowBinding

/**
 * Bottom sheet for selecting delivery time window and viewing pricing breakdown.
 * Allows users to choose delivery urgency which affects the delivery fee.
 */
class DeliveryWindowBottomSheet(
    private val currentWindow: DeliveryWindow = DeliveryWindow.flexible(),
    private val onWindowSelected: (DeliveryWindow) -> Unit = {}
) : DialogFragment() {

    private lateinit var binding: BottomSheetDeliveryWindowBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetDeliveryWindowBinding.inflate(inflater, container, false)
        setupUI()
        return binding.root
    }

    private fun setupUI() {
        // Window type options
        val options = listOf(
            DeliveryWindow.morning(),
            DeliveryWindow.afternoon(),
            DeliveryWindow.evening(),
            DeliveryWindow.flexible()
        )

        val optionNames = options.map { "${it.getDisplayName()} (${String.format("%.1fx", it.getDeliveryMultiplier())})" }

        binding.windowSelector.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, optionNames)
        )

        // Set current selection
        binding.windowSelector.setText(optionNames[options.indexOfFirst { it.type == currentWindow.type }])

        // Select button
        binding.btnSelectWindow.setOnClickListener {
            val selectedIndex = options.indexOfFirst {
                it.getDisplayName() in binding.windowSelector.text.toString()
            }
            if (selectedIndex >= 0) {
                onWindowSelected(options[selectedIndex])
                dismiss()
            }
        }

        // Info text
        binding.tvWindowInfo.text = "Choose your preferred delivery time. Evening delivery has a rush fee premium."
    }
}
