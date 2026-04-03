package com.meditrack.app.ui.pharmacy.compose

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val DashboardBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFF4F6FB),
        Color(0xFFF7F9FC),
        Color(0xFFFFFFFF)
    )
)

private val PendingColor = Color(0xFFF59E0B)
private val ConfirmedColor = Color(0xFF0EA5E9)
private val PreparingColor = Color(0xFF2563EB)
private val ReadyColor = Color(0xFF7C3AED)
private val OutForDeliveryColor = Color(0xFF0F766E)
private val DeliveredColor = Color(0xFF16A34A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PharmacyDashboardScreen(
    state: PharmacyDashboardUiState,
    inventoryState: InventoryUiState,
    onRefresh: () -> Unit,
    onTabSelected: (DashboardTab) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onOrderExpandedToggle: (String) -> Unit,
    onAdvanceOrderStatus: (String) -> Unit,
    onRejectOrder: (String) -> Unit,
    onCallPatient: (Order) -> Unit,
    onInventorySearchQueryChanged: (String) -> Unit,
    onInventorySortChanged: (InventorySortOption) -> Unit,
    onAddMedicine: (InventoryMedicineDraft) -> Unit,
    onUpdateMedicine: (InventoryMedicineDraft) -> Unit,
    onDeleteMedicine: (String) -> Unit,
    onAdjustStock: (String, Int) -> Unit,
    onProfileClick: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.pharmacyName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Live pharmacy operations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh dashboard"
                        )
                    }
                    IconButton(onClick = onProfileClick) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Open pharmacy profile"
                        )
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DashboardBackground)
                .padding(innerPadding)
        ) {
            if (state.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.errorMessage != null) {
                ErrorBanner(
                    message = state.errorMessage,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            StatsRow(
                pendingCount = state.pendingCount,
                preparingCount = state.preparingCount,
                readyCount = state.readyCount,
                deliveredCount = state.deliveredCount,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            DashboardTabRow(
                selectedTab = state.selectedTab,
                onTabSelected = onTabSelected,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (state.isLoading) {
                DashboardLoadingState(modifier = Modifier.fillMaxSize())
                return@Column
            }

            when (state.selectedTab) {
                DashboardTab.ORDERS -> {
                    OrdersTab(
                        searchQuery = state.searchQuery,
                        orders = state.activeOrders,
                        expandedOrderIds = state.expandedOrderIds,
                        onSearchQueryChanged = onSearchQueryChanged,
                        onOrderExpandedToggle = onOrderExpandedToggle,
                        onAdvanceOrderStatus = onAdvanceOrderStatus,
                        onRejectOrder = onRejectOrder,
                        onCallPatient = onCallPatient,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                DashboardTab.INVENTORY -> {
                    InventoryScreen(
                        state = inventoryState,
                        onSearchQueryChanged = onInventorySearchQueryChanged,
                        onSortOptionChanged = onInventorySortChanged,
                        onAddMedicine = onAddMedicine,
                        onUpdateMedicine = onUpdateMedicine,
                        onDeleteMedicine = onDeleteMedicine,
                        onAdjustStock = onAdjustStock,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                DashboardTab.ANALYTICS -> {
                    AnalyticsScreen(
                        analytics = state.analytics,
                        forecast = state.forecast,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardTabRow(
    selectedTab: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = remember {
        listOf(
            DashboardTab.ORDERS to Pair("Orders", Icons.Default.Schedule),
            DashboardTab.INVENTORY to Pair("Inventory", Icons.Default.Inventory2),
            DashboardTab.ANALYTICS to Pair("Analytics", Icons.Default.QueryStats)
        )
    }

    ScrollableTabRow(
        selectedTabIndex = tabs.indexOfFirst { it.first == selectedTab },
        edgePadding = 16.dp,
        containerColor = Color.Transparent,
        modifier = modifier
    ) {
        tabs.forEach { (tab, details) ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = { Text(details.first, fontWeight = FontWeight.SemiBold) },
                icon = {
                    Icon(
                        imageVector = details.second,
                        contentDescription = details.first
                    )
                }
            )
        }
    }
}

@Composable
private fun OrdersTab(
    searchQuery: String,
    orders: List<Order>,
    expandedOrderIds: Set<String>,
    onSearchQueryChanged: (String) -> Unit,
    onOrderExpandedToggle: (String) -> Unit,
    onAdvanceOrderStatus: (String) -> Unit,
    onRejectOrder: (String) -> Unit,
    onCallPatient: (Order) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                },
                label = { Text("Search by patient, medicine, or order ID") }
            )
        }

        if (orders.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No active orders",
                    subtitle = "New prescriptions and refill requests will appear here.",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            items(orders, key = { it.id }) { order ->
                OrderCard(
                    order = order,
                    expanded = expandedOrderIds.contains(order.id),
                    onToggleExpanded = { onOrderExpandedToggle(order.id) },
                    onAdvanceStatus = { onAdvanceOrderStatus(order.id) },
                    onReject = { onRejectOrder(order.id) },
                    onCallPatient = { onCallPatient(order) }
                )
            }
        }
    }
}

@Composable
fun StatsRow(
    pendingCount: Int,
    preparingCount: Int,
    readyCount: Int,
    deliveredCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            title = "Pending",
            value = pendingCount.toString(),
            accent = PendingColor,
            modifier = Modifier.widthIn(min = 80.dp)
        )
        StatCard(
            title = "Preparing",
            value = preparingCount.toString(),
            accent = PreparingColor,
            modifier = Modifier.widthIn(min = 80.dp)
        )
        StatCard(
            title = "Ready",
            value = readyCount.toString(),
            accent = ReadyColor,
            modifier = Modifier.widthIn(min = 80.dp)
        )
        StatCard(
            title = "Delivered",
            value = deliveredCount.toString(),
            accent = DeliveredColor,
            modifier = Modifier.widthIn(min = 80.dp)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun OrderCard(
    order: Order,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onAdvanceStatus: () -> Unit,
    onReject: () -> Unit,
    onCallPatient: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryActionLabel = primaryActionLabel(order.status)
    val canReject = order.status == OrderStatus.PENDING || order.status == OrderStatus.CONFIRMED
    val pillShape = RoundedCornerShape(100)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.patient.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Order #${order.id.takeLast(6).uppercase(Locale.ROOT)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (order.patient.phone.isNotBlank()) order.patient.phone else "Phone unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatusBadge(order.status)
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                order.items.take(2).forEach { item ->
                    Text(
                        text = "${item.displayName} x${item.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (order.items.size > 2) {
                    Text(
                        text = "+${order.items.size - 2} more",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTimestamp(order.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatCurrency(order.totalAmount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.wrapContentWidth()
                )
            }

            OrderDetailsExpandable(
                items = order.items,
                expanded = expanded,
                onToggleExpanded = onToggleExpanded
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCallPatient,
                    shape = pillShape,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                ) {
                    Icon(imageVector = Icons.Default.Call, contentDescription = "Call patient")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call")
                }

                if (canReject) {
                    OutlinedButton(
                        onClick = onReject,
                        shape = pillShape,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Text("Reject")
                    }
                }

                if (primaryActionLabel != null) {
                    Button(
                        onClick = onAdvanceStatus,
                        shape = pillShape,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(primaryActionLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun OrderDetailsExpandable(
    items: List<OrderItem>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpanded() }
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Items (${items.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse items" else "Expand items"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.displayName} x${item.quantity}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatCurrency(item.price),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End
                            )
                        }
                        if (index < items.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryScreen(
    state: InventoryUiState,
    onSearchQueryChanged: (String) -> Unit,
    onSortOptionChanged: (InventorySortOption) -> Unit,
    onAddMedicine: (InventoryMedicineDraft) -> Unit,
    onUpdateMedicine: (InventoryMedicineDraft) -> Unit,
    onDeleteMedicine: (String) -> Unit,
    onAdjustStock: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by rememberSaveable { mutableStateOf(false) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var editingDraft by remember { mutableStateOf<InventoryMedicineDraft?>(null) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search inventory")
                    },
                    label = { Text("Search medicine") }
                )

                Box {
                    OutlinedButton(
                        onClick = { showSortMenu = true },
                        shape = RoundedCornerShape(100)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sort")
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        InventorySortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.label,
                                        fontWeight = if (option == state.sortOption) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    showSortMenu = false
                                    onSortOptionChanged(option)
                                }
                            )
                        }
                    }
                }
            }

            if (state.errorMessage != null) {
                ErrorBanner(
                    message = state.errorMessage,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            if (state.isSubmitting) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            when {
                state.isLoading -> {
                    InventoryLoadingState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 12.dp)
                    )
                }

                state.filteredItems.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        EmptyStateCard(
                            title = "Inventory is empty",
                            subtitle = "Add your first medicine to start managing stock."
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 10.dp),
                        contentPadding = PaddingValues(bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.filteredItems, key = { it.id }) { medicine ->
                            InventoryMedicineCard(
                                medicine = medicine,
                                onEdit = {
                                    editingDraft = InventoryMedicineDraft(
                                        id = medicine.id,
                                        medicineName = medicine.medicineName,
                                        dosage = medicine.dosage,
                                        genericName = medicine.genericName,
                                        manufacturer = medicine.manufacturer,
                                        unitPrice = medicine.unitPrice,
                                        stockQuantity = medicine.stockQuantity,
                                        lowStockThreshold = medicine.lowStockThreshold,
                                        unit = medicine.unit
                                    )
                                    showDialog = true
                                },
                                onDelete = { onDeleteMedicine(medicine.id) },
                                onDecrease = { onAdjustStock(medicine.id, -1) },
                                onIncrease = { onAdjustStock(medicine.id, 1) }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingDraft = null
                showDialog = true
            },
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add medicine")
        }
    }

    if (showDialog) {
        AddMedicineDialog(
            initialDraft = editingDraft,
            isSaving = state.isSubmitting,
            onDismiss = {
                showDialog = false
                editingDraft = null
            },
            onSave = { draft ->
                if (draft.id.isBlank()) {
                    onAddMedicine(draft)
                } else {
                    onUpdateMedicine(draft)
                }
                showDialog = false
                editingDraft = null
            }
        )
    }
}

@Composable
private fun InventoryMedicineCard(
    medicine: InventoryMedicine,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    val (statusText, statusColor) = when (medicine.stockStatus) {
        InventoryStockStatus.OUT_OF_STOCK -> "Out of stock" to Color(0xFFDC2626)
        InventoryStockStatus.LOW_STOCK -> "Low stock" to Color(0xFFF59E0B)
        InventoryStockStatus.HEALTHY -> "Healthy" to Color(0xFF16A34A)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medicine.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (medicine.genericName.isNotBlank()) {
                        Text(
                            text = medicine.genericName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = formatCurrency(medicine.unitPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AssistChip(
                    onClick = {},
                    label = { Text(statusText) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = statusColor.copy(alpha = 0.14f),
                        labelColor = statusColor
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onDecrease,
                        shape = RoundedCornerShape(100),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease stock")
                    }
                    Text(
                        text = "${medicine.stockQuantity}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = onIncrease,
                        shape = RoundedCornerShape(100),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Increase stock")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Edit medicine")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete medicine")
                    }
                }
            }

            Text(
                text = "Low stock threshold: ${medicine.lowStockThreshold}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AddMedicineDialog(
    initialDraft: InventoryMedicineDraft?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (InventoryMedicineDraft) -> Unit
) {
    var medicineName by rememberSaveable(initialDraft?.id) {
        mutableStateOf(initialDraft?.medicineName.orEmpty())
    }
    var dosage by rememberSaveable(initialDraft?.id) {
        mutableStateOf(initialDraft?.dosage.orEmpty())
    }
    var genericName by rememberSaveable(initialDraft?.id) {
        mutableStateOf(initialDraft?.genericName.orEmpty())
    }
    var manufacturer by rememberSaveable(initialDraft?.id) {
        mutableStateOf(initialDraft?.manufacturer.orEmpty())
    }
    var priceText by rememberSaveable(initialDraft?.id) {
        mutableStateOf(
            if (initialDraft == null) "" else initialDraft.unitPrice.toString()
        )
    }
    var stockText by rememberSaveable(initialDraft?.id) {
        mutableStateOf(
            if (initialDraft == null) "" else initialDraft.stockQuantity.toString()
        )
    }
    var thresholdText by rememberSaveable(initialDraft?.id) {
        mutableStateOf(
            if (initialDraft == null) "10" else initialDraft.lowStockThreshold.toString()
        )
    }

    val isEdit = initialDraft != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEdit) "Edit medicine" else "Add medicine")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = medicineName,
                    onValueChange = { medicineName = it },
                    label = { Text("Medicine name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Strength (e.g. 500 mg)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = genericName,
                    onValueChange = { genericName = it },
                    label = { Text("Generic name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = manufacturer,
                    onValueChange = { manufacturer = it },
                    label = { Text("Manufacturer") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Price") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = stockText,
                    onValueChange = { stockText = it },
                    label = { Text("Stock") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { thresholdText = it },
                    label = { Text("Low stock threshold") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedPrice = priceText.toDoubleOrNull()
                    val parsedStock = stockText.toIntOrNull()
                    val parsedThreshold = thresholdText.toIntOrNull()

                    if (
                        medicineName.trim().isBlank() ||
                        parsedPrice == null ||
                        parsedStock == null ||
                        parsedThreshold == null
                    ) {
                        return@Button
                    }

                    onSave(
                        InventoryMedicineDraft(
                            id = initialDraft?.id.orEmpty(),
                            medicineName = medicineName.trim(),
                            dosage = dosage.trim(),
                            genericName = genericName.trim(),
                            manufacturer = manufacturer.trim(),
                            unitPrice = parsedPrice,
                            stockQuantity = parsedStock,
                            lowStockThreshold = parsedThreshold,
                            unit = initialDraft?.unit ?: "tablets"
                        )
                    )
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(100)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (isEdit) "Save" else "Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AnalyticsScreen(
    analytics: AnalyticsData,
    forecast: ForecastData,
    modifier: Modifier = Modifier
) {
    val lineSeries = if (forecast.recentOrders.isNotEmpty()) {
        forecast.recentOrders
    } else {
        forecast.projectedOrders
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SummaryCards(analytics = analytics)
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Orders Trend (Last 7 Days)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (lineSeries.isEmpty()) {
                        Text(
                            text = "Not enough data",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        OrdersLineChart(data = lineSeries)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Top Medicines", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (analytics.topMedicines.isEmpty()) {
                        Text(
                            text = "No sales data yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        TopMedicinesBarChart(analytics.topMedicines)
                        analytics.topMedicines.forEachIndexed { index, medicine ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}. ${medicine.medicineName}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${medicine.unitsSold} units",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Demand Forecast",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = forecast.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "7-day moving average: ${forecast.movingAverage.roundToInt()} orders/day",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (forecast.hasEnoughData && forecast.projectedOrders.isNotEmpty()) {
                        Text(
                            text = "Next 7 days: ${forecast.projectedOrders.joinToString(", ")}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCards(analytics: AnalyticsData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryCard(
            title = "Orders",
            value = analytics.totalOrdersToday.toString(),
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            title = "Revenue",
            value = formatCurrency(analytics.totalRevenue),
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            title = "Completion",
            value = "${(analytics.completionRate * 100).roundToInt()}%",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OrdersLineChart(data: List<Int>) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                axisRight.isEnabled = false
                setDrawGridBackground(false)
                setScaleEnabled(false)
                setPinchZoom(false)
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                axisLeft.axisMinimum = 0f
                axisLeft.setDrawGridLines(true)
            }
        },
        update = { chart ->
            val entries = data.mapIndexed { index, value ->
                Entry(index.toFloat(), value.toFloat())
            }

            val dataSet = LineDataSet(entries, "Orders").apply {
                color = AndroidColor.parseColor("#2563EB")
                lineWidth = 2.5f
                setDrawValues(false)
                setDrawFilled(true)
                fillColor = AndroidColor.parseColor("#DBEAFE")
                fillAlpha = 180
                circleRadius = 3.2f
                setCircleColor(AndroidColor.parseColor("#1D4ED8"))
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }

            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "D${value.toInt() + 1}"
                }
            }

            chart.data = LineData(dataSet)
            chart.notifyDataSetChanged()
            chart.invalidate()
        }
    )
}

@Composable
private fun TopMedicinesBarChart(topMedicines: List<TopMedicineStat>) {
    val chartItems = topMedicines.take(5)

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        factory = { context ->
            BarChart(context).apply {
                description.isEnabled = false
                axisRight.isEnabled = false
                setDrawGridBackground(false)
                setScaleEnabled(false)
                legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
                legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                axisLeft.axisMinimum = 0f
            }
        },
        update = { chart ->
            val entries = chartItems.mapIndexed { index, stat ->
                BarEntry(index.toFloat(), stat.unitsSold.toFloat())
            }

            val dataSet = BarDataSet(entries, "Units sold").apply {
                color = AndroidColor.parseColor("#7C3AED")
                valueTextSize = 11f
            }

            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt()
                    val label = chartItems.getOrNull(index)?.medicineName.orEmpty()
                    return if (label.length <= 8) label else "${label.take(8)}.."
                }
            }

            chart.data = BarData(dataSet).apply {
                barWidth = 0.6f
            }
            chart.notifyDataSetChanged()
            chart.invalidate()
        }
    )
}

@Composable
private fun StatusBadge(status: OrderStatus) {
    val (label, color) = when (status) {
        OrderStatus.PENDING -> "Pending" to PendingColor
        OrderStatus.CONFIRMED -> "Confirmed" to ConfirmedColor
        OrderStatus.PREPARING -> "Preparing" to PreparingColor
        OrderStatus.READY -> "Ready" to ReadyColor
        OrderStatus.OUT_FOR_DELIVERY -> "Dispatched" to OutForDeliveryColor
        OrderStatus.DELIVERED -> "Delivered" to DeliveredColor
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = when (status) {
                OrderStatus.PENDING -> Icons.Default.Schedule
                OrderStatus.CONFIRMED -> Icons.Default.CheckCircle
                OrderStatus.PREPARING -> Icons.Default.Inventory2
                OrderStatus.READY -> Icons.Default.CheckCircle
                OrderStatus.OUT_FOR_DELIVERY -> Icons.Default.LocalShipping
                OrderStatus.DELIVERED -> Icons.Default.CheckCircle
            },
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun DashboardLoadingState(modifier: Modifier = Modifier) {
    val shimmerBrush = rememberShimmerBrush()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(5) {
            ShimmerBlock(
                brush = shimmerBrush,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }
    }
}

@Composable
private fun InventoryLoadingState(modifier: Modifier = Modifier) {
    val shimmerBrush = rememberShimmerBrush()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(4) {
            ShimmerBlock(
                brush = shimmerBrush,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }
    }
}

@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "dashboard-shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dashboard-shimmer-translate"
    )

    return Brush.linearGradient(
        colors = listOf(
            Color(0xFFE7EBF0),
            Color(0xFFF4F7FB),
            Color(0xFFE7EBF0)
        ),
        start = Offset(translate - 220f, translate - 220f),
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

private fun primaryActionLabel(status: OrderStatus): String? = when (status) {
    OrderStatus.PENDING -> "Confirm"
    OrderStatus.CONFIRMED -> "Start Prep"
    OrderStatus.PREPARING -> "Mark Ready"
    OrderStatus.READY -> "Dispatch"
    OrderStatus.OUT_FOR_DELIVERY -> "Mark Delivered"
    OrderStatus.DELIVERED -> null
}

private fun formatCurrency(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return formatter.format(amount)
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Just now"

    val now = Calendar.getInstance()
    val event = Calendar.getInstance().apply { timeInMillis = timestamp }

    val sameDay =
        now.get(Calendar.YEAR) == event.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == event.get(Calendar.DAY_OF_YEAR)

    if (sameDay) {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return "Today, ${timeFormat.format(Date(timestamp))}"
    }

    now.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday =
        now.get(Calendar.YEAR) == event.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == event.get(Calendar.DAY_OF_YEAR)

    val fullFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return if (yesterday) {
        "Yesterday, ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))}"
    } else {
        fullFormat.format(Date(timestamp))
    }
}
