package com.meditrack.app.domain.usecase

import com.meditrack.app.data.model.OrderStatus
import com.meditrack.app.data.model.RefillOrder
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.OrderRepository
import com.meditrack.app.data.repository.PharmacyRepository
import javax.inject.Inject

class ReturnOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val pharmacyRepository: PharmacyRepository
) {
    suspend fun execute(order: RefillOrder, reason: String = "Returned by patient"): Resource<Unit> {
        if (order.status != OrderStatus.DELIVERED) {
            return Resource.Error("Only delivered orders can be returned")
        }

        val updateResult = orderRepository.updateOrderStatus(
            orderId = order.id,
            newStatus = OrderStatus.CANCELLED,
            note = reason
        )

        if (updateResult is Resource.Success) {
            pharmacyRepository.logRefundTransaction(order)
        }

        return updateResult
    }
}
