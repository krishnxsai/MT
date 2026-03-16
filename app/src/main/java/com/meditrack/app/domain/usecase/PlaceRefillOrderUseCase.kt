package com.meditrack.app.domain.usecase

import com.meditrack.app.data.model.Medicine
import com.meditrack.app.data.model.Pharmacy
import com.meditrack.app.data.model.RefillOrder
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.OrderRepository
import com.meditrack.app.data.repository.PharmacyRepository
import com.meditrack.app.data.repository.PrescriptionVerification
import javax.inject.Inject

class PlaceRefillOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val pharmacyRepository: PharmacyRepository
) {
    suspend fun execute(
        medicine: Medicine,
        quantity: Int,
        notes: String = "",
        pharmacyId: String? = null,
        pharmacyName: String? = null,
        prescriptionVerification: PrescriptionVerification? = null,
        pharmacy: Pharmacy? = null
    ): Resource<RefillOrder> {
        val result = if (pharmacyId != null && pharmacyName != null) {
            orderRepository.placeRefillOrderWithPharmacy(
                medicine, quantity, pharmacyId, pharmacyName, notes
            )
        } else {
            orderRepository.placeRefillOrder(medicine, quantity, notes)
        }

        // Log the transaction if order was placed successfully
        if (result is Resource.Success) {
            val isVerified = prescriptionVerification?.isValid ?: false
            pharmacyRepository.logOrderTransaction(
                order = result.data,
                pharmacy = pharmacy,
                prescriptionVerified = isVerified
            )
        }

        return result
    }
}
