package com.meditrack.app.ui.order.unified

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.meditrack.app.ui.order.compose.OrderTrackingComposeActivity
import com.meditrack.app.ui.order.compose.ordering.CartUiEvent
import com.meditrack.app.ui.order.compose.ordering.CartViewModel
import com.meditrack.app.ui.order.compose.ordering.MedicineListScreen
import com.meditrack.app.ui.payment.PaymentActivity
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class UnifiedOrderActivity : ComponentActivity() {

    private val viewModel: CartViewModel by viewModels()
    private var pendingPaymentOrderId: String? = null

    private val paymentActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val orderId = pendingPaymentOrderId ?: return@registerForActivityResult

        when (result.resultCode) {
            RESULT_OK -> {
                openOrderTracking(orderId)
                pendingPaymentOrderId = null
                finish()
            }

            RESULT_CANCELED -> {
                val reason = result.data?.getStringExtra(PaymentActivity.EXTRA_ERROR_MESSAGE)
                    ?.takeIf { it.isNotBlank() }
                    ?: "Payment cancelled"
                viewModel.handleIncompletePayment(orderId, reason)
                pendingPaymentOrderId = null
                Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
            }

            RESULT_FIRST_USER -> {
                val reason = result.data?.getStringExtra(PaymentActivity.EXTRA_ERROR_MESSAGE)
                    ?.takeIf { it.isNotBlank() }
                    ?: "Payment failed"
                viewModel.handleIncompletePayment(orderId, reason)
                pendingPaymentOrderId = null
                Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            fetchCurrentAddress()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is CartUiEvent.ShowMessage -> {
                                snackbarHostState.showSnackbar(event.message)
                            }

                            is CartUiEvent.LaunchPayment -> {
                                launchPayment(event.orderId, event.amount)
                            }

                            is CartUiEvent.LaunchTracking -> {
                                openOrderTracking(event.orderId)
                                finish()
                            }
                        }
                    }
                }

                MedicineListScreen(
                    uiState = uiState,
                    snackbarHostState = snackbarHostState,
                    onSearchChanged = viewModel::onSearchChanged,
                    onAddToCart = viewModel::addToCart,
                    onIncreaseQuantity = viewModel::increaseQuantity,
                    onDecreaseQuantity = viewModel::decreaseQuantity,
                    onRemoveFromCart = viewModel::removeFromCart,
                    onSelectPharmacy = viewModel::selectPharmacy,
                    onDeliveryAddressChanged = viewModel::onDeliveryAddressChanged,
                    onUseGps = ::requestLocationAndFillAddress,
                    onPlaceOrder = viewModel::placeOrder,
                    onRefresh = viewModel::refresh,
                    onBack = { finish() }
                )
            }
        }
    }

    private fun launchPayment(orderId: String, amount: Double) {
        pendingPaymentOrderId = orderId
        val intent = Intent(this, PaymentActivity::class.java).apply {
            putExtra(PaymentActivity.EXTRA_ORDER_ID, orderId)
            putExtra(PaymentActivity.EXTRA_AMOUNT, amount)
        }
        paymentActivityLauncher.launch(intent)
    }

    private fun openOrderTracking(orderId: String) {
        val intent = Intent(this, OrderTrackingComposeActivity::class.java).apply {
            putExtra(OrderTrackingComposeActivity.EXTRA_ORDER_ID, orderId)
        }
        startActivity(intent)
    }

    private fun requestLocationAndFillAddress() {
        val hasFineLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

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
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val cancellationToken = CancellationTokenSource()

        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken.token)
            .addOnSuccessListener { location ->
                if (location == null) {
                    Toast.makeText(this, "Unable to fetch location", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                resolveAddress(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    onResolved = { address ->
                        if (address.isNullOrBlank()) {
                            Toast.makeText(this, "Unable to resolve address", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.applyGpsAddress(address)
                        }
                    }
                )
            }
            .addOnFailureListener {
                Toast.makeText(this, "Unable to fetch location", Toast.LENGTH_SHORT).show()
            }
    }

    private fun resolveAddress(
        latitude: Double,
        longitude: Double,
        onResolved: (String?) -> Unit
    ) {
        if (!Geocoder.isPresent()) {
            onResolved(null)
            return
        }

        val geocoder = Geocoder(this, Locale.getDefault())

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
}
