package com.meditrack.app.ui.pharmacy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meditrack.app.ui.auth.LoginActivity
import com.meditrack.app.ui.profile.ProfileActivity
import com.meditrack.app.data.model.RefillOrder
import com.meditrack.app.data.model.OrderStatus as DataOrderStatus
import com.meditrack.app.ui.pharmacy.compose.OrderStatus
import com.meditrack.app.ui.pharmacy.compose.InventoryViewModel
import com.meditrack.app.ui.pharmacy.compose.PharmacyDashboardEvent
import com.meditrack.app.ui.pharmacy.compose.PharmacyDashboardScreen
import com.meditrack.app.ui.pharmacy.compose.PharmacyDashboardUiState
import com.meditrack.app.ui.pharmacy.compose.PharmacyViewModel
import com.meditrack.app.util.CallUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PharmacyDashboardActivity : ComponentActivity() {

    // Kept for backward compatibility with legacy pharmacy fragments.
    val dashboardViewModel: PharmacyDashboardViewModel by viewModels()

    private val viewModel: PharmacyViewModel by viewModels()
    private val inventoryViewModel: InventoryViewModel by viewModels()

    private var pendingShipmentOrderId: String? = null

    private val foregroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (!granted) {
            pendingShipmentOrderId = null
            Toast.makeText(
                this,
                "Location permission is required before marking order Out for Delivery",
                Toast.LENGTH_LONG
            ).show()
            return@registerForActivityResult
        }

        continueBackgroundPermissionFlow()
    }

    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || !requiresBackgroundLocationPermission()) {
            proceedPendingShipmentOrder()
            return@registerForActivityResult
        }

        handleBackgroundPermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeEvents()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val inventoryState by inventoryViewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(state.pharmacyId) {
                inventoryViewModel.bindPharmacy(state.pharmacyId)
            }

            MaterialTheme {
                Surface {
                    PharmacyDashboardScreen(
                        state = state,
                        inventoryState = inventoryState,
                        onRefresh = {
                            viewModel.refresh()
                            inventoryViewModel.refresh()
                        },
                        onTabSelected = viewModel::onTabSelected,
                        onSearchQueryChanged = viewModel::onSearchQueryChanged,
                        onOrderExpandedToggle = viewModel::onToggleOrderExpanded,
                        onAdvanceOrderStatus = { orderId ->
                            handleAdvanceOrderAction(state, orderId)
                        },
                        onRejectOrder = viewModel::rejectOrder,
                        onCallPatient = { order ->
                            if (order.patient.phone.isBlank()) {
                                Toast.makeText(this, "Patient phone number unavailable", Toast.LENGTH_LONG).show()
                            } else {
                                CallUtils.dialPhoneNumber(this, order.patient.phone, order.patient.name)
                            }
                        },
                        onInventorySearchQueryChanged = inventoryViewModel::onSearchQueryChanged,
                        onInventorySortChanged = inventoryViewModel::onSortOptionSelected,
                        onAddMedicine = inventoryViewModel::addMedicine,
                        onUpdateMedicine = inventoryViewModel::updateMedicine,
                        onDeleteMedicine = inventoryViewModel::deleteMedicine,
                        onAdjustStock = inventoryViewModel::adjustStock,
                        onProfileClick = ::openProfile,
                        onSignOut = ::signOut
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
        inventoryViewModel.refresh()

        if (pendingShipmentOrderId != null &&
            hasForegroundLocationPermission() &&
            (!requiresBackgroundLocationPermission() || hasBackgroundLocationPermission())
        ) {
            proceedPendingShipmentOrder()
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.events.collectLatest { event ->
                        when (event) {
                            is PharmacyDashboardEvent.ShowMessage -> {
                                Toast.makeText(this@PharmacyDashboardActivity, event.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                launch {
                    inventoryViewModel.events.collectLatest { message ->
                        Toast.makeText(this@PharmacyDashboardActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun handleAdvanceOrderAction(state: PharmacyDashboardUiState, orderId: String) {
        val order = state.activeOrders.firstOrNull { it.id == orderId } ?: return

        if (order.status == OrderStatus.READY) {
            pendingShipmentOrderId = orderId
            ensureDeliveryTrackingPermissions()
            return
        }

        viewModel.advanceOrderStatus(orderId)
    }

    private fun ensureDeliveryTrackingPermissions() {
        if (!hasForegroundLocationPermission()) {
            requestForegroundLocationPermissions()
            return
        }

        continueBackgroundPermissionFlow()
    }

    private fun continueBackgroundPermissionFlow() {
        if (!requiresBackgroundLocationPermission() || hasBackgroundLocationPermission()) {
            proceedPendingShipmentOrder()
            return
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            showBackgroundLocationRationale()
            return
        }

        backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    private fun requestForegroundLocationPermissions() {
        if (
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            AlertDialog.Builder(this)
                .setTitle("Location Permission Required")
                .setMessage("Foreground location is required to publish live delivery tracking to patients.")
                .setPositiveButton("Continue") { _, _ ->
                    foregroundLocationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
                .setNegativeButton("Not Now") { _, _ ->
                    pendingShipmentOrderId = null
                    Toast.makeText(
                        this,
                        "Cannot mark Out for Delivery without location permission",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .show()
            return
        }

        foregroundLocationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun showBackgroundLocationRationale() {
        AlertDialog.Builder(this)
            .setTitle("Allow All-Time Location")
            .setMessage("Background location keeps tracking active when the app is minimized or screen is locked.")
            .setPositiveButton("Allow") { _, _ ->
                backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setNegativeButton("Cancel") { _, _ ->
                pendingShipmentOrderId = null
                Toast.makeText(
                    this,
                    "Background location is required for reliable delivery tracking",
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }

    private fun handleBackgroundPermissionDenied() {
        val canAskAgain = shouldShowRequestPermissionRationale(
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )

        if (canAskAgain) {
            Toast.makeText(
                this,
                "Background location permission was denied",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Enable Background Location")
            .setMessage("Background location is permanently denied. Open app settings and choose 'Allow all the time' to continue.")
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel") { _, _ ->
                pendingShipmentOrderId = null
            }
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun proceedPendingShipmentOrder() {
        val orderId = pendingShipmentOrderId ?: return
        pendingShipmentOrderId = null
        viewModel.advanceOrderStatus(orderId)
    }

    private fun hasForegroundLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        if (!requiresBackgroundLocationPermission()) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requiresBackgroundLocationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    private fun openProfile() {
        val pharmacyId = viewModel.uiState.value.pharmacyId
        if (pharmacyId.isNotBlank()) {
            startActivity(
                Intent(this, PharmacyProfileSetupActivity::class.java)
                    .putExtra("pharmacyId", pharmacyId)
            )
        } else {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun signOut() {
        viewModel.signOut()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    // Legacy callbacks retained so old fragment classes continue to compile.
    fun handleAcceptOrder(order: RefillOrder) {
        if (order.status == DataOrderStatus.READY) {
            pendingShipmentOrderId = order.id
            ensureDeliveryTrackingPermissions()
            return
        }

        viewModel.advanceOrderStatus(order.id)
    }

    fun handleRejectOrder(order: RefillOrder) {
        viewModel.rejectOrder(order.id)
    }

    fun handleCallPatient(order: RefillOrder) {
        val fallbackPhone = order.deliveryAddress?.contactPhone.orEmpty()
        val cachedPhone = viewModel.uiState.value.activeOrders
            .firstOrNull { it.id == order.id }
            ?.patient
            ?.phone
            .orEmpty()

        val phoneToDial = if (cachedPhone.isNotBlank()) cachedPhone else fallbackPhone
        val displayName = viewModel.uiState.value.activeOrders
            .firstOrNull { it.id == order.id }
            ?.patient
            ?.name
            ?: "Patient"

        if (phoneToDial.isBlank()) {
            Toast.makeText(this, "Patient phone number unavailable", Toast.LENGTH_LONG).show()
            return
        }

        CallUtils.dialPhoneNumber(this, phoneToDial, displayName)
    }
}
