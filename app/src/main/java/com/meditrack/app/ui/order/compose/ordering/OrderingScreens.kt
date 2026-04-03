package com.meditrack.app.ui.order.compose.ordering

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meditrack.app.data.model.Pharmacy
import java.util.Locale

private val AppGreen = Color(0xFF059669)
private val UrgentOrange = Color(0xFFD97706)
private val OutRed = Color(0xFFDC2626)
private const val LOW_STOCK_LIMIT = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineListScreen(
    uiState: OrderingUiState,
    snackbarHostState: SnackbarHostState,
    onSearchChanged: (String) -> Unit,
    onAddToCart: (Medicine) -> Unit,
    onIncreaseQuantity: (String) -> Unit,
    onDecreaseQuantity: (String) -> Unit,
    onRemoveFromCart: (String) -> Unit,
    onSelectPharmacy: (String) -> Unit,
    onDeliveryAddressChanged: (String) -> Unit,
    onUseGps: () -> Unit,
    onPlaceOrder: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    var showCartSheet by rememberSaveable { mutableStateOf(false) }
    val quantityMap = remember(uiState.cartItems) {
        uiState.cartItems.associate { it.medicine.id to it.quantity }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Medicine Orders",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showCartSheet = true }) {
                        BadgedBox(
                            badge = {
                                if (uiState.cartItems.isNotEmpty()) {
                                    Badge {
                                        Text(uiState.cartItems.size.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.ShoppingCart, contentDescription = "Cart")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (uiState.cartItems.isNotEmpty()) {
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${uiState.cartItems.sumOf { it.quantity }} items",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatPrice(uiState.total),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppGreen
                            )
                        }
                        Button(
                            onClick = { showCartSheet = true },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
                        ) {
                            Text("Review Order")
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChanged,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search medicines") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            if (uiState.lowStockMedicines.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "Low Stock",
                        suffix = "(${uiState.lowStockMedicines.size})"
                    )
                }
                item {
                    LowStockSection(
                        medicines = uiState.lowStockMedicines,
                        quantityMap = quantityMap,
                        onAddToCart = onAddToCart,
                        onIncreaseQuantity = onIncreaseQuantity,
                        onDecreaseQuantity = onDecreaseQuantity
                    )
                }
            }

            item {
                SectionTitle(title = "Nearby Pharmacies")
            }

            item {
                PharmacySelectorRow(
                    pharmacies = uiState.pharmacies,
                    selectedPharmacy = uiState.selectedPharmacy,
                    onSelectPharmacy = onSelectPharmacy
                )
            }

            item {
                SectionTitle(
                    title = "All Medicines",
                    suffix = "(${uiState.medicines.size})"
                )
            }

            if (uiState.medicines.isEmpty() && !uiState.isLoading) {
                item {
                    EmptyState(
                        title = "No medicines found",
                        subtitle = "Try a different keyword or refresh the list.",
                        actionLabel = "Refresh",
                        onAction = onRefresh
                    )
                }
            }

            items(uiState.medicines, key = { it.id }) { medicine ->
                MedicineCard(
                    medicine = medicine,
                    quantity = quantityMap[medicine.id] ?: 0,
                    onAdd = { onAddToCart(medicine) },
                    onIncrease = { onIncreaseQuantity(medicine.id) },
                    onDecrease = { onDecreaseQuantity(medicine.id) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }

    if (showCartSheet) {
        CartBottomSheet(
            uiState = uiState,
            onDismiss = { showCartSheet = false },
            onIncreaseQuantity = onIncreaseQuantity,
            onDecreaseQuantity = onDecreaseQuantity,
            onRemoveFromCart = onRemoveFromCart,
            onDeliveryAddressChanged = onDeliveryAddressChanged,
            onUseGps = onUseGps,
            onChangePharmacy = { showCartSheet = false },
            onPlaceOrder = onPlaceOrder
        )
    }
}

@Composable
fun MedicineCard(
    medicine: Medicine,
    quantity: Int,
    onAdd: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stockText: String
    val stockColor: Color

    when {
        medicine.stock <= 0 -> {
            stockText = "Out of stock"
            stockColor = OutRed
        }

        medicine.stock <= LOW_STOCK_LIMIT -> {
            stockText = "Only ${medicine.stock} left"
            stockColor = UrgentOrange
        }

        else -> {
            stockText = "In stock"
            stockColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F7F4)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(AppGreen.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalPharmacy,
                    contentDescription = null,
                    tint = AppGreen
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = medicine.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = medicine.mg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stockText,
                    style = MaterialTheme.typography.labelMedium,
                    color = stockColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = formatPrice(medicine.price),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                AnimatedContent(
                    targetState = quantity > 0,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "add-to-stepper"
                ) { hasItem ->
                    if (hasItem) {
                        QuantityStepper(
                            quantity = quantity,
                            onIncrease = onIncrease,
                            onDecrease = onDecrease,
                            increaseEnabled = quantity < medicine.stock,
                            decreaseEnabled = quantity > 0
                        )
                    } else {
                        OutlinedButton(
                            onClick = onAdd,
                            enabled = medicine.stock > 0,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, AppGreen)
                        ) {
                            Text("ADD", color = AppGreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuantityStepper(
    quantity: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    increaseEnabled: Boolean,
    decreaseEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color(0xFFE4F3ED)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onDecrease,
                enabled = decreaseEnabled,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = AppGreen)
            }
            Text(
                text = quantity.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppGreen,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            IconButton(
                onClick = onIncrease,
                enabled = increaseEnabled,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase", tint = AppGreen)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartBottomSheet(
    uiState: OrderingUiState,
    onDismiss: () -> Unit,
    onIncreaseQuantity: (String) -> Unit,
    onDecreaseQuantity: (String) -> Unit,
    onRemoveFromCart: (String) -> Unit,
    onDeliveryAddressChanged: (String) -> Unit,
    onUseGps: () -> Unit,
    onChangePharmacy: () -> Unit,
    onPlaceOrder: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Confirm Order",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Order Items",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "(${uiState.cartItems.size} items)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.cartItems.isEmpty()) {
                EmptyState(
                    title = "Cart is empty",
                    subtitle = "Add medicines to continue",
                    actionLabel = "Close",
                    onAction = onDismiss
                )
            } else {
                uiState.cartItems.forEach { item ->
                    OutlinedCard(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.medicine.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = item.medicine.mg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                                QuantityStepper(
                                    quantity = item.quantity,
                                    onIncrease = { onIncreaseQuantity(item.medicine.id) },
                                    onDecrease = {
                                        if (item.quantity == 1) {
                                            onRemoveFromCart(item.medicine.id)
                                        } else {
                                            onDecreaseQuantity(item.medicine.id)
                                        }
                                    },
                                    increaseEnabled = item.quantity < item.medicine.stock,
                                    decreaseEnabled = item.quantity > 0
                                )
                            }
                            Text(
                                text = formatPrice(item.medicine.price * item.quantity),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Delivery Address",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onUseGps) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = AppGreen)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Use GPS", color = AppGreen)
                }
            }

            OutlinedTextField(
                value = uiState.deliveryAddress,
                onValueChange = onDeliveryAddressChanged,
                placeholder = { Text("Enter your delivery address") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedCard(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.selectedPharmacy?.name ?: "Select a pharmacy",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = uiState.selectedPharmacy?.let {
                                if (it.isDeliveryAvailable) "Delivery available" else "Pickup only"
                            } ?: "No pharmacy selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onChangePharmacy) {
                        Text("Change", color = AppGreen)
                    }
                }
            }

            BillSummary(
                subtotal = uiState.subtotal,
                deliveryFee = uiState.deliveryFee,
                total = uiState.total
            )

            if (uiState.prescriptionRequiredCount > 0) {
                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFFFF8E8)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Prescription required for ${uiState.prescriptionRequiredCount} item",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UrgentOrange,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = onPlaceOrder,
                enabled = !uiState.isPlacingOrder && uiState.cartItems.isNotEmpty(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(if (uiState.isPlacingOrder) "Placing Order..." else "Place Order")
            }
        }
    }
}

@Composable
private fun BillSummary(subtotal: Double, deliveryFee: Double, total: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Bill Summary",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        SummaryRow(title = "Subtotal", value = formatPrice(subtotal))
        SummaryRow(title = "Delivery Fee", value = formatPrice(deliveryFee))
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        SummaryRow(
            title = "Total",
            value = formatPrice(total),
            valueColor = AppGreen,
            emphasized = true
        )
    }
}

@Composable
private fun SummaryRow(
    title: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    emphasized: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun LowStockSection(
    medicines: List<Medicine>,
    quantityMap: Map<String, Int>,
    onAddToCart: (Medicine) -> Unit,
    onIncreaseQuantity: (String) -> Unit,
    onDecreaseQuantity: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        items(medicines, key = { it.id }) { medicine ->
            val quantity = quantityMap[medicine.id] ?: 0
            val tag = when {
                medicine.stock <= 0 -> StockTag.OUT
                medicine.stock <= LOW_STOCK_LIMIT -> StockTag.URGENT
                else -> null
            }

            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .width(220.dp)
                    .animateContentSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (tag != null) {
                        Text(
                            text = if (tag == StockTag.OUT) "OUT" else "URGENT",
                            color = if (tag == StockTag.OUT) OutRed else UrgentOrange,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .background(
                                    color = if (tag == StockTag.OUT) OutRed.copy(alpha = 0.12f) else UrgentOrange.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Text(
                        text = medicine.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = medicine.mg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatPrice(medicine.price),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (quantity <= 0) {
                        OutlinedButton(
                            onClick = { onAddToCart(medicine) },
                            enabled = medicine.stock > 0,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ADD", color = AppGreen)
                        }
                    } else {
                        QuantityStepper(
                            quantity = quantity,
                            onIncrease = { onIncreaseQuantity(medicine.id) },
                            onDecrease = { onDecreaseQuantity(medicine.id) },
                            increaseEnabled = quantity < medicine.stock,
                            decreaseEnabled = quantity > 0,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PharmacySelectorRow(
    pharmacies: List<Pharmacy>,
    selectedPharmacy: Pharmacy?,
    onSelectPharmacy: (String) -> Unit
) {
    if (pharmacies.isEmpty()) {
        Text(
            text = "No nearby pharmacy available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        return
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        items(pharmacies, key = { it.id }) { pharmacy ->
            FilterChip(
                selected = selectedPharmacy?.id == pharmacy.id,
                onClick = { onSelectPharmacy(pharmacy.id) },
                label = {
                    Text(
                        text = pharmacy.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onAction, shape = RoundedCornerShape(50)) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, suffix: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (suffix.isNotBlank()) {
            Text(
                text = suffix,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatPrice(amount: Double): String {
    return String.format(Locale.US, "Rs. %.2f", amount)
}
