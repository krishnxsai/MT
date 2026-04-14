package com.meditrack.app.ui.order.compose

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.meditrack.app.data.model.DeliveryTracking
import com.meditrack.app.data.model.OrderItem
import com.meditrack.app.data.model.OrderStatus
import com.meditrack.app.data.model.OrderStatusEntry
import com.meditrack.app.data.model.RefillOrder
import com.meditrack.app.ui.order.OrderTrackingViewModel
import com.meditrack.app.util.CallUtils
import com.meditrack.app.util.DeliveryRouteManager
import com.meditrack.app.util.ETACalculator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val TrackingGreen = Color(0xFF119D76)
private val TrackingBlue = Color(0xFF2E90FA)
private val TrackingGray = Color(0xFFD0D5DD)
private val TrackingGrayText = Color(0xFF667085)
private val TrackingCardTint = Color(0xFFF2F7F4)
private val TrackingRed = Color(0xFFD92D20)

enum class StepStatus { COMPLETED, ACTIVE, PENDING }

data class OrderStep(
    val title: String,
    val subtitle: String?,
    val time: String,
    val status: StepStatus
)

@AndroidEntryPoint
class OrderTrackingComposeActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ORDER_ID = OrderTrackingViewModel.EXTRA_ORDER_ID
    }

    private val viewModel: OrderTrackingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val orderId = intent.getStringExtra(EXTRA_ORDER_ID)
        if (orderId.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                val order by viewModel.order.collectAsStateWithLifecycle()
                val tracking by viewModel.deliveryTracking.collectAsStateWithLifecycle()
                val apiKey = getString(com.meditrack.app.R.string.google_maps_api_key)

                Surface {
                    OrderTrackingScreen(
                        order = order,
                        tracking = tracking,
                        bindToRealtimeData = true,
                        mapsApiKey = apiKey,
                        onBackClick = { finish() },
                        onSupportClick = { callPharmacy(order) },
                        onContactClick = { callPharmacy(order) }
                    )
                }
            }
        }
    }

    private fun callPharmacy(order: RefillOrder?) {
        val currentOrder = order
        if (currentOrder == null) {
            Toast.makeText(this, "Order details are still loading", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            when (val result = viewModel.getPharmacyPhoneForOrder(currentOrder.id)) {
                is com.meditrack.app.data.model.Resource.Success -> {
                    CallUtils.dialPhoneNumber(
                        this@OrderTrackingComposeActivity,
                        result.data.orEmpty(),
                        currentOrder.pharmacyName.ifEmpty { "Pharmacy" }
                    )
                }

                is com.meditrack.app.data.model.Resource.Error -> {
                    Toast.makeText(
                        this@OrderTrackingComposeActivity,
                        "Failed to load pharmacy contact",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderTrackingScreen(
    order: RefillOrder? = null,
    tracking: DeliveryTracking? = null,
    bindToRealtimeData: Boolean = false,
    mapsApiKey: String = "",
    onBackClick: () -> Unit = {},
    onSupportClick: () -> Unit = {},
    onContactClick: () -> Unit = {},
    simulateRealtimeUpdates: Boolean = true,
    simulatePaymentFailure: Boolean = false
) {
    var demoLoading by remember { mutableStateOf(true) }
    var demoPaymentFailed by remember { mutableStateOf(false) }
    var demoActiveStepIndex by remember { mutableIntStateOf(2) }
    val routeManager = remember(mapsApiKey) { DeliveryRouteManager(mapsApiKey) }
    var liveRouteEtaText by remember(bindToRealtimeData, order?.id) { mutableStateOf<String?>(null) }

    val showLoading = if (bindToRealtimeData) order == null else demoLoading
    val paymentFailed = remember(order, bindToRealtimeData, demoPaymentFailed) {
        if (bindToRealtimeData) {
            isPaymentFailure(order)
        } else {
            demoPaymentFailed
        }
    }

    val steps = remember(order, bindToRealtimeData, demoActiveStepIndex, paymentFailed) {
        if (bindToRealtimeData) {
            buildOrderStepsFromOrder(order = order, paymentFailed = paymentFailed)
        } else {
            buildOrderSteps(activeStepIndex = demoActiveStepIndex, paymentFailed = paymentFailed)
        }
    }
    val currentActiveIndex = steps.indexOfFirst { it.status == StepStatus.ACTIVE }
        .let { if (it >= 0) it else steps.lastIndex }
        .coerceAtLeast(0)
    val completedCount = steps.count { it.status == StepStatus.COMPLETED }
    val progressSteps = if (steps.isEmpty()) 1 else steps.size
    val activeBonus = if (!paymentFailed && steps.any { it.status == StepStatus.ACTIVE }) 1 else 0
    val progressPercent = remember(steps, paymentFailed) {
        ((completedCount + activeBonus).coerceAtMost(progressSteps).toFloat() / progressSteps) * 100f
    }
    val progress by animateFloatAsState(
        targetValue = progressPercent / 100f,
        animationSpec = tween(durationMillis = 550),
        label = "progress-animation"
    )

    val orderIdText = remember(order, bindToRealtimeData) {
        if (bindToRealtimeData && order != null) {
            "Order #${order.id.take(8)}"
        } else {
            "Order #SZAA7pPw"
        }
    }

    val pharmacyNameText = remember(order, bindToRealtimeData) {
        if (bindToRealtimeData) {
            order?.pharmacyName?.takeIf { it.isNotBlank() } ?: "Pharmacy"
        } else {
            "Lakshmi Pharmacy"
        }
    }

    val addressText = remember(order, bindToRealtimeData) {
        if (bindToRealtimeData) {
            formatDeliveryAddress(order)
        } else {
            "26-12-212, Mini Bypass, Rajula Complex, Bank Colony, Nellore,\nAndhra Pradesh 524004, India"
        }
    }

    val estimatedDeliveryText = remember(order, tracking, bindToRealtimeData, liveRouteEtaText) {
        if (bindToRealtimeData) {
            formatEstimatedDelivery(order, tracking, liveRouteEtaText)
        } else {
            "Estimated delivery: Apr 02, 01:17 pm"
        }
    }

    LaunchedEffect(
        bindToRealtimeData,
        mapsApiKey,
        order?.id,
        order?.status,
        order?.deliveryAddress?.latitude,
        order?.deliveryAddress?.longitude,
        tracking?.isActive,
        tracking?.latitude,
        tracking?.longitude
    ) {
        if (!bindToRealtimeData || mapsApiKey.isBlank()) {
            liveRouteEtaText = null
            return@LaunchedEffect
        }

        val currentOrder = order
        val currentTracking = tracking

        if (currentOrder?.status != OrderStatus.SHIPPED || currentTracking?.isActive != true) {
            liveRouteEtaText = null
            return@LaunchedEffect
        }

        val destination = currentOrder.deliveryAddress
            ?.takeIf { !(it.latitude == 0.0 && it.longitude == 0.0) }
            ?.let { LatLng(it.latitude, it.longitude) }

        if (destination == null) {
            liveRouteEtaText = null
            return@LaunchedEffect
        }

        val routeData = routeManager.getRouteDataForEta(
            deliveryLocation = currentTracking.toLatLng(),
            patientLocation = destination
        )

        val etaMinutes = ((routeData.durationSeconds + 59) / 60).coerceAtLeast(1)
        liveRouteEtaText = "ETA: ${etaMinutes} min"
    }

    val itemRows = remember(order, bindToRealtimeData) {
        if (bindToRealtimeData) {
            buildOrderItemRows(order)
        } else {
            listOf(OrderItemRow(name = "Azee 500 500mg", quantity = 6, price = 300.0))
        }
    }

    val totalAmount = remember(order, bindToRealtimeData, itemRows) {
        if (bindToRealtimeData && order != null) {
            order.totalAmount
        } else {
            itemRows.sumOf { it.price }
        }
    }

    LaunchedEffect(bindToRealtimeData) {
        if (!bindToRealtimeData) {
            delay(1000)
            demoLoading = false
        }
    }

    LaunchedEffect(showLoading, bindToRealtimeData, simulateRealtimeUpdates, simulatePaymentFailure, demoPaymentFailed) {
        if (bindToRealtimeData || showLoading || !simulateRealtimeUpdates || demoPaymentFailed) {
            return@LaunchedEffect
        }

        if (simulatePaymentFailure) {
            delay(3200)
            demoActiveStepIndex = 1
            demoPaymentFailed = true
            return@LaunchedEffect
        }

        while (demoActiveStepIndex < 5) {
            delay(3400)
            demoActiveStepIndex += 1
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Order Tracking") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            BottomActions(
                onSupportClick = onSupportClick,
                onContactClick = onContactClick
            )
        }
    ) { padding ->
        if (showLoading) {
            OrderTrackingLoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = orderIdText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TrackingGrayText
                    )
                }

                item {
                    OrderCard(containerColor = TrackingCardTint) {
                        Text(
                            text = pharmacyNameText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = addressText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TrackingGrayText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = estimatedDeliveryText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TrackingGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                item {
                    StatusProgressCard(
                        progress = progress,
                        progressLabel = "Order progress ${progressPercent.roundToInt()}%",
                        isError = paymentFailed
                    )
                }

                item {
                    if (bindToRealtimeData) {
                        LiveTrackingSection(
                            order = order,
                            tracking = tracking,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LiveTrackingPlaceholder(
                            isEnabled = currentActiveIndex >= 4 && !paymentFailed,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (paymentFailed) {
                    item {
                        PaymentFailedCard(
                            onRetryPayment = {
                                demoPaymentFailed = false
                                demoActiveStepIndex = 2
                            },
                            showRetryButton = !bindToRealtimeData
                        )
                    }
                }

                item {
                    Text(
                        text = "Order Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                itemsIndexed(
                    items = steps,
                    key = { _, step -> step.title }
                ) { index, step ->
                    TimelineItem(
                        step = step,
                        isFirst = index == 0,
                        isLast = index == steps.lastIndex,
                        isFailure = paymentFailed && index == 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OrderCard(
                        title = "Order Items",
                        containerColor = TrackingCardTint
                    ) {
                        itemRows.forEachIndexed { index, row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = row.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Qty: ${row.quantity}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TrackingGrayText
                                    )
                                }
                                Text(
                                    text = formatCurrency(row.price),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (index < itemRows.lastIndex) {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Total: ${formatCurrency(totalAmount)}",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.End,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineItem(
    step: OrderStep,
    isFirst: Boolean,
    isLast: Boolean,
    isFailure: Boolean,
    modifier: Modifier = Modifier
) {
    val targetColor = when {
        isFailure -> TrackingRed
        step.status == StepStatus.COMPLETED -> TrackingGreen
        step.status == StepStatus.ACTIVE -> TrackingBlue
        else -> TrackingGray
    }
    val iconTint by animateColorAsState(targetValue = targetColor, label = "timeline-icon-color")
    val titleColor by animateColorAsState(
        targetValue = when {
            step.status == StepStatus.PENDING -> TrackingGrayText
            isFailure -> TrackingRed
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "timeline-title-color"
    )
    val lineColor by animateColorAsState(
        targetValue = when {
            isFailure -> TrackingRed
            step.status == StepStatus.PENDING -> TrackingGray
            step.status == StepStatus.COMPLETED -> TrackingGreen
            else -> TrackingBlue
        },
        label = "timeline-line-color"
    )

    Row(
        modifier = modifier.animateContentSize(),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.width(42.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(18.dp)
                    .background(if (isFirst) Color.Transparent else lineColor)
            )

            Surface(
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                color = if (step.status == StepStatus.PENDING && !isFailure) {
                    Color(0xFFF2F4F7)
                } else {
                    lineColor.copy(alpha = 0.14f)
                },
                border = BorderStroke(width = 1.5.dp, color = iconTint)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = timelineIcon(step.title),
                        contentDescription = step.title,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(44.dp)
                    .background(if (isLast) Color.Transparent else lineColor)
            )
        }

        Column(
            modifier = Modifier
                .padding(start = 12.dp, top = 2.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                fontWeight = if (step.status == StepStatus.ACTIVE || isFailure) {
                    FontWeight.Bold
                } else {
                    FontWeight.Medium
                }
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = step.time,
                style = MaterialTheme.typography.bodySmall,
                color = TrackingGrayText
            )

            if (!step.subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = step.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFailure) TrackingRed else TrackingGrayText
                )
            }
        }
    }
}

@Composable
fun OrderCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
fun BottomActions(
    onSupportClick: () -> Unit,
    onContactClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BottomActionButton(
                label = "Support",
                icon = Icons.Default.Info,
                onClick = onSupportClick,
                modifier = Modifier.weight(1f)
            )
            BottomActionButton(
                label = "Contact",
                icon = Icons.Default.Phone,
                onClick = onContactClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(100),
        colors = ButtonDefaults.buttonColors(
            containerColor = TrackingGreen,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StatusProgressCard(
    progress: Float,
    progressLabel: String,
    isError: Boolean
) {
    OrderCard(containerColor = TrackingCardTint) {
        Text(
            text = progressLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isError) TrackingRed else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(100)),
            color = if (isError) TrackingRed else TrackingGreen,
            trackColor = Color(0xFFE4E7EC)
        )
    }
}

@Composable
private fun LiveTrackingSection(
    order: RefillOrder?,
    tracking: DeliveryTracking?,
    modifier: Modifier = Modifier
) {
    if (order?.status != OrderStatus.SHIPPED) {
        LiveTrackingPlaceholder(
            isEnabled = false,
            message = "Map unlocks when order reaches Out for delivery",
            modifier = modifier
        )
        return
    }

    if (tracking?.isActive != true) {
        LiveTrackingPlaceholder(
            isEnabled = false,
            message = "Waiting for courier location updates...",
            modifier = modifier
        )
        return
    }

    OrderCard(modifier = modifier, title = "Live Delivery Tracking") {
        LiveTrackingMap(
            order = order,
            tracking = tracking,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(14.dp))
        )
    }
}

@Composable
private fun LiveTrackingMap(
    order: RefillOrder,
    tracking: DeliveryTracking,
    modifier: Modifier = Modifier
) {
    val mapView = rememberMapViewWithLifecycle()
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { googleMap ->
                    googleMap.uiSettings.isMapToolbarEnabled = false
                    googleMap.uiSettings.isZoomControlsEnabled = true
                    this@LiveTrackingMap.googleMap = googleMap
                    renderTrackingMap(googleMap, order, tracking)
                }
            }
        },
        update = {
            googleMap?.let { map ->
                renderTrackingMap(map, order, tracking)
            }
        },
        modifier = modifier
    )
}

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    return mapView
}

private fun renderTrackingMap(
    map: GoogleMap,
    order: RefillOrder,
    tracking: DeliveryTracking
) {
    val deliveryLocation = tracking.toLatLng()
    val patientLocation = order.deliveryAddress
        ?.takeIf { !(it.latitude == 0.0 && it.longitude == 0.0) }
        ?.let { LatLng(it.latitude, it.longitude) }
    val pharmacyLocation = order.pharmacyLocation
        ?.let { LatLng(it.latitude, it.longitude) }

    map.clear()
    map.addMarker(
        MarkerOptions()
            .position(deliveryLocation)
            .title("Delivery Person")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
    )
    pharmacyLocation?.let {
        map.addMarker(
            MarkerOptions()
                .position(it)
                .title(order.pharmacyName.ifBlank { "Pharmacy" })
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )
    }
    patientLocation?.let {
        map.addMarker(
            MarkerOptions()
                .position(it)
                .title("Your Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
    }

    val points = listOfNotNull(deliveryLocation, patientLocation, pharmacyLocation)
    if (points.size == 1) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(deliveryLocation, 15f))
        return
    }

    val boundsBuilder = LatLngBounds.Builder()
    points.forEach { boundsBuilder.include(it) }
    val bounds = boundsBuilder.build()
    try {
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
    } catch (_: IllegalStateException) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(deliveryLocation, 15f))
    }
}

@Composable
private fun LiveTrackingPlaceholder(
    isEnabled: Boolean,
    message: String? = null,
    modifier: Modifier = Modifier
) {
    OrderCard(modifier = modifier, title = "Live Delivery Tracking") {
        val gradient = Brush.linearGradient(
            colors = if (isEnabled) {
                listOf(Color(0xFFD8F3E7), Color(0xFFEDF7F2))
            } else {
                listOf(Color(0xFFF2F4F7), Color(0xFFF9FAFB))
            }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(gradient),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Map,
                    contentDescription = "Map Placeholder",
                    tint = if (isEnabled) TrackingGreen else TrackingGrayText,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message ?: if (isEnabled) {
                        "Live map placeholder: courier location updates in real time"
                    } else {
                        "Map unlocks when order reaches Out for delivery"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TrackingGrayText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun PaymentFailedCard(
    onRetryPayment: () -> Unit,
    showRetryButton: Boolean
) {
    OrderCard(containerColor = Color(0xFFFFF1F0)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = "Payment Failed",
                tint = TrackingRed
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Payment failed",
                    style = MaterialTheme.typography.titleSmall,
                    color = TrackingRed,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Order progression is paused until payment succeeds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TrackingGrayText
                )
            }
        }

        if (showRetryButton) {
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRetryPayment,
                shape = RoundedCornerShape(100),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TrackingRed,
                    contentColor = Color.White
                )
            ) {
                Text(text = "Retry Payment")
            }
        }
    }
}

@Composable
private fun OrderTrackingLoadingState(
    modifier: Modifier = Modifier
) {
    val shimmerBrush = rememberShimmerBrush()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ShimmerBlock(
                brush = shimmerBrush,
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }

        item {
            ShimmerBlock(
                brush = shimmerBrush,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }

        item {
            ShimmerBlock(
                brush = shimmerBrush,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }

        item {
            ShimmerBlock(
                brush = shimmerBrush,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }

        items(4) {
            Row {
                ShimmerBlock(
                    brush = shimmerBrush,
                    modifier = Modifier
                        .width(42.dp)
                        .height(86.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                ShimmerBlock(
                    brush = shimmerBrush,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer-transition")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-translate"
    )

    return Brush.linearGradient(
        colors = listOf(
            Color(0xFFE9EDF1),
            Color(0xFFF7F9FB),
            Color(0xFFE9EDF1)
        ),
        start = Offset(translate - 200f, translate - 200f),
        end = Offset(translate, translate)
    )
}

@Composable
private fun ShimmerBlock(
    brush: Brush,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(brush))
}

private data class OrderItemRow(
    val name: String,
    val quantity: Int,
    val price: Double
)

private val TimelineStatusOrder = listOf(
    OrderStatus.PENDING,
    OrderStatus.CONFIRMED,
    OrderStatus.PREPARING,
    OrderStatus.READY,
    OrderStatus.SHIPPED,
    OrderStatus.DELIVERED
)

private fun buildOrderSteps(
    activeStepIndex: Int,
    paymentFailed: Boolean
): List<OrderStep> {
    val titles = listOf(
        "Waiting for confirmation",
        "Order confirmed",
        "Preparing your order",
        "Ready for pickup",
        "Out for delivery",
        "Delivered"
    )
    val times = listOf(
        "Apr 02, 12:47 pm",
        "Apr 02, 12:48 pm",
        "Apr 02, 01:05 pm",
        "Expected soon",
        "Expected soon",
        "Pending"
    )

    return titles.mapIndexed { index, title ->
        val status = when {
            paymentFailed && index == 1 -> StepStatus.ACTIVE
            paymentFailed && index > 1 -> StepStatus.PENDING
            index < activeStepIndex -> StepStatus.COMPLETED
            index == activeStepIndex -> StepStatus.ACTIVE
            else -> StepStatus.PENDING
        }

        val subtitle = when (index) {
            0 -> "Order placed at Lakshmi Pharmacy"
            1 -> if (paymentFailed) {
                "Payment failed via UPI. Retry required"
            } else {
                "Payment confirmed via UPI"
            }
            2 -> "Pharmacy is packing your medicines"
            3 -> "Medicines will be available at pickup desk"
            4 -> "Delivery partner is assigned and heading to you"
            5 -> "Package delivered successfully"
            else -> null
        }

        OrderStep(
            title = title,
            subtitle = subtitle,
            time = times[index],
            status = status
        )
    }
}

private fun buildOrderStepsFromOrder(
    order: RefillOrder?,
    paymentFailed: Boolean
): List<OrderStep> {
    if (order == null) {
        return buildOrderSteps(activeStepIndex = 2, paymentFailed = false)
    }

    val historyByStatus = buildStatusHistoryIndex(order.statusHistory)
    val currentIndex = TimelineStatusOrder.indexOf(order.status)
    val lastReachedIndex = TimelineStatusOrder.indexOfLast { status ->
        historyByStatus.containsKey(status.name)
    }

    val activeIndex = when {
        paymentFailed -> 1
        currentIndex >= 0 -> currentIndex
        lastReachedIndex >= 0 -> lastReachedIndex
        else -> 0
    }

    return TimelineStatusOrder.mapIndexed { index, status ->
        val historyEntry = historyByStatus[status.name]
        val stepStatus = when {
            paymentFailed && index == 1 -> StepStatus.ACTIVE
            paymentFailed && index > 1 -> StepStatus.PENDING
            index < activeIndex -> StepStatus.COMPLETED
            index == activeIndex -> StepStatus.ACTIVE
            else -> StepStatus.PENDING
        }

        val time = historyEntry?.changedAt?.let(::formatTimelineTime)
            ?: if (index <= activeIndex) "Updated" else "Pending"

        val subtitle = historyEntry?.note?.takeIf { it.isNotBlank() }
            ?: defaultStepSubtitle(status = status, paymentFailed = paymentFailed)

        OrderStep(
            title = status.getDisplayLabel(),
            subtitle = subtitle,
            time = time,
            status = stepStatus
        )
    }
}

private fun buildStatusHistoryIndex(history: List<OrderStatusEntry>): Map<String, OrderStatusEntry> {
    return history
        .groupBy { it.status.trim().uppercase(Locale.getDefault()) }
        .mapValues { (_, entries) ->
            entries.maxByOrNull { it.changedAt?.time ?: Long.MIN_VALUE } ?: entries.last()
        }
}

private fun defaultStepSubtitle(status: OrderStatus, paymentFailed: Boolean): String? {
    return when (status) {
        OrderStatus.PENDING -> "Order placed at Lakshmi Pharmacy"
        OrderStatus.CONFIRMED -> {
            if (paymentFailed) {
                "Payment failed via UPI. Retry required"
            } else {
                "Payment confirmed via UPI"
            }
        }

        OrderStatus.PREPARING -> "Pharmacy is packing your medicines"
        OrderStatus.READY -> "Medicines will be available at pickup desk"
        OrderStatus.SHIPPED -> "Delivery partner is assigned and heading to you"
        OrderStatus.DELIVERED -> "Package delivered successfully"
        else -> null
    }
}

private fun isPaymentFailure(order: RefillOrder?): Boolean {
    if (order == null) {
        return false
    }

    val text = buildString {
        append(order.cancelReason)
        append(' ')
        append(order.notes)
        append(' ')
        order.statusHistory.forEach { entry ->
            append(entry.note)
            append(' ')
        }
    }.lowercase(Locale.getDefault())

    val mentionsPayment = text.contains("payment") || text.contains("upi") || text.contains("razorpay")
    val mentionsFailure = text.contains("fail") || text.contains("declin") || text.contains("timeout")
    val terminalOrder = order.status == OrderStatus.CANCELLED || order.status == OrderStatus.RETURNED

    return terminalOrder && mentionsPayment && mentionsFailure
}

private fun formatDeliveryAddress(order: RefillOrder?): String {
    if (order == null) {
        return "Address unavailable"
    }

    val address = order.deliveryAddress ?: return "Address unavailable"
    val fullAddress = address.fullAddress.ifBlank { "Address unavailable" }

    return if (address.landmark.isNotBlank()) {
        "$fullAddress\nNear ${address.landmark}"
    } else {
        fullAddress
    }
}

private fun formatEstimatedDelivery(
    order: RefillOrder?,
    tracking: DeliveryTracking?,
    liveRouteEtaText: String? = null
): String {
    if (order == null) {
        return "Estimated delivery: calculating..."
    }

    order.estimatedDelivery?.let { deliveryDate ->
        val format = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        return "Estimated delivery: ${format.format(deliveryDate)}"
    }

    if (order.status == OrderStatus.SHIPPED && tracking?.isActive == true) {
        if (!liveRouteEtaText.isNullOrBlank()) {
            return liveRouteEtaText
        }

        val destination = order.deliveryAddress
            ?.takeIf { !(it.latitude == 0.0 && it.longitude == 0.0) }
            ?.let { LatLng(it.latitude, it.longitude) }

        val eta = ETACalculator.calculateETAHybrid(
            deliveryTracking = tracking,
            patientLocation = destination
        )
        if (eta != null) {
            return "ETA: $eta"
        }

        return "Estimated delivery: courier is on the way"
    }

    if (order.estimatedDeliveryMinutes > 0) {
        return "Estimated delivery in ~${order.estimatedDeliveryMinutes} min"
    }

    return "Estimated delivery: calculating..."
}

private fun buildOrderItemRows(order: RefillOrder?): List<OrderItemRow> {
    if (order == null) {
        return emptyList()
    }

    val rows = if (order.isMultiItemOrder) {
        order.items.map { item ->
            val title = buildString {
                append(item.medicineName.ifBlank { "Medicine" })
                if (item.medicineDosage.isNotBlank()) {
                    append(" ")
                    append(item.medicineDosage)
                }
            }

            val linePrice = when {
                item.totalPrice > 0 -> item.totalPrice
                item.unitPrice > 0 && item.quantity > 0 -> item.unitPrice * item.quantity
                else -> 0.0
            }

            OrderItemRow(
                name = title,
                quantity = item.quantity.coerceAtLeast(1),
                price = linePrice
            )
        }
    } else {
        listOf(buildLegacySingleItem(order))
    }

    return rows.ifEmpty {
        listOf(OrderItemRow(name = "Medicine", quantity = 1, price = order.totalAmount))
    }
}

private fun buildLegacySingleItem(order: RefillOrder): OrderItemRow {
    val title = buildString {
        append(order.medicineName.ifBlank { "Medicine" })
        if (order.medicineDosage.isNotBlank()) {
            append(" ")
            append(order.medicineDosage)
        }
    }

    val price = when {
        order.totalAmount > 0 -> order.totalAmount
        order.subtotal > 0 -> order.subtotal
        else -> 0.0
    }

    return OrderItemRow(
        name = title,
        quantity = order.quantity.coerceAtLeast(1),
        price = price
    )
}

private fun formatTimelineTime(date: Date?): String {
    if (date == null) {
        return "Updated"
    }

    return runCatching {
        SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(date)
    }.getOrDefault("Updated")
}

private fun formatCurrency(amount: Double): String {
    return String.format(Locale.getDefault(), "₹%.2f", amount)
}

private fun timelineIcon(title: String): ImageVector {
    return when (title) {
        "Waiting for confirmation" -> Icons.Outlined.Schedule
        "Order confirmed" -> Icons.Outlined.Verified
        "Preparing your order" -> Icons.Outlined.Edit
        "Ready for pickup" -> Icons.Outlined.Inventory2
        "Out for delivery" -> Icons.Outlined.LocalShipping
        "Delivered" -> Icons.Outlined.CheckCircle
        else -> Icons.Outlined.Schedule
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OrderTrackingScreenPreview() {
    MaterialTheme {
        OrderTrackingScreen(
            simulateRealtimeUpdates = false,
            simulatePaymentFailure = false
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OrderTrackingScreenPaymentFailedPreview() {
    MaterialTheme {
        OrderTrackingScreen(
            simulateRealtimeUpdates = false,
            simulatePaymentFailure = true
        )
    }
}
