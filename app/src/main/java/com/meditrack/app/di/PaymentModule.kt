package com.meditrack.app.di

import android.content.Context
import com.meditrack.app.data.network.RazorpayApiClient
import com.meditrack.app.data.repository.ConfigRepository
import com.meditrack.app.data.repository.RazorpayPaymentHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for payment-related dependencies.
 *
 * Provides:
 * - RazorpayApiClient (Razorpay REST API client)
 * - RazorpayPaymentHandler (Payment callback handler)
 * - RazorpayRepository (Payment repository)
 * - ConfigRepository (Configuration management)
 *
 * All are singletons to ensure consistent state across the app.
 */
@Module
@InstallIn(SingletonComponent::class)
object PaymentModule {

    /**
     * Provide ConfigRepository singleton.
     *
     * ConfigRepository manages secure configuration from Firebase Remote Config,
     * which is populated from Google Cloud Secret Manager.
     *
     * Used for:
     * - Fetching Razorpay API Key ID
     * - Future: Other sensitive configuration
     */
    @Provides
    @Singleton
    fun provideConfigRepository(): ConfigRepository {
        return ConfigRepository()
    }

    /**
     * Provide RazorpayApiClient singleton.
     *
     * RazorpayApiClient is responsible for:
     * - Fetching payment details from Razorpay API
     * - Authenticating with HTTP Basic Auth
     * - Handling retries with exponential backoff
     * - Parsing and validating responses
     *
     * Dependency: ConfigRepository (for API credentials)
     */
    @Provides
    @Singleton
    fun provideRazorpayApiClient(
        configRepository: ConfigRepository
    ): RazorpayApiClient {
        return RazorpayApiClient(configRepository)
    }

    /**
     * Provide RazorpayPaymentHandler singleton.
     *
     * RazorpayPaymentHandler manages payment callbacks and state:
     * - Handles onPaymentSuccess callback
     * - Fetches complete payment response from API
     * - Caches responses to prevent redundant calls
     * - Thread-safe concurrent payment processing
     *
     * Dependency: RazorpayApiClient (for API calls)
     */
    @Provides
    @Singleton
    fun provideRazorpayPaymentHandler(
        apiClient: RazorpayApiClient
    ): RazorpayPaymentHandler {
        return RazorpayPaymentHandler(apiClient)
    }
}
