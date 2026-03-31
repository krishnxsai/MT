package com.meditrack.app.domain.usecase

import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.OrderStatus
import com.meditrack.app.data.model.RefillOrder
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.OrderRepository
import com.meditrack.app.data.repository.PharmacyRepository
import javax.inject.Inject

sealed class AcceptOrderResult {
    data class Accepted(val message: String) : AcceptOrderResult()
    data class PrescriptionInvalid(val reason: String) : AcceptOrderResult()
    data class Error(val message: String) : AcceptOrderResult()
    data class StatusAdvanced(val newStatus: String) : AcceptOrderResult()
}

class VerifyAndAcceptOrderUseCase @Inject constructor(
    private val pharmacyRepository: PharmacyRepository,
    private val orderRepository: OrderRepository
) {
    suspend fun execute(order: RefillOrder): AcceptOrderResult {
        // If order is pending with a prescription, verify first
        if (order.status == OrderStatus.PENDING && order.prescriptionId.isNotEmpty()) {
            val medicine = Medicine(
                id = order.medicineId,
                name = order.medicineName,
                prescriptionId = order.prescriptionId,
                prescribedByDoctor = true
            )
            return when (val result = pharmacyRepository.verifyPrescription(medicine)) {
                is Resource.Success -> {
                    if (result.data.isValid) {
                        when (val updateResult = orderRepository.updateOrderStatus(
                            order.id,
                            OrderStatus.CONFIRMED,
                            "Prescription verified"
                        )) {
                            is Resource.Success -> AcceptOrderResult.Accepted("Prescription verified — order confirmed")
                            is Resource.Error -> AcceptOrderResult.Error(updateResult.message)
                            is Resource.Loading -> AcceptOrderResult.Error("Unexpected loading state")
                        }
                    } else {
                        AcceptOrderResult.PrescriptionInvalid(result.data.reason)
                    }
                }
                is Resource.Error -> AcceptOrderResult.Error("Verification failed: ${result.message}")
                is Resource.Loading -> AcceptOrderResult.Error("Unexpected loading state")
            }
        }

        // Regular status advancement
        val nextStatus = when (order.status) {
            OrderStatus.PENDING -> OrderStatus.CONFIRMED
            OrderStatus.CONFIRMED -> OrderStatus.PREPARING
            OrderStatus.PREPARING -> OrderStatus.SHIPPED
            OrderStatus.SHIPPED -> OrderStatus.DELIVERED
            else -> return AcceptOrderResult.Error("Cannot advance order in current status")
        }

        return when (val updateResult = orderRepository.updateOrderStatus(order.id, nextStatus)) {
            is Resource.Success -> AcceptOrderResult.StatusAdvanced(nextStatus.name)
            is Resource.Error -> AcceptOrderResult.Error(updateResult.message)
            is Resource.Loading -> AcceptOrderResult.Error("Unexpected loading state")
        }
    }
}
