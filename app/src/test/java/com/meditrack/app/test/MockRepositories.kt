package com.meditrack.app.test

import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.*
import io.mockk.coEvery
import io.mockk.mockk

/**
 * Mock repository factory for unit tests.
 * Provides pre-configured mocks with common behaviors.
 */
object MockRepositories {

    /**
     * Create a mock RazorpayRepository with default success behaviors.
     */
    fun createMockRazorpayRepository(): RazorpayRepository = mockk {
        coEvery {
            createOrder(
                amount = any(),
                meditrackOrderId = any(),
                customerEmail = any(),
                customerPhone = any(),
                customerName = any()
            )
        } returns Resource.Success(
            TestData.createTestRazorpayOrder()
        )

        coEvery {
            verifyPayment(
                orderId = TestData.TEST_RAZORPAY_ORDER_ID,
                paymentId = TestData.TEST_RAZORPAY_PAYMENT_ID,
                signature = any(),
                meditrackOrderId = TestData.TEST_MEDITRACK_ORDER_ID
            )
        } returns Resource.Success(true)

        coEvery {
            recordPaymentSuccess(
                paymentId = any(),
                orderId = any(),
                signature = any(),
                meditrackOrderId = any(),
                paymentMethod = any()
            )
        } returns Resource.Success("payment_doc_123")

        coEvery {
            recordPaymentFailure(
                orderId = any(),
                meditrackOrderId = any(),
                errorCode = any(),
                errorDescription = any(),
                errorSource = any()
            )
        } returns Resource.Success(Unit)

        coEvery {
            refundPayment(paymentId = any(), amount = any())
        } returns Resource.Success("refund_123")
    }

    /**
     * Create a mock OrderRepository with default success behaviors.
     */
    fun createMockOrderRepository(): OrderRepository = mockk {
        coEvery {
            validateInventory(
                medicineId = any(),
                quantity = any(),
                pharmacyId = any()
            )
        } returns Resource.Success(true)

        coEvery {
            reduceInventory(
                medicineId = any(),
                quantity = any(),
                pharmacyId = any()
            )
        } returns Resource.Success(Unit)

        coEvery {
            placeOrder(any())
        } returns Resource.Success(TestData.TEST_MEDITRACK_ORDER_ID)

        coEvery {
            updateOrderStatus(
                orderId = any(),
                newStatus = any(),
                note = any()
            )
        } returns Resource.Success(Unit)

        coEvery {
            getOrderById(any())
        } returns Resource.Success(TestData.createTestRefillOrder())
    }

    /**
     * Create a mock PharmacyInventoryRepository with default success behaviors.
     */
    fun createMockPharmacyInventoryRepository(): PharmacyInventoryRepository = mockk {
        coEvery {
            getInventoryItem(
                medicineId = any(),
                pharmacyId = any()
            )
        } returns Resource.Success(TestData.createTestInventoryItem())

        coEvery {
            checkStock(
                medicineId = any(),
                quantity = any(),
                pharmacyId = any()
            )
        } returns Resource.Success(true)

        coEvery {
            updateStock(
                medicineId = any(),
                quantity = any(),
                pharmacyId = any()
            )
        } returns Resource.Success(Unit)
    }

    /**
     * Create a mock with failed payment verification.
     */
    fun createFailingPaymentRepository(): RazorpayRepository = mockk {
        coEvery {
            verifyPayment(
                orderId = any(),
                paymentId = any(),
                signature = any(),
                meditrackOrderId = any()
            )
        } returns Resource.Error("Payment signature verification failed")
    }

    /**
     * Create a mock with invalid inventory.
     */
    fun createInsufficientInventoryRepository(): PharmacyInventoryRepository = mockk {
        coEvery {
            checkStock(
                medicineId = any(),
                quantity = any(),
                pharmacyId = any()
            )
        } returns Resource.Success(false)
    }
}
