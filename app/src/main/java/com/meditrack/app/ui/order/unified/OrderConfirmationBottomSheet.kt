package com.meditrack.app.ui.order.unified

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.meditrack.app.R
import com.meditrack.app.data.model.Pharmacy
import java.io.Serializable
import java.util.Locale

/**
 * Bottom sheet dialog for confirming an order before placement.
 * Shows order items, pharmacy info, and pricing breakdown.
 */
class OrderConfirmationBottomSheet : BottomSheetDialogFragment() {

    data class OrderItemDisplay(
        val medicineId: String,
        val medicineName: String,
        val quantity: Int,
        val unitPrice: Double,
        val totalPrice: Double,
        val prescriptionRequired: Boolean
    ) : Serializable

    interface OnOrderConfirmListener {
        fun onOrderConfirmed(deliveryAddress: String)
        fun onChangePharmacy()
    }

    private var listener: OnOrderConfirmListener? = null
    private var orderItems: List<OrderItemDisplay> = emptyList()
    private var pharmacyName: String = ""
    private var pharmacyInfo: String = ""
    private var subtotal: Double = 0.0
    private var deliveryFee: Double = 0.0
    private var total: Double = 0.0

    private lateinit var tvItemCount: TextView
    private lateinit var rvOrderItems: RecyclerView
    private lateinit var tvPharmacyName: TextView
    private lateinit var tvPharmacyInfo: TextView
    private lateinit var tvChangePharmacy: TextView
    private lateinit var tvUseCurrentLocation: TextView
    private lateinit var tilDeliveryAddress: TextInputLayout
    private lateinit var etDeliveryAddress: TextInputEditText
    private lateinit var tvSubtotal: TextView
    private lateinit var tvDeliveryFee: TextView
    private lateinit var tvTotal: TextView
    private lateinit var prescriptionWarning: MaterialCardView
    private lateinit var tvPrescriptionWarning: TextView
    private lateinit var btnPlaceOrder: MaterialButton

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            fetchCurrentAddress()
        } else {
            showToast(getString(R.string.location_permission_required))
        }
    }

    private val itemAdapter = OrderItemAdapter()

    companion object {
        private const val ARG_ITEMS = "arg_items"
        private const val ARG_PHARMACY_NAME = "arg_pharmacy_name"
        private const val ARG_PHARMACY_INFO = "arg_pharmacy_info"
        private const val ARG_SUBTOTAL = "arg_subtotal"
        private const val ARG_DELIVERY_FEE = "arg_delivery_fee"
        private const val ARG_TOTAL = "arg_total"

        fun newInstance(
            items: List<OrderItemDisplay>,
            pharmacy: Pharmacy,
            distanceText: String,
            subtotal: Double,
            deliveryFee: Double,
            total: Double
        ): OrderConfirmationBottomSheet {
            return OrderConfirmationBottomSheet().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_ITEMS, ArrayList(items))
                    putString(ARG_PHARMACY_NAME, pharmacy.name)
                    putString(ARG_PHARMACY_INFO, "$distanceText ~ ${pharmacy.estimatedDeliveryTime.ifEmpty { "30 min" }} delivery")
                    putDouble(ARG_SUBTOTAL, subtotal)
                    putDouble(ARG_DELIVERY_FEE, deliveryFee)
                    putDouble(ARG_TOTAL, total)
                }
            }
        }
    }

    fun setOnOrderConfirmListener(listener: OnOrderConfirmListener) {
        this.listener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { args ->
            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            orderItems = (args.getSerializable(ARG_ITEMS) as? ArrayList<OrderItemDisplay>) ?: emptyList()
            pharmacyName = args.getString(ARG_PHARMACY_NAME, "")
            pharmacyInfo = args.getString(ARG_PHARMACY_INFO, "")
            subtotal = args.getDouble(ARG_SUBTOTAL, 0.0)
            deliveryFee = args.getDouble(ARG_DELIVERY_FEE, 0.0)
            total = args.getDouble(ARG_TOTAL, 0.0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_order_confirm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind views
        tvItemCount = view.findViewById(R.id.tvItemCount)
        rvOrderItems = view.findViewById(R.id.rvOrderItems)
        tvPharmacyName = view.findViewById(R.id.tvPharmacyName)
        tvPharmacyInfo = view.findViewById(R.id.tvPharmacyInfo)
        tvChangePharmacy = view.findViewById(R.id.tvChangePharmacy)
        tvUseCurrentLocation = view.findViewById(R.id.tvUseCurrentLocation)
        tilDeliveryAddress = view.findViewById(R.id.tilDeliveryAddress)
        etDeliveryAddress = view.findViewById(R.id.etDeliveryAddress)
        tvSubtotal = view.findViewById(R.id.tvSubtotal)
        tvDeliveryFee = view.findViewById(R.id.tvDeliveryFee)
        tvTotal = view.findViewById(R.id.tvTotal)
        prescriptionWarning = view.findViewById(R.id.prescriptionWarning)
        tvPrescriptionWarning = view.findViewById(R.id.tvPrescriptionWarning)
        btnPlaceOrder = view.findViewById(R.id.btnPlaceOrder)

        setupRecyclerView()
        populateData()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        rvOrderItems.apply {
            adapter = itemAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun populateData() {
        // Items
        tvItemCount.text = "(${orderItems.size} items)"
        itemAdapter.submitList(orderItems)

        // Pharmacy
        tvPharmacyName.text = pharmacyName.ifEmpty { "Pharmacy" }
        tvPharmacyInfo.text = pharmacyInfo.ifEmpty { "Nearby pharmacy" }

        // Pricing
        tvSubtotal.text = formatPrice(subtotal)
        tvDeliveryFee.text = formatPrice(deliveryFee)
        tvTotal.text = formatPrice(total)

        // Prescription warning
        val prescriptionItems = orderItems.filter { it.prescriptionRequired }
        if (prescriptionItems.isNotEmpty()) {
            prescriptionWarning.isVisible = true
            tvPrescriptionWarning.text = "Prescription required for ${prescriptionItems.size} item${if (prescriptionItems.size > 1) "s" else ""}"
        } else {
            prescriptionWarning.isVisible = false
        }
    }

    private fun setupClickListeners() {
        tvChangePharmacy.setOnClickListener {
            listener?.onChangePharmacy()
            dismiss()
        }

        tvUseCurrentLocation.setOnClickListener {
            requestLocationAndFillAddress()
        }

        btnPlaceOrder.setOnClickListener {
            val address = etDeliveryAddress.text?.toString()?.trim() ?: ""
            if (address.isEmpty()) {
                tilDeliveryAddress.error = "Please enter a delivery address"
                return@setOnClickListener
            }
            tilDeliveryAddress.error = null
            listener?.onOrderConfirmed(address)
            dismiss()
        }
    }

    private fun requestLocationAndFillAddress() {
        val context = context ?: return
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation || hasCoarseLocation) {
            fetchCurrentAddress()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchCurrentAddress() {
        val activity = activity ?: return
        showToast(getString(R.string.fetching_location))

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity)
        val cancellationToken = CancellationTokenSource()

        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken.token)
            .addOnSuccessListener { location ->
                if (location == null) {
                    showToast(getString(R.string.unable_to_get_location))
                    return@addOnSuccessListener
                }

                resolveAddress(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    onResolved = { addressText ->
                        if (!isAdded) return@resolveAddress

                        if (addressText.isNullOrBlank()) {
                            showToast(getString(R.string.unable_to_resolve_address))
                            return@resolveAddress
                        }

                        // Post to main thread to update UI
                        view?.post {
                            etDeliveryAddress.setText(addressText)
                            etDeliveryAddress.setSelection(addressText.length)
                            tilDeliveryAddress.error = null
                        }
                    }
                )
            }
            .addOnFailureListener {
                showToast(getString(R.string.unable_to_get_location))
            }
    }

    private fun resolveAddress(
        latitude: Double,
        longitude: Double,
        onResolved: (String?) -> Unit
    ) {
        val context = context ?: run {
            onResolved(null)
            return
        }

        if (!Geocoder.isPresent()) {
            onResolved(null)
            return
        }

        val geocoder = Geocoder(context, Locale.getDefault())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    onResolved(addresses.firstOrNull()?.getAddressLine(0))
                }

                override fun onError(errorMessage: String?) {
                    onResolved(null)
                }
            })
        } else {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            onResolved(addresses?.firstOrNull()?.getAddressLine(0))
        }
    }

    private fun showToast(message: String) {
        if (!isAdded) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun formatPrice(amount: Double): String {
        return String.format("Rs. %.2f", amount)
    }

    // ==================== ADAPTER ====================

    private class OrderItemAdapter : ListAdapter<OrderItemDisplay, OrderItemAdapter.ViewHolder>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_order_confirm, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvMedicineName: TextView = itemView.findViewById(R.id.tvMedicineName)
            private val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
            private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)

            fun bind(item: OrderItemDisplay) {
                tvMedicineName.text = item.medicineName
                tvQuantity.text = "Qty: ${item.quantity}"
                tvPrice.text = String.format("Rs. %.2f", item.totalPrice)
            }
        }

        private class DiffCallback : DiffUtil.ItemCallback<OrderItemDisplay>() {
            override fun areItemsTheSame(oldItem: OrderItemDisplay, newItem: OrderItemDisplay): Boolean {
                return oldItem.medicineId == newItem.medicineId
            }

            override fun areContentsTheSame(oldItem: OrderItemDisplay, newItem: OrderItemDisplay): Boolean {
                return oldItem == newItem
            }
        }
    }
}
