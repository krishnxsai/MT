package com.meditrack.app.ui.order

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.meditrack.app.R
import com.meditrack.app.data.model.OrderStatus
import com.meditrack.app.data.model.RefillOrder
import com.meditrack.app.databinding.ActivityOrderTrackingBinding
import com.meditrack.app.util.CallUtils
import com.meditrack.app.util.DeliveryRouteManager
import com.meditrack.app.util.ETACalculator
import com.meditrack.app.util.SmoothMarkerAnimator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

/**
 * Real-time order tracking screen showing live status updates.
 * Displays a Zomato-style progress tracker with pharmacy details.
 * Shows Google Map with live delivery tracking when order is OUT_FOR_DELIVERY.
 */
@AndroidEntryPoint
class OrderTrackingActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_ORDER_ID = "order_id"
    }

    private lateinit var binding: ActivityOrderTrackingBinding
    private val viewModel: OrderTrackingViewModel by viewModels()
    private lateinit var orderItemsAdapter: OrderItemSummaryAdapter
    private var previousStatus: String? = null
    private var currentOrder: RefillOrder? = null

    // Map-related variables
    private var googleMap: GoogleMap? = null
    private var deliveryMarker: com.google.android.gms.maps.model.Marker? = null
    private var pharmacyMarker: com.google.android.gms.maps.model.Marker? = null
    private var patientMarker: com.google.android.gms.maps.model.Marker? = null
    private lateinit var routeManager: DeliveryRouteManager
    private var lastDeliveryLocation: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get order ID from intent
        val orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: run {
            finish()
            return
        }

        // Get API key from resources
        val apiKey = getString(R.string.google_maps_api_key)
        routeManager = DeliveryRouteManager(apiKey)

        setupToolbar()
        setupRecyclerView()
        setupActionButtons()
        setupMapFragment()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        orderItemsAdapter = OrderItemSummaryAdapter()
        binding.rvOrderItems.apply {
            layoutManager = LinearLayoutManager(this@OrderTrackingActivity)
            adapter = orderItemsAdapter
        }
    }

    private fun setupActionButtons() {
        binding.btnContact.setOnClickListener {
            callPharmacyForCurrentOrder()
        }

        binding.btnSupport.setOnClickListener {
            callPharmacyForCurrentOrder()
        }
    }

    private fun setupMapFragment() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.deliveryMap)
        if (mapFragment is SupportMapFragment) {
            mapFragment.getMapAsync(this)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        // Initial map configuration
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe order data
                launch {
                    viewModel.order.collectLatest { order ->
                        if (order == null) {
                            binding.loadingOverlay.isVisible = true
                            return@collectLatest
                        }

                        binding.loadingOverlay.isVisible = false
                        currentOrder = order

                        // Update order ID in toolbar
                        binding.tvOrderId.text = "Order #${order.id.take(8)}"

                        // Update pharmacy details
                        binding.tvPharmacyName.text = order.pharmacyName.ifEmpty { "Pharmacy" }
                        binding.tvDeliveryAddress.text = formatDeliveryAddress(order)

                        // Update estimated delivery time
                        binding.tvEstimatedDelivery.text = formatEstimatedDelivery(order)

                        // Update progress tracker
                        binding.progressTracker.bindOrder(order)

                        // Update pricing
                        binding.tvTotalLabel.text = String.format("Total: ₹%.2f", order.totalAmount)

                        // Populate order items list
                        if (order.isMultiItemOrder) {
                            orderItemsAdapter.submitList(order.items)
                        } else if (order.medicineName.isNotEmpty()) {
                            orderItemsAdapter.submitList(listOf(
                                com.meditrack.app.data.model.OrderItem(
                                    medicineId = order.medicineId,
                                    medicineName = order.medicineName,
                                    medicineDosage = order.medicineDosage,
                                    quantity = order.quantity,
                                    unitPrice = order.totalAmount,
                                    totalPrice = order.totalAmount
                                )
                            ))
                        }

                        // Show/hide map based on delivery status
                        updateMapVisibility(order)

                        // Initialize map markers if visible and OUT_FOR_DELIVERY
                        if (order.status == OrderStatus.SHIPPED && googleMap != null) {
                            initializeMapMarkers(order)
                        }

                        // Show status change toast
                        val currentStatus = order.status.getDisplayLabel()
                        if (previousStatus != null && currentStatus != previousStatus) {
                            showStatusChangeToast(order.status.getDisplayLabel())
                        }
                        previousStatus = currentStatus
                    }
                }

                // Observe delivery tracking updates (real-time GPS)
                launch {
                    viewModel.deliveryTracking.collectLatest { tracking ->
                        if (tracking != null && tracking.isActive && currentOrder?.status == OrderStatus.SHIPPED) {
                            updateDeliveryMarkerOnMap(tracking)
                        }
                    }
                }

                // Observe status label
                launch {
                    viewModel.statusLabel.collectLatest { label ->
                        // Can be used to update a status display if needed
                    }
                }
            }
        }
    }

    private fun updateMapVisibility(order: RefillOrder) {
        // Show map only when order is OUT_FOR_DELIVERY (SHIPPED status in our enum)
        val shouldShowMap = order.status == OrderStatus.SHIPPED
        binding.mapContainer.isVisible = shouldShowMap

        if (!shouldShowMap) {
            // Clear markers when delivery ends
            deliveryMarker?.remove()
            pharmacyMarker?.remove()
            patientMarker?.remove()
            deliveryMarker = null
            pharmacyMarker = null
            patientMarker = null
            lastDeliveryLocation = null
            routeManager.clearRoute(googleMap ?: return)
        }
    }

    private fun initializeMapMarkers(order: RefillOrder) {
        val map = googleMap ?: return

        val pharmacyLocation = order.pharmacyLocation?.let {
            LatLng(it.latitude, it.longitude)
        }

        // Get patient delivery location when available for ETA/route rendering.
        val patientLocation = order.deliveryAddress?.let {
            // Validate coordinates are not null island (0, 0)
            if (it.latitude == 0.0 && it.longitude == 0.0) {
                null
            } else {
                LatLng(it.latitude, it.longitude)
            }
        }

        // Add pharmacy marker (green) when available.
        pharmacyMarker?.remove()
        pharmacyMarker = pharmacyLocation?.let { sourceLocation ->
            map.addMarker(
                MarkerOptions()
                    .position(sourceLocation)
                    .title("Pharmacy")
                    .snippet(order.pharmacyName)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
        }

        // Add patient destination marker (red) when destination coordinates exist.
        patientMarker?.remove()
        patientMarker = patientLocation?.let { destination ->
            map.addMarker(
                MarkerOptions()
                    .position(destination)
                    .title("Your Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }

        val initialDeliveryLocation = order.currentLocation?.let {
            LatLng(it.latitude, it.longitude)
        } ?: pharmacyLocation ?: patientLocation ?: return

        // Add placeholder delivery marker (will be updated with real tracking data)
        val marker = deliveryMarker
        if (marker == null) {
            deliveryMarker = map.addMarker(
                MarkerOptions()
                    .position(initialDeliveryLocation)
                    .title("Delivery Person")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )
        } else {
            marker.position = initialDeliveryLocation
        }
        lastDeliveryLocation = initialDeliveryLocation

        // Animate camera to show all markers
        val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.Builder()
            .include(initialDeliveryLocation)
        patientLocation?.let { boundsBuilder.include(it) }
        pharmacyLocation?.let { boundsBuilder.include(it) }

        val bounds = boundsBuilder.build()

        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))

        // Fetch route from Directions API
        if (patientLocation != null) {
            lifecycleScope.launch {
                routeManager.updateRouteWithDirections(
                    map = map,
                    deliveryLocation = initialDeliveryLocation,
                    patientLocation = patientLocation,
                    onRouteUpdate = { distanceMeters, durationSeconds ->
                        // Update ETA with route data
                        binding.tvEstimatedDelivery.text = "Delivery in ~${"%.0f".format(distanceMeters / 1000.0 / 25 * 60)} minutes"
                    },
                    onError = {
                        binding.tvEstimatedDelivery.text = "Calculating delivery time..."
                    }
                )
            }
        }
    }

    private fun updateDeliveryMarkerOnMap(tracking: com.meditrack.app.data.model.DeliveryTracking) {
        val map = googleMap ?: return
        val currentLocation = tracking.toLatLng()

        var marker = deliveryMarker
        if (marker == null) {
            marker = map.addMarker(
                MarkerOptions()
                    .position(currentLocation)
                    .title("Delivery Person")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )
            deliveryMarker = marker
        }

        if (marker == null) return

        if (lastDeliveryLocation == null) {
            // First update, just set position
            marker.position = currentLocation
            lastDeliveryLocation = currentLocation
        } else {
            // Animate marker smoothly to new position
            SmoothMarkerAnimator.animateMarkerMovement(
                marker = marker,
                fromLatLng = lastDeliveryLocation!!,
                toLatLng = currentLocation,
                durationMs = 1500,
                bearing = tracking.bearing.toFloat()
            )
            lastDeliveryLocation = currentLocation
        }

        // Update ETA
        val patientLocation = currentOrder?.deliveryAddress?.let {
            LatLng(it.latitude, it.longitude)
        }
        if (patientLocation != null) {
            val eta = ETACalculator.calculateETAHybrid(
                tracking,
                patientLocation,
                routeManager.getLastRouteDistance()
            )
            if (eta != null) {
                binding.tvEstimatedDelivery.text = "ETA: $eta"
            }
        }

        // Update route as delivery person moves
        val destLocation = currentOrder?.deliveryAddress?.let {
            LatLng(it.latitude, it.longitude)
        } ?: return

        lifecycleScope.launch {
            routeManager.updateRouteWithDirections(
                map = map,
                deliveryLocation = currentLocation,
                patientLocation = destLocation
            )
        }
    }

    private fun formatDeliveryAddress(order: RefillOrder): String {
        val address = order.deliveryAddress ?: return "No delivery address"
        val parts = mutableListOf<String>()

        if (address.fullAddress.isNotBlank()) {
            parts.add(address.fullAddress)
        }
        if (address.landmark.isNotBlank()) {
            parts.add("Near ${address.landmark}")
        }

        return parts.joinToString(", ").ifBlank { "No delivery address" }
    }

    private fun formatEstimatedDelivery(order: com.meditrack.app.data.model.RefillOrder): String {
        return if (order.estimatedDelivery != null) {
            val format = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault())
            "Estimated delivery: ${format.format(order.estimatedDelivery)}"
        } else if (order.estimatedDeliveryMinutes > 0) {
            "Delivery in ~${order.estimatedDeliveryMinutes} minutes"
        } else {
            "Calculating delivery time..."
        }
    }

    private fun showStatusChangeToast(status: String) {
        val message = "Order status: $status"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun callPharmacyForCurrentOrder() {
        val order = currentOrder
        if (order == null) {
            Toast.makeText(this, "Order details are still loading", Toast.LENGTH_SHORT).show()
            return
        }

        if (order.pharmacyId.isBlank()) {
            CallUtils.dialPhoneNumber(this, "", order.pharmacyName.ifEmpty { "Pharmacy" })
            return
        }

        // Fetch pharmacy phone using repository (asynchronously in background)
        lifecycleScope.launch {
            val result = viewModel.getPharmacyPhoneForOrder(order.id)
            when (result) {
                is com.meditrack.app.data.model.Resource.Success -> {
                    val phone = result.data.orEmpty()
                    CallUtils.dialPhoneNumber(this@OrderTrackingActivity, phone, order.pharmacyName.ifEmpty { "Pharmacy" })
                }
                is com.meditrack.app.data.model.Resource.Error -> {
                    Toast.makeText(this@OrderTrackingActivity, "Failed to load pharmacy contact", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        routeManager.clearRoute(googleMap ?: return)
    }
}
