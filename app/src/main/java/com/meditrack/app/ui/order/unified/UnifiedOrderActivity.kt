package com.meditrack.app.ui.order.unified

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.util.Log
import android.widget.Toast
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.meditrack.app.R
import com.meditrack.app.databinding.ActivityUnifiedOrderBinding
import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.Pharmacy
import com.meditrack.app.ui.payment.PaymentActivity
import com.meditrack.app.util.FeatureFlags
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Unified medicine ordering screen.
 * Combines low-stock alerts, medicine search, and pharmacy selection
 * into a Zomato/Swiggy-like ordering experience.
 */
@AndroidEntryPoint
class UnifiedOrderActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_MAP = 1001
        private const val REQUEST_CODE_PAYMENT = 1002
        private const val TAG = "UnifiedOrderActivity"
    }

    private lateinit var binding: ActivityUnifiedOrderBinding
    private val viewModel: UnifiedOrderViewModel by viewModels()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentUserLocation: Location? = null
    private var currentPaymentOrderId: String? = null  // Track order ID for payment result

    private lateinit var lowStockAdapter: LowStockMedicineAdapter
    private lateinit var pharmacyAdapter: PharmacyUnifiedAdapter
    private lateinit var searchResultsAdapter: LowStockMedicineAdapter

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            getCurrentLocation()
        }
    }

    private val mapActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == PharmacyMapActivity.RESULT_PHARMACY_SELECTED) {
            val pharmacyId = result.data?.getStringExtra(PharmacyMapActivity.EXTRA_SELECTED_PHARMACY_ID)
            pharmacyId?.let { id ->
                viewModel.selectPharmacyById(id)
            }
        }
    }

    private val paymentActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            RESULT_OK -> {
                if (currentPaymentOrderId != null) {
                    val intent = Intent(this, com.meditrack.app.ui.order.OrderTrackingActivity::class.java)
                    intent.putExtra(com.meditrack.app.ui.order.OrderTrackingActivity.EXTRA_ORDER_ID, currentPaymentOrderId)
                    startActivity(intent)
                    finish()
                }
            }
            RESULT_CANCELED -> {
                Toast.makeText(this, "Payment cancelled", Toast.LENGTH_SHORT).show()
            }
            RESULT_FIRST_USER -> {
                val errorMessage = result.data?.getStringExtra("error_message") ?: "Payment failed"
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUnifiedOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupToolbar()
        setupRecyclerViews()
        setupSearch()
        setupClickListeners()
        observeViewModel()

        requestLocationPermission()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerViews() {
        // Low stock medicines (horizontal scroll)
        lowStockAdapter = LowStockMedicineAdapter(
            onAddClick = { medicine -> viewModel.addToCart(medicine, fromAlert = true) },
            onItemClick = { medicine -> showMedicineDetails(medicine) }
        )
        binding.rvLowStock.apply {
            adapter = lowStockAdapter
            layoutManager = LinearLayoutManager(this@UnifiedOrderActivity, LinearLayoutManager.HORIZONTAL, false)
        }

        // Pharmacies (vertical list)
        pharmacyAdapter = PharmacyUnifiedAdapter(
            onSelectClick = { pharmacy -> viewModel.selectPharmacy(pharmacy) },
            onItemClick = { pharmacy -> showPharmacyDetails(pharmacy) }
        )
        binding.rvPharmacies.apply {
            adapter = pharmacyAdapter
            layoutManager = LinearLayoutManager(this@UnifiedOrderActivity)
        }

        // Search results
        searchResultsAdapter = LowStockMedicineAdapter(
            onAddClick = { medicine -> viewModel.addToCart(medicine) },
            onItemClick = { medicine -> showMedicineDetails(medicine) }
        )
        binding.rvSearchResults.apply {
            adapter = searchResultsAdapter
            layoutManager = LinearLayoutManager(this@UnifiedOrderActivity)
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                viewModel.search(query)
                binding.btnClearSearch.isVisible = query.isNotEmpty()
            }
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            viewModel.clearSearch()
        }
    }

    private fun setupClickListeners() {
        binding.btnCart.setOnClickListener {
            showCartBottomSheet()
        }

        binding.tvMapView.setOnClickListener {
            viewModel.openPharmacyMap()
        }

        binding.btnProceed.setOnClickListener {
            if (viewModel.selectedPharmacy.value == null) {
                Snackbar.make(binding.root, "Please select a pharmacy", Snackbar.LENGTH_SHORT).show()
            } else {
                showOrderConfirmation()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // UI State
                launch {
                    viewModel.uiState.collectLatest { state ->
                        when (state) {
                            is UnifiedOrderViewModel.UiState.Loading -> {
                                binding.loadingOverlay.isVisible = true
                            }
                            is UnifiedOrderViewModel.UiState.Success -> {
                                binding.loadingOverlay.isVisible = false
                                updateLowStockSection(state.lowStockMedicines)
                                updatePharmacySection(state.nearbyPharmacies)
                                updateSearchResults(state.searchResults)
                            }
                            is UnifiedOrderViewModel.UiState.Error -> {
                                binding.loadingOverlay.isVisible = false
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                // Cart updates
                launch {
                    viewModel.cartItemCount.collectLatest { count ->
                        updateCartBadge(count)
                        updateCartBar(count)
                    }
                }

                // Cart total
                launch {
                    viewModel.cartTotal.collectLatest { total ->
                        binding.tvCartTotal.text = String.format("Rs. %.2f", total)
                    }
                }

                // Selected pharmacy
                launch {
                    viewModel.selectedPharmacy.collectLatest { pharmacy ->
                        updateProceedButton(pharmacy)
                    }
                }

                // Navigation events
                launch {
                    viewModel.navigationEvent.collectLatest { event ->
                        handleNavigationEvent(event)
                    }
                }

                // Loading state for order placement
                launch {
                    viewModel.isPlacingOrder.collectLatest { isPlacing ->
                        binding.loadingOverlay.isVisible = isPlacing
                    }
                }
            }
        }

        // Error handling
        viewModel.error.observe(this) { message ->
            message?.let {
                Log.e(TAG, "Error: $it")

                // Show as dialog for critical errors like inventory
                if (it.contains("Insufficient stock") || it.contains("unavailable")) {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Unable to Place Order")
                        .setMessage(it)
                        .setPositiveButton("Select Different Pharmacy") { _, _ ->
                            // Clear cart and return to home page
                            viewModel.clearCart()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                }
                viewModel.clearError()
            }
        }
    }

    private fun updateLowStockSection(items: List<UnifiedOrderViewModel.LowStockItem>) {
        val cartMedicineIds = viewModel.cart.value.map { it.medicineId }.toSet()
        lowStockAdapter.submitList(items.map { item ->
            LowStockMedicineAdapter.LowStockMedicineItem(
                medicine = item.medicine,
                urgency = item.urgency,
                daysRemaining = item.daysRemaining,
                stockPercentage = item.stockPercentage,
                statusText = item.statusText,
                isInCart = item.medicine.id in cartMedicineIds
            )
        })

        binding.tvLowStockCount.text = "(${items.size})"
        binding.tvNoLowStock.isVisible = items.isEmpty()
        binding.rvLowStock.isVisible = items.isNotEmpty()
    }

    private fun updatePharmacySection(pharmacies: List<UnifiedOrderViewModel.PharmacyWithDistance>) {
        pharmacyAdapter.submitList(pharmacies.map { item ->
            PharmacyUnifiedAdapter.PharmacyItem(
                pharmacy = item.pharmacy,
                distanceText = item.distanceText,
                isSelected = item.isSelected
            )
        })

        binding.tvNoPharmacies.isVisible = pharmacies.isEmpty()
        binding.rvPharmacies.isVisible = pharmacies.isNotEmpty()
    }

    private fun updateSearchResults(medicines: List<Medicine>) {
        val cartMedicineIds = viewModel.cart.value.map { it.medicineId }.toSet()
        searchResultsAdapter.submitList(medicines.map { medicine ->
            LowStockMedicineAdapter.LowStockMedicineItem(
                medicine = medicine,
                urgency = com.meditrack.app.data.model.RefillUrgency.NONE,
                daysRemaining = -1,
                stockPercentage = medicine.stockPercentage,
                statusText = "",
                isInCart = medicine.id in cartMedicineIds
            )
        })

        binding.rvSearchResults.isVisible = medicines.isNotEmpty()
        binding.lowStockSection.isVisible = medicines.isEmpty()
    }

    private fun updateCartBadge(count: Int) {
        binding.tvCartBadge.apply {
            text = count.toString()
            isVisible = count > 0
        }
    }

    private fun updateCartBar(count: Int) {
        binding.cartBar.isVisible = count > 0
        binding.tvCartItemCount.text = "$count item${if (count > 1) "s" else ""} in cart"
    }

    private fun updateProceedButton(pharmacy: Pharmacy?) {
        if (pharmacy != null) {
            binding.btnProceed.text = "Place Order"
            binding.btnProceed.setIconResource(R.drawable.ic_cart)
        } else {
            binding.btnProceed.text = "Select Pharmacy"
            binding.btnProceed.setIconResource(R.drawable.ic_arrow_right)
        }
    }

    private fun handleNavigationEvent(event: UnifiedOrderViewModel.NavigationEvent) {
        Log.d(TAG, "handleNavigationEvent: $event")
        when (event) {
            is UnifiedOrderViewModel.NavigationEvent.ToOrderTracking -> {
                Log.d(TAG, "Navigating to OrderTracking with orderId: ${event.orderId}")
                val intent = Intent(this, com.meditrack.app.ui.order.OrderTrackingActivity::class.java)
                intent.putExtra(com.meditrack.app.ui.order.OrderTrackingActivity.EXTRA_ORDER_ID, event.orderId)
                startActivity(intent)
                finish()
            }
            is UnifiedOrderViewModel.NavigationEvent.ToPharmacyMap -> {
                // Open PharmacyMapActivity
                Log.d(TAG, "Navigating to PharmacyMap")
                val intent = Intent(this, PharmacyMapActivity::class.java)
                mapActivityLauncher.launch(intent)
            }
            is UnifiedOrderViewModel.NavigationEvent.ToOrderConfirmation -> {
                // Order placed successfully — navigate to tracking
                Log.d(TAG, "Navigating to OrderConfirmation with orderId: ${event.orderId}")
                val intent = Intent(this, com.meditrack.app.ui.order.OrderTrackingActivity::class.java)
                intent.putExtra(com.meditrack.app.ui.order.OrderTrackingActivity.EXTRA_ORDER_ID, event.orderId)
                startActivity(intent)
                finish()
            }
            is UnifiedOrderViewModel.NavigationEvent.ToPayment -> {
                Log.d(TAG, "Navigating to Payment: orderId=${event.orderId}, amount=${event.amount}")
                // Check feature flag for payment
                if (FeatureFlags.isEnabled(FeatureFlags.PAYMENT_ENABLED)) {
                    Log.d(TAG, "Payment feature flag enabled, launching PaymentActivity")
                    launchPaymentActivity(event.orderId, event.amount)
                } else {
                    Log.d(TAG, "Payment feature flag disabled, navigating to OrderTracking instead")
                    // Fallback: auto-confirm order and go to tracking
                    val intent = Intent(this, com.meditrack.app.ui.order.OrderTrackingActivity::class.java)
                    intent.putExtra(com.meditrack.app.ui.order.OrderTrackingActivity.EXTRA_ORDER_ID, event.orderId)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    private fun launchPaymentActivity(orderId: String, amount: Double) {
        Log.d(TAG, "launchPaymentActivity: orderId=$orderId, amount=$amount")
        currentPaymentOrderId = orderId  // Store for use in onActivityResult
        lifecycleScope.launch {
            try {
                // Get current user
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    Log.e(TAG, "launchPaymentActivity: User not authenticated")
                    Toast.makeText(this@UnifiedOrderActivity, "User not authenticated", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Log.d(TAG, "launchPaymentActivity: Fetching user details for uid=${currentUser.uid}")
                // Fetch user details from Firestore
                val userDoc = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUser.uid)
                    .get()
                    .await()

                val userEmail = userDoc.getString("email") ?: currentUser.email ?: ""
                val userPhone = userDoc.getString("phone") ?: ""
                val userName = userDoc.getString("fullName") ?: currentUser.displayName ?: "User"

                Log.d(TAG, "launchPaymentActivity: Got user data - email=$userEmail, phone=$userPhone, name=$userName")

                // Create intent for PaymentActivity
                val intent = Intent(this@UnifiedOrderActivity, PaymentActivity::class.java).apply {
                    putExtra(PaymentActivity.EXTRA_ORDER_ID, orderId)
                    putExtra(PaymentActivity.EXTRA_AMOUNT, amount)
                    putExtra(PaymentActivity.EXTRA_USER_EMAIL, userEmail)
                    putExtra(PaymentActivity.EXTRA_USER_PHONE, userPhone)
                    putExtra(PaymentActivity.EXTRA_USER_NAME, userName)
                }

                Log.d(TAG, "launchPaymentActivity: Launching PaymentActivity with launcher")
                paymentActivityLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e(TAG, "launchPaymentActivity: Exception occurred", e)
                Toast.makeText(this@UnifiedOrderActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showMedicineDetails(medicine: Medicine) {
        // TODO: Show medicine details bottom sheet
    }

    private fun showPharmacyDetails(pharmacy: Pharmacy) {
        // TODO: Show pharmacy details bottom sheet
    }

    private fun showCartBottomSheet() {
        // TODO: Implement cart bottom sheet
        val cartItems = viewModel.cart.value
        if (cartItems.isEmpty()) {
            Snackbar.make(binding.root, "Your cart is empty", Snackbar.LENGTH_SHORT).show()
            return
        }
        // For now, just place order if pharmacy is selected
        if (viewModel.selectedPharmacy.value != null) {
            showOrderConfirmation()
        } else {
            Snackbar.make(binding.root, "Please select a pharmacy first", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showOrderConfirmation() {
        val pharmacy = viewModel.selectedPharmacy.value ?: return
        val cartItems = viewModel.cartWithPrices.value

        if (cartItems.isEmpty()) {
            Snackbar.make(binding.root, "Your cart is empty", Snackbar.LENGTH_SHORT).show()
            return
        }

        // Find distance text for selected pharmacy
        val uiState = viewModel.uiState.value
        val distanceText = if (uiState is UnifiedOrderViewModel.UiState.Success) {
            uiState.nearbyPharmacies.find { it.pharmacy.id == pharmacy.id }?.distanceText ?: "Unknown"
        } else {
            "Unknown"
        }

        // Build order item display list
        val orderItems = cartItems.map { item ->
            OrderConfirmationBottomSheet.OrderItemDisplay(
                medicineId = item.cartItem.medicineId,
                medicineName = item.cartItem.medicineName,
                quantity = item.cartItem.quantity,
                unitPrice = item.unitPrice,
                totalPrice = item.totalPrice,
                prescriptionRequired = item.cartItem.prescriptionRequired
            )
        }

        val subtotal = viewModel.cartSubtotal.value
        val deliveryFee = viewModel.deliveryFee.value
        val total = viewModel.cartTotal.value

        // Show bottom sheet
        val bottomSheet = OrderConfirmationBottomSheet.newInstance(
            items = orderItems,
            pharmacy = pharmacy,
            distanceText = distanceText,
            subtotal = subtotal,
            deliveryFee = deliveryFee,
            total = total
        )

        bottomSheet.setOnOrderConfirmListener(object : OrderConfirmationBottomSheet.OnOrderConfirmListener {
            override fun onOrderConfirmed(deliveryAddress: String) {
                Log.d(TAG, "onOrderConfirmed: deliveryAddress=$deliveryAddress")
                // Ensure we have valid location coordinates
                var location = currentUserLocation
                if (location == null) {
                    // Use default location as fallback
                    location = Location("fallback").apply {
                        latitude = 28.7041  // Delhi, India coordinates
                        longitude = 77.1025
                    }
                }

                Log.d(TAG, "onOrderConfirmed: Calling placeOrder with location lat=${location.latitude}, lon=${location.longitude}")
                viewModel.placeOrder(
                    deliveryAddress = com.meditrack.app.data.model.DeliveryAddress(
                        fullAddress = deliveryAddress,
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                )
            }

            override fun onChangePharmacy() {
                Log.d(TAG, "onChangePharmacy: Clearing pharmacy selection")
                viewModel.clearPharmacySelection()
            }
        })

        bottomSheet.show(supportFragmentManager, "order_confirmation")
    }

    // ==================== LOCATION ====================

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED -> {
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

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                currentUserLocation = location
                viewModel.updateUserLocation(location)
            } else {
                // Fallback: Use default location if lastLocation is null
                // This allows orders to proceed even if real location is unavailable
                val defaultLocation = Location("default").apply {
                    latitude = 28.7041  // Delhi, India coordinates
                    longitude = 77.1025
                }
                currentUserLocation = defaultLocation
                viewModel.updateUserLocation(defaultLocation)
            }
        }.addOnFailureListener {
            // On failure, use fallback location
            val defaultLocation = Location("fallback").apply {
                latitude = 28.7041  // Delhi, India coordinates
                longitude = 77.1025
            }
            currentUserLocation = defaultLocation
            viewModel.updateUserLocation(defaultLocation)
        }
    }

    // ==================== ACTIVITY RESULT ====================
    // All activity results are now handled via registerForActivityResult launchers (mapActivityLauncher, paymentActivityLauncher)
}
