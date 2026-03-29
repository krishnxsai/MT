package com.meditrack.app.test

import com.meditrack.app.data.model.*
import java.security.MessageDigest
import java.util.Date
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Test data factories and builders for unit tests.
 * Provides realistic test data for all domain models.
 */

object TestData {

    // Constants
    const val TEST_USER_ID = "test_user_123"
    const val TEST_PHARMACY_ID = "test_pharmacy_456"
    const val TEST_DOCTOR_ID = "test_doctor_789"
    const val TEST_RAZORPAY_ORDER_ID = "order_1234567890"
    const val TEST_RAZORPAY_PAYMENT_ID = "pay_1234567890"
    const val TEST_MEDITRACK_ORDER_ID = "refill_order_xyz"
    const val TEST_RAZORPAY_SECRET = "test_secret_key_12345"

    // ─────────────── User Data ───────────────

    fun createTestUser(
        id: String = TEST_USER_ID,
        email: String = "test@example.com",
        name: String = "Test User",
        phone: String = "+919876543210",
        role: UserRole = UserRole.PATIENT
    ): User = User(
        id = id,
        email = email,
        name = name,
        phone = phone,
        role = role,
        profilePicture = null,
        address = "123 Test St, Test City",
        createdAt = Date(),
        updatedAt = Date(),
        isApproved = true
    )

    fun createTestPatient(id: String = TEST_USER_ID): User =
        createTestUser(id = id, role = UserRole.PATIENT)

    fun createTestDoctor(id: String = TEST_DOCTOR_ID): User =
        createTestUser(id = id, role = UserRole.DOCTOR, name = "Dr. Test")

    fun createTestPharmacy(id: String = TEST_PHARMACY_ID): User =
        createTestUser(id = id, role = UserRole.PHARMACY, name = "Test Pharmacy")

    // ─────────────── Razorpay Payment Data ───────────────

    fun createTestRazorpayOrder(
        id: String = "doc_${System.currentTimeMillis()}",
        razorpayOrderId: String = TEST_RAZORPAY_ORDER_ID,
        meditrackOrderId: String = TEST_MEDITRACK_ORDER_ID,
        amount: Int = 50000, // 500 rupees in paise
        userId: String = TEST_USER_ID,
        status: PaymentStatus = PaymentStatus.CREATED
    ): RazorpayOrder = RazorpayOrder(
        id = id,
        razorpayOrderId = razorpayOrderId,
        meditrackOrderId = meditrackOrderId,
        amount = amount,
        currency = "INR",
        userId = userId,
        customerEmail = "test@example.com",
        customerPhone = "+919876543210",
        customerName = "Test User",
        status = status,
        receipt = meditrackOrderId,
        attempts = 0
    )

    fun createTestPaymentSuccess(
        paymentId: String = TEST_RAZORPAY_PAYMENT_ID,
        orderId: String = TEST_RAZORPAY_ORDER_ID,
        meditrackOrderId: String = TEST_MEDITRACK_ORDER_ID
    ) = mapOf(
        "orderId" to orderId,
        "paymentId" to paymentId,
        "signature" to generateTestSignature(orderId, paymentId, TEST_RAZORPAY_SECRET),
        "meditrackOrderId" to meditrackOrderId
    )

    // ─────────────── Order Data ───────────────

    fun createTestRefillOrder(
        id: String = TEST_MEDITRACK_ORDER_ID,
        userId: String = TEST_USER_ID,
        pharmacyId: String = TEST_PHARMACY_ID,
        status: OrderStatus = OrderStatus.PENDING,
        totalAmount: Double = 500.0
    ): RefillOrder = RefillOrder(
        id = id,
        userId = userId,
        pharmacyId = pharmacyId,
        items = listOf(
            OrderItem("med_001", "Aspirin", 1, 100.0, "tablet", 100.0),
            OrderItem("med_002", "Vitamin C", 2, 200.0, "tablet", 400.0)
        ),
        status = status,
        totalAmount = totalAmount,
        deliveryAddress = "123 Test St, Test City",
        deliveryWindow = DeliveryWindow.MORNING,
        preferredDeliveryDate = Date(),
        paymentMethod = "RAZORPAY",
        paymentStatus = if (status == OrderStatus.CONFIRMED) PaymentStatus.CAPTURED else PaymentStatus.PENDING,
        createdAt = Date(),
        updatedAt = Date(),
        notes = ""
    )

    fun createTestConfirmedOrder(id: String = TEST_MEDITRACK_ORDER_ID): RefillOrder =
        createTestRefillOrder(id = id, status = OrderStatus.CONFIRMED)

    // ─────────────── Medicine Data ───────────────

    fun createTestMedicine(
        id: String = "med_001",
        name: String = "Aspirin",
        description: String = "Pain reliever",
        price: Double = 100.0,
        quantity: Int = 10
    ): Medicine = Medicine(
        id = id,
        name = name,
        description = description,
        price = price,
        quantity = quantity,
        unit = "tablet",
        expiryDate = Date(System.currentTimeMillis() + 86400000 * 365), // 1 year from now
        manufacturer = "Test Pharma",
        batchNumber = "BATCH123"
    )

    // ─────────────── Pharmacy Inventory Data ───────────────

    fun createTestInventoryItem(
        medicineId: String = "med_001",
        stock: Int = 100,
        price: Double = 100.0,
        lastUpdated: Date = Date()
    ): InventoryItem = InventoryItem(
        medicineId = medicineId,
        stock = stock,
        price = price,
        lastUpdated = lastUpdated
    )

    // ─────────────── Signature Verification Helpers ───────────────

    /**
     * Generate test HMAC-SHA256 signature for testing payment verification.
     * Must match the server-side calculation.
     */
    fun generateTestSignature(
        orderId: String,
        paymentId: String,
        secret: String
    ): String {
        val message = "$orderId|$paymentId"
        return try {
            val hmacKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(hmacKey)
            val digest = mac.doFinal(message.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate test signature: ${e.message}")
        }
    }

    /**
     * Generate invalid signature for negative testing.
     */
    fun generateInvalidSignature(): String = "invalid_signature_${System.currentTimeMillis()}"

    /**
     * Generate tampered signature (wrong secret).
     */
    fun generateTamperedSignature(
        orderId: String,
        paymentId: String,
        wrongSecret: String = "wrong_secret"
    ): String = generateTestSignature(orderId, paymentId, wrongSecret)

    // ─────────────── Resource Wrappers ───────────────

    fun <T> resourceSuccess(data: T): Resource<T> = Resource.Success(data)

    fun <T> resourceError(message: String): Resource<T> = Resource.Error(message)

    fun <T> resourceLoading(): Resource<T> = Resource.Loading
}

/**
 * Test-specific data holders
 */
data class TestPaymentContext(
    val razorpayOrderId: String = TestData.TEST_RAZORPAY_ORDER_ID,
    val razorpayPaymentId: String = TestData.TEST_RAZORPAY_PAYMENT_ID,
    val signature: String = TestData.generateTestSignature(
        TestData.TEST_RAZORPAY_ORDER_ID,
        TestData.TEST_RAZORPAY_PAYMENT_ID,
        TestData.TEST_RAZORPAY_SECRET
    ),
    val meditrackOrderId: String = TestData.TEST_MEDITRACK_ORDER_ID
)
