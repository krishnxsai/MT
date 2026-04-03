package com.meditrack.app.ui.pharmacy.compose

/**
 * Dashboard patient model used for UI rendering.
 */
data class Patient(
    val id: String,
    val name: String,
    val phone: String
)

/**
 * Dashboard order item model used for UI rendering.
 */
data class OrderItem(
    val medicineName: String,
    val dosage: String = "",
    val quantity: Int,
    val price: Double
) {
    val displayName: String
        get() = if (dosage.isBlank()) medicineName else "$medicineName $dosage"
}

/**
 * Dashboard order model with patient relation resolved.
 */
data class Order(
    val id: String,
    val patient: Patient,
    val items: List<OrderItem>,
    val totalAmount: Double,
    val status: OrderStatus,
    val timestamp: Long
)

enum class OrderStatus {
    PENDING,
    CONFIRMED,
    PREPARING,
    READY,
    OUT_FOR_DELIVERY,
    DELIVERED
}

enum class DashboardTab {
    ORDERS,
    INVENTORY,
    ANALYTICS
}

data class TopMedicineStat(
    val medicineName: String,
    val unitsSold: Int
)

data class AnalyticsData(
    val totalOrdersToday: Int = 0,
    val totalRevenue: Double = 0.0,
    val completionRate: Double = 0.0,
    val topMedicines: List<TopMedicineStat> = emptyList()
)

data class ForecastData(
    val hasEnoughData: Boolean = false,
    val movingAverage: Double = 0.0,
    val recentOrders: List<Int> = emptyList(),
    val projectedOrders: List<Int> = emptyList(),
    val message: String = "Not enough data"
)

data class InventorySummary(
    val medicineName: String,
    val stockQuantity: Int,
    val lowStockThreshold: Int
)

enum class InventorySortOption(val label: String) {
    LOW_STOCK_FIRST("Low Stock First"),
    NAME("Name"),
    STOCK_ASC("Stock Low to High"),
    STOCK_DESC("Stock High to Low"),
    PRICE_ASC("Price Low to High"),
    PRICE_DESC("Price High to Low")
}

enum class InventoryStockStatus {
    OUT_OF_STOCK,
    LOW_STOCK,
    HEALTHY
}

data class InventoryMedicine(
    val id: String,
    val medicineName: String,
    val dosage: String = "",
    val genericName: String = "",
    val manufacturer: String = "",
    val unitPrice: Double,
    val stockQuantity: Int,
    val lowStockThreshold: Int,
    val unit: String = "tablets"
) {
    val displayName: String
        get() = if (dosage.isBlank()) medicineName else "$medicineName $dosage"

    val stockStatus: InventoryStockStatus
        get() = when {
            stockQuantity <= 0 -> InventoryStockStatus.OUT_OF_STOCK
            stockQuantity <= lowStockThreshold -> InventoryStockStatus.LOW_STOCK
            else -> InventoryStockStatus.HEALTHY
        }
}

data class InventoryMedicineDraft(
    val id: String = "",
    val medicineName: String,
    val dosage: String = "",
    val genericName: String = "",
    val manufacturer: String = "",
    val unitPrice: Double,
    val stockQuantity: Int,
    val lowStockThreshold: Int,
    val unit: String = "tablets"
)

data class InventoryUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val pharmacyId: String = "",
    val searchQuery: String = "",
    val sortOption: InventorySortOption = InventorySortOption.LOW_STOCK_FIRST,
    val items: List<InventoryMedicine> = emptyList(),
    val filteredItems: List<InventoryMedicine> = emptyList()
)

data class PharmacyDashboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val pharmacyId: String = "",
    val pharmacyName: String = "Pharmacy Dashboard",
    val selectedTab: DashboardTab = DashboardTab.ORDERS,
    val searchQuery: String = "",
    val activeOrders: List<Order> = emptyList(),
    val deliveredOrders: List<Order> = emptyList(),
    val expandedOrderIds: Set<String> = emptySet(),
    val pendingCount: Int = 0,
    val preparingCount: Int = 0,
    val readyCount: Int = 0,
    val deliveredCount: Int = 0,
    val notificationCount: Int = 0,
    val analytics: AnalyticsData = AnalyticsData(),
    val forecast: ForecastData = ForecastData(),
    val inventorySummary: List<InventorySummary> = emptyList()
)

sealed interface PharmacyDashboardEvent {
    data class ShowMessage(val message: String) : PharmacyDashboardEvent
}
