package com.meditrack.app.ui.order.compose.ordering

import com.meditrack.app.data.model.InventoryItem
import com.meditrack.app.data.model.Medicine as DomainMedicine
import com.meditrack.app.data.model.Pharmacy
import com.meditrack.app.data.model.RefillOrder
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.CartRepository
import com.meditrack.app.data.repository.MedicineRepository
import com.meditrack.app.data.repository.OrderRepository
import com.meditrack.app.data.repository.PharmacyInventoryRepository
import com.meditrack.app.data.repository.PharmacyRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var medicineRepository: MedicineRepository
    private lateinit var pharmacyRepository: PharmacyRepository
    private lateinit var pharmacyInventoryRepository: PharmacyInventoryRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var cartRepository: CartRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        medicineRepository = mockk()
        pharmacyRepository = mockk()
        pharmacyInventoryRepository = mockk()
        orderRepository = mockk()
        cartRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `add and increase quantity respects stock limit`() = runTest {
        val domainMedicine = sampleDomainMedicine(
            id = "m1",
            name = "Azee 500",
            dosage = "500mg",
            stock = 2
        )

        val viewModel = createViewModel(
            medicines = listOf(domainMedicine)
        )
        advanceUntilIdle()

        val medicine = viewModel.uiState.value.medicines.first()

        viewModel.addToCart(medicine)
        viewModel.increaseQuantity(medicine.id)
        viewModel.increaseQuantity(medicine.id)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.cartItems.single().quantity)
        assertEquals(100.0, viewModel.uiState.value.subtotal, 0.001)
    }

    @Test
    fun `decrease quantity removes item at zero`() = runTest {
        val domainMedicine = sampleDomainMedicine(id = "m1", name = "Paracetamol", dosage = "650mg")

        val viewModel = createViewModel(
            medicines = listOf(domainMedicine)
        )
        advanceUntilIdle()

        val medicine = viewModel.uiState.value.medicines.first()

        viewModel.addToCart(medicine)
        advanceUntilIdle()
        viewModel.decreaseQuantity(medicine.id)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.cartItems.isEmpty())
        assertEquals(0.0, viewModel.uiState.value.subtotal, 0.001)
        assertEquals(0.0, viewModel.uiState.value.total, 0.001)
    }

    @Test
    fun `totals recompute when quantities change`() = runTest {
        val medicines = listOf(
            sampleDomainMedicine(id = "m1", name = "Azee 500", dosage = "500mg"),
            sampleDomainMedicine(id = "m2", name = "Crocin", dosage = "650mg")
        )

        val viewModel = createViewModel(
            medicines = medicines
        )
        advanceUntilIdle()

        val med1 = viewModel.uiState.value.medicines.first { it.id == "m1" }
        val med2 = viewModel.uiState.value.medicines.first { it.id == "m2" }

        viewModel.addToCart(med1)
        viewModel.addToCart(med2)
        viewModel.increaseQuantity("m1")
        advanceUntilIdle()

        assertEquals(150.0, viewModel.uiState.value.subtotal, 0.001)
        assertEquals(0.0, viewModel.uiState.value.deliveryFee, 0.001)
        assertEquals(150.0, viewModel.uiState.value.total, 0.001)

        viewModel.decreaseQuantity("m1")
        advanceUntilIdle()

        assertEquals(100.0, viewModel.uiState.value.subtotal, 0.001)
        assertEquals(100.0, viewModel.uiState.value.total, 0.001)
    }

    @Test
    fun `place order emits payment event and clears cart`() = runTest {
        val medicines = listOf(sampleDomainMedicine(id = "m1", name = "Azee 500", dosage = "500mg"))

        val orderSlot = slot<RefillOrder>()
        coEvery { orderRepository.placeOrder(capture(orderSlot)) } returns Resource.Success("order_123")

        val viewModel = createViewModel(
            medicines = medicines
        )
        advanceUntilIdle()

        val medicine = viewModel.uiState.value.medicines.first()
        viewModel.addToCart(medicine)
        viewModel.onDeliveryAddressChanged("123 Main Street, Nellore")
        advanceUntilIdle()

        val events = mutableListOf<CartUiEvent>()
        val collectJob = backgroundScope.launch {
            viewModel.events.take(1).toList(events)
        }

        viewModel.placeOrder()
        advanceUntilIdle()

        collectJob.cancel()

        val launchPayment = events.single() as CartUiEvent.LaunchPayment
        assertEquals("order_123", launchPayment.orderId)
    assertEquals(50.0, launchPayment.amount, 0.001)

    assertEquals(50.0, orderSlot.captured.subtotal, 0.001)
    assertEquals(50.0, orderSlot.captured.totalAmount, 0.001)
        assertEquals("123 Main Street, Nellore", orderSlot.captured.deliveryAddress?.fullAddress)
        assertEquals(1, orderSlot.captured.items.size)

        coVerify(exactly = 1) { orderRepository.placeOrder(any()) }
        coVerify(exactly = 1) { cartRepository.clearCart() }
        assertTrue(viewModel.uiState.value.cartItems.isEmpty())
    }

    @Test
    fun `initial load keeps all pharmacies when source has more than fifty`() = runTest {
        val medicines = listOf(sampleDomainMedicine(id = "m1", name = "Azee 500", dosage = "500mg"))
        val pharmacies = (1..75).map { index ->
            samplePharmacy(id = "p$index", name = "Pharmacy $index")
        }

        val viewModel = createViewModel(
            medicines = medicines,
            pharmacies = pharmacies
        )
        advanceUntilIdle()

        assertEquals(75, viewModel.uiState.value.pharmacies.size)
        assertEquals("p1", viewModel.uiState.value.selectedPharmacy?.id)
    }

    @Test
    fun `missing inventory maps medicine to out of stock`() = runTest {
        val domainMedicine = sampleDomainMedicine(
            id = "m1",
            name = "Azee 500",
            dosage = "500mg",
            stock = 20
        )

        val viewModel = createViewModel(
            medicines = listOf(domainMedicine),
            seedInventoryFromMedicines = false
        )
        advanceUntilIdle()

        viewModel.selectPharmacy("p1")
        advanceUntilIdle()

        val medicine = viewModel.uiState.value.medicines.first()
        assertEquals(0, medicine.stock)

        viewModel.addToCart(medicine)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.cartItems.isEmpty())
    }

    @Test
    fun `place order is blocked when selected pharmacy cannot fulfill cart`() = runTest {
        val medicineName = "Azee 500"
        val domainMedicine = sampleDomainMedicine(id = "m1", name = medicineName, dosage = "500mg", stock = 10)

        val viewModel = createViewModel(
            medicines = listOf(domainMedicine),
            inventoriesByName = mapOf(
                medicineName to sampleInventoryItem(medicineName = medicineName, stockQuantity = 5)
            ),
            seedInventoryFromMedicines = false
        )
        advanceUntilIdle()

        val medicine = viewModel.uiState.value.medicines.first()
        viewModel.addToCart(medicine)
        viewModel.onDeliveryAddressChanged("123 Main Street, Nellore")
        advanceUntilIdle()

        coEvery {
            pharmacyInventoryRepository.getInventoryItem(any(), medicineName)
        } returns Resource.Success(null)

        viewModel.placeOrder()
        advanceUntilIdle()

        coVerify(exactly = 0) { orderRepository.placeOrder(any()) }
        assertTrue(viewModel.uiState.value.cartItems.isEmpty())
    }

    @Test
    fun `cart is pruned when pharmacy refresh marks item unavailable`() = runTest {
        val medicineName = "Azee 500"
        val domainMedicine = sampleDomainMedicine(id = "m1", name = medicineName, dosage = "500mg", stock = 10)

        val viewModel = createViewModel(
            medicines = listOf(domainMedicine),
            inventoriesByName = mapOf(
                medicineName to sampleInventoryItem(medicineName = medicineName, stockQuantity = 4)
            ),
            seedInventoryFromMedicines = false
        )
        advanceUntilIdle()

        val medicine = viewModel.uiState.value.medicines.first()
        viewModel.addToCart(medicine)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.cartItems.size)

        coEvery {
            pharmacyInventoryRepository.getInventoryItem(any(), medicineName)
        } returns Resource.Success(null)

        viewModel.selectPharmacy("p1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.cartItems.isEmpty())
        assertEquals(0, viewModel.uiState.value.medicines.first().stock)
    }

    private fun createViewModel(
        medicines: List<DomainMedicine>,
        pharmacies: List<Pharmacy> = listOf(samplePharmacy(id = "p1", name = "HealthPlus")),
        inventoriesByName: Map<String, InventoryItem?> = emptyMap(),
        seedInventoryFromMedicines: Boolean = true
    ): CartViewModel {
        coEvery { medicineRepository.getMedicines() } returns Resource.Success(medicines)
        coEvery { pharmacyRepository.getPharmacies() } returns Resource.Success(pharmacies)
        coEvery { cartRepository.getCart() } returns Resource.Success(null)
        coEvery { cartRepository.saveCart(any(), any()) } returns Resource.Success(Unit)
        coEvery { cartRepository.clearCart() } returns Resource.Success(Unit)
        coEvery { orderRepository.cancelOrderWithRefund(any(), any(), any()) } returns Resource.Success(Unit)

        val seededInventory = if (seedInventoryFromMedicines) {
            medicines.associate { medicine ->
                val effectiveStock = when {
                    medicine.currentQuantity >= 0 -> medicine.currentQuantity
                    medicine.totalQuantity > 0 -> medicine.totalQuantity
                    else -> 0
                }

                medicine.name to sampleInventoryItem(
                    medicineName = medicine.name,
                    stockQuantity = effectiveStock
                )
            }
        } else {
            emptyMap()
        }

        val resolvedInventory = seededInventory + inventoriesByName

        coEvery {
            pharmacyInventoryRepository.getInventoryItem(any(), any())
        } answers {
            val medicineName = arg<String>(1)
            Resource.Success(resolvedInventory[medicineName])
        }

        return CartViewModel(
            medicineRepository = medicineRepository,
            pharmacyRepository = pharmacyRepository,
            pharmacyInventoryRepository = pharmacyInventoryRepository,
            orderRepository = orderRepository,
            cartRepository = cartRepository
        )
    }

    private fun sampleDomainMedicine(
        id: String,
        name: String,
        dosage: String,
        stock: Int = 20
    ): DomainMedicine {
        return DomainMedicine(
            id = id,
            userId = "user-1",
            name = name,
            dosage = dosage,
            currentQuantity = stock,
            totalQuantity = stock,
            prescribedByDoctor = false,
            prescriptionId = ""
        )
    }

    private fun samplePharmacy(id: String, name: String): Pharmacy {
        return Pharmacy(
            id = id,
            name = name,
            verificationStatus = "APPROVED",
            isActive = true,
            isDeliveryAvailable = true,
            estimatedDeliveryTime = "40 min"
        )
    }

    private fun sampleInventoryItem(
        medicineName: String,
        stockQuantity: Int,
        pharmacyId: String = "p1",
        unitPrice: Double = 50.0
    ): InventoryItem {
        val normalizedName = medicineName.trim().lowercase()

        return InventoryItem(
            id = "inv_$normalizedName",
            pharmacyId = pharmacyId,
            medicineName = medicineName,
            medicineNameNormalized = normalizedName,
            stockQuantity = stockQuantity,
            unitPrice = unitPrice,
            isActive = true
        )
    }

}
