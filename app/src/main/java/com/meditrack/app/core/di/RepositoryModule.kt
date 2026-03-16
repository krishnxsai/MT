package com.meditrack.app.core.di

import com.meditrack.app.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepository = AuthRepository()

    @Provides
    @Singleton
    fun provideMedicineRepository(): MedicineRepository = MedicineRepository()

    @Provides
    @Singleton
    fun provideHealthLogRepository(): HealthLogRepository = HealthLogRepository()

    @Provides
    @Singleton
    fun provideChatRepository(): ChatRepository = ChatRepository()

    @Provides
    @Singleton
    fun provideDoctorRepository(): DoctorRepository = DoctorRepository()

    @Provides
    @Singleton
    fun provideAppointmentRepository(): AppointmentRepository = AppointmentRepository()

    @Provides
    @Singleton
    fun provideOrderRepository(): OrderRepository = OrderRepository()

    @Provides
    @Singleton
    fun providePharmacyRepository(): PharmacyRepository = PharmacyRepository()

    @Provides
    @Singleton
    fun providePharmacyInventoryRepository(): PharmacyInventoryRepository = PharmacyInventoryRepository()

    @Provides
    @Singleton
    fun provideAdminRepository(): AdminRepository = AdminRepository()

    @Provides
    @Singleton
    fun providePrescriptionRepository(): PrescriptionRepository = PrescriptionRepository()

    @Provides
    @Singleton
    fun provideClinicalDecisionRepository(): ClinicalDecisionRepository = ClinicalDecisionRepository()

    @Provides
    @Singleton
    fun provideMedicineIntakeRepository(): MedicineIntakeRepository = MedicineIntakeRepository()

    @Provides
    @Singleton
    fun provideRiskScoreRepository(): RiskScoreRepository = RiskScoreRepository()

    @Provides
    @Singleton
    fun provideRefillAlertRepository(): RefillAlertRepository = RefillAlertRepository()
}
