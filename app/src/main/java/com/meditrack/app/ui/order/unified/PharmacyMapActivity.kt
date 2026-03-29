package com.meditrack.app.ui.order.unified

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.meditrack.app.R
import com.meditrack.app.databinding.ActivityPharmacyMapBinding
import com.meditrack.app.data.model.Pharmacy
import com.meditrack.app.util.CallUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Map-based pharmacy discovery and selection screen.
 * Shows nearby pharmacies on a Google Map with custom markers.
 */
@AndroidEntryPoint
class PharmacyMapActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_SELECTED_PHARMACY_ID = "selected_pharmacy_id"
        const val RESULT_PHARMACY_SELECTED = 100
        private const val DEFAULT_ZOOM = 14f
        private const val SELECTED_ZOOM = 16f
    }

    private lateinit var binding: ActivityPharmacyMapBinding
    private val viewModel: PharmacyMapViewModel by viewModels()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null
    private var userLocationMarker: Marker? = null
    private val pharmacyMarkers = mutableMapOf<String, Marker>()

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var pharmacyAdapter: PharmacyMapListAdapter

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            enableMyLocation()
            getCurrentLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPharmacyMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupToolbar()
        setupBottomSheet()
        setupRecyclerView()
        setupChips()
        setupClickListeners()
        observeViewModel()

        // Initialize map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // Handle state changes if needed
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Adjust FAB position based on slide
                binding.fabMyLocation.translationY = -slideOffset * 100
            }
        })
    }

    private fun setupRecyclerView() {
        pharmacyAdapter = PharmacyMapListAdapter(
            onPharmacyClick = { pharmacy ->
                viewModel.selectPharmacy(pharmacy)
                focusOnPharmacy(pharmacy)
            },
            onSelectClick = { pharmacy ->
                viewModel.selectPharmacy(pharmacy)
                focusOnPharmacy(pharmacy)
            },
            onCallClick = { pharmacy ->
                CallUtils.dialPhoneNumber(this, pharmacy.phone, pharmacy.name)
            }
        )

        binding.rvPharmacies.apply {
            adapter = pharmacyAdapter
            layoutManager = LinearLayoutManager(this@PharmacyMapActivity)
        }
    }

    private fun setupChips() {
        binding.sortChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val sortType = when {
                checkedIds.contains(R.id.chipNearby) -> PharmacyMapViewModel.SortType.DISTANCE
                checkedIds.contains(R.id.chipRating) -> PharmacyMapViewModel.SortType.RATING
                checkedIds.contains(R.id.chipFastDelivery) -> PharmacyMapViewModel.SortType.DELIVERY_TIME
                checkedIds.contains(R.id.chipOpen) -> PharmacyMapViewModel.SortType.OPEN_NOW
                else -> PharmacyMapViewModel.SortType.DISTANCE
            }
            viewModel.setSortType(sortType)
        }
    }

    private fun setupClickListeners() {
        binding.fabMyLocation.setOnClickListener {
            getCurrentLocation()
        }

        binding.btnConfirm.setOnClickListener {
            confirmSelection()
        }

        binding.btnFilter.setOnClickListener {
            // TODO: Show filter dialog
            Snackbar.make(binding.root, "Filter options coming soon", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Pharmacies list
                launch {
                    viewModel.pharmacies.collectLatest { pharmacies ->
                        updatePharmacyList(pharmacies)
                        updateMapMarkers(pharmacies)
                    }
                }

                // Selected pharmacy
                launch {
                    viewModel.selectedPharmacy.collectLatest { pharmacy ->
                        updateSelectedPharmacyUI(pharmacy)
                    }
                }

                // Loading state
                launch {
                    viewModel.isLoading.collectLatest { isLoading ->
                        binding.loadingOverlay.isVisible = isLoading
                    }
                }

                // User location
                launch {
                    viewModel.userLocation.collectLatest { location ->
                        location?.let { updateUserLocationOnMap(it) }
                    }
                }
            }
        }

        viewModel.error.observe(this) { message ->
            message?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    // ==================== MAP CALLBACKS ====================

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Configure map UI
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isMapToolbarEnabled = false
            isMyLocationButtonEnabled = false
        }

        // Set map style (optional - for custom styling)
        // map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style))

        // Marker click listener
        map.setOnMarkerClickListener { marker ->
            val pharmacyId = marker.tag as? String
            pharmacyId?.let { id ->
                viewModel.selectPharmacyById(id)
                // Animate camera to marker
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, SELECTED_ZOOM))
            }
            true
        }

        // Map click listener (deselect)
        map.setOnMapClickListener {
            viewModel.clearSelection()
        }

        // Request location permission and load data
        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED -> {
                enableMyLocation()
                getCurrentLocation()
            }
            else -> {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun enableMyLocation() {
        googleMap?.let { map ->
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                map.isMyLocationEnabled = true
            }
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val cancellationToken = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken.token)
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    viewModel.updateUserLocation(it)
                    // Move camera to user location
                    googleMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(it.latitude, it.longitude),
                            DEFAULT_ZOOM
                        )
                    )
                }
            }
    }

    // ==================== MAP UPDATES ====================

    private fun updateUserLocationOnMap(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)

        // Update or create user marker (if not using blue dot)
        userLocationMarker?.remove()
        // Note: We're using map's built-in blue dot via isMyLocationEnabled
    }

    private fun updateMapMarkers(pharmacies: List<PharmacyMapViewModel.PharmacyWithDistance>) {
        val map = googleMap ?: return

        // Clear existing markers
        pharmacyMarkers.values.forEach { it.remove() }
        pharmacyMarkers.clear()

        val selectedId = viewModel.selectedPharmacy.value?.id

        // Add markers for each pharmacy
        pharmacies.forEach { pharmacyWithDistance ->
            val pharmacy = pharmacyWithDistance.pharmacy
            val position = LatLng(pharmacy.latitude, pharmacy.longitude)

            val markerIcon = if (pharmacy.id == selectedId) {
                createSelectedMarkerIcon()
            } else {
                createPharmacyMarkerIcon(pharmacy.rating)
            }

            val marker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(pharmacy.name)
                    .snippet("${String.format("%.1f", pharmacy.rating)} · ${pharmacyWithDistance.distanceText}")
                    .icon(markerIcon)
            )

            marker?.tag = pharmacy.id
            marker?.let { pharmacyMarkers[pharmacy.id] = it }
        }

        // Fit all markers in view if no selection
        if (selectedId == null && pharmacies.isNotEmpty()) {
            val boundsBuilder = LatLngBounds.Builder()
            viewModel.userLocation.value?.let {
                boundsBuilder.include(LatLng(it.latitude, it.longitude))
            }
            pharmacies.forEach {
                boundsBuilder.include(LatLng(it.pharmacy.latitude, it.pharmacy.longitude))
            }
            try {
                val bounds = boundsBuilder.build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
            } catch (e: Exception) {
                // Not enough points
            }
        }
    }

    private fun createPharmacyMarkerIcon(rating: Double): BitmapDescriptor {
        // Create custom marker with rating
        val color = when {
            rating >= 4.5 -> R.color.success
            rating >= 4.0 -> R.color.primary
            rating >= 3.5 -> R.color.warning
            else -> R.color.text_secondary
        }

        return BitmapDescriptorFactory.defaultMarker(
            when {
                rating >= 4.5 -> BitmapDescriptorFactory.HUE_GREEN
                rating >= 4.0 -> BitmapDescriptorFactory.HUE_AZURE
                rating >= 3.5 -> BitmapDescriptorFactory.HUE_ORANGE
                else -> BitmapDescriptorFactory.HUE_RED
            }
        )
    }

    private fun createSelectedMarkerIcon(): BitmapDescriptor {
        return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
    }

    private fun focusOnPharmacy(pharmacy: Pharmacy) {
        googleMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(pharmacy.latitude, pharmacy.longitude),
                SELECTED_ZOOM
            )
        )

        // Update marker appearance
        pharmacyMarkers.forEach { (id, marker) ->
            marker.setIcon(
                if (id == pharmacy.id) createSelectedMarkerIcon()
                else createPharmacyMarkerIcon(
                    viewModel.pharmacies.value.find { it.pharmacy.id == id }?.pharmacy?.rating ?: 0.0
                )
            )
        }

        // Collapse bottom sheet to show map
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    // ==================== UI UPDATES ====================

    private fun updatePharmacyList(pharmacies: List<PharmacyMapViewModel.PharmacyWithDistance>) {
        val selectedId = viewModel.selectedPharmacy.value?.id
        pharmacyAdapter.submitList(pharmacies.map { item ->
            PharmacyMapListAdapter.PharmacyListItem(
                pharmacy = item.pharmacy,
                distanceText = item.distanceText,
                isSelected = item.pharmacy.id == selectedId
            )
        })

        binding.tvPharmacyCount.text = "${pharmacies.size} found"
    }

    private fun updateSelectedPharmacyUI(pharmacy: Pharmacy?) {
        if (pharmacy != null) {
            binding.confirmBar.isVisible = true
            binding.tvSelectedPharmacy.text = pharmacy.name
            val distance = viewModel.pharmacies.value.find { it.pharmacy.id == pharmacy.id }
            binding.tvSelectedDistance.text = "${distance?.distanceText ?: "Unknown"} · ${pharmacy.estimatedDeliveryTime.ifEmpty { "30 min" }} delivery"

            // Update list selection
            val currentList = pharmacyAdapter.currentList.map { item ->
                item.copy(isSelected = item.pharmacy.id == pharmacy.id)
            }
            pharmacyAdapter.submitList(currentList)

            // Show marker info window
            pharmacyMarkers[pharmacy.id]?.showInfoWindow()
        } else {
            binding.confirmBar.isVisible = false

            // Clear list selection
            val currentList = pharmacyAdapter.currentList.map { item ->
                item.copy(isSelected = false)
            }
            pharmacyAdapter.submitList(currentList)
        }
    }

    private fun confirmSelection() {
        val pharmacy = viewModel.selectedPharmacy.value
        if (pharmacy != null) {
            val resultIntent = Intent().apply {
                putExtra(EXTRA_SELECTED_PHARMACY_ID, pharmacy.id)
            }
            setResult(RESULT_PHARMACY_SELECTED, resultIntent)
            finish()
        }
    }
}
