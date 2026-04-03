package com.meditrack.app.core.di

import android.content.Context
import com.meditrack.app.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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
    fun provideCartRepository(): CartRepository = CartRepository()

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
    fun provideOrderRepository(@ApplicationContext context: Context): OrderRepository =
        OrderRepository(context)

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

    @Provides
    @Singleton
    fun provideDeliveryTrackingRepository(): DeliveryTrackingRepository = DeliveryTrackingRepository()

    @Provides
    @Singleton
    fun provideStockForecastRepository(): StockForecastRepository = StockForecastRepository()

    @Provides
    @Singleton
    fun provideRazorpayRepository(@ApplicationContext context: Context): RazorpayRepository =
        RazorpayRepository(context)

    @Provides
    @Singleton
    fun provideNotificationPreferenceRepository(): NotificationPreferenceRepository =
        NotificationPreferenceRepository()

    @Provides
    @Singleton
    fun providePricingRepository(): PricingRepository = PricingRepository()

    @Provides
    @Singleton
    fun provideFeatureFlagRepository(): FeatureFlagRepository = FeatureFlagRepository()
}
