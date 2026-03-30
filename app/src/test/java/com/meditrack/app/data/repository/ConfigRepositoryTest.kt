package com.meditrack.app.data.repository

import com.google.android.gms.tasks.Tasks
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfigRepositoryTest {

    private lateinit var remoteConfig: FirebaseRemoteConfig

    @Before
    fun setUp() {
        remoteConfig = mockk(relaxed = true)
        every { remoteConfig.setConfigSettingsAsync(any()) } returns Tasks.forResult(null)
        every { remoteConfig.setDefaultsAsync(any<Map<String, Any>>()) } returns Tasks.forResult(null)
    }

    @Test
    fun `resolveCheckoutKey prefers valid remote config key`() = runBlocking {
        every { remoteConfig.fetchAndActivate() } returns Tasks.forResult(true)
        every { remoteConfig.getString(ConfigRepository.KEY_RAZORPAY_KEY_ID) } returns " rzp_test_1234567890abcd "

        val repository = ConfigRepository(remoteConfig)
        val result = repository.resolveCheckoutKey("rzp_test_fallback_123456")

        assertEquals(ConfigRepository.RazorpayKeySource.REMOTE_CONFIG, result.source)
        assertEquals("rzp_test_1234567890abcd", result.key)
    }

    @Test
    fun `resolveCheckoutKey falls back to resource key when remote key missing`() = runBlocking {
        every { remoteConfig.fetchAndActivate() } returns Tasks.forResult(true)
        every { remoteConfig.getString(ConfigRepository.KEY_RAZORPAY_KEY_ID) } returns ""

        val repository = ConfigRepository(remoteConfig)
        val result = repository.resolveCheckoutKey("rzp_test_fallback_123456")

        assertEquals(ConfigRepository.RazorpayKeySource.RESOURCE_FALLBACK, result.source)
        assertEquals("rzp_test_fallback_123456", result.key)
    }

    @Test
    fun `resolveCheckoutKey ignores placeholder remote key and uses fallback`() = runBlocking {
        every { remoteConfig.fetchAndActivate() } returns Tasks.forResult(true)
        every { remoteConfig.getString(ConfigRepository.KEY_RAZORPAY_KEY_ID) } returns "rzp_live_XXXX"

        val repository = ConfigRepository(remoteConfig)
        val result = repository.resolveCheckoutKey("rzp_test_fallback_123456")

        assertEquals(ConfigRepository.RazorpayKeySource.RESOURCE_FALLBACK, result.source)
        assertEquals("rzp_test_fallback_123456", result.key)
    }

    @Test
    fun `resolveCheckoutKey returns NONE when remote fetch fails and fallback invalid`() = runBlocking {
        every { remoteConfig.fetchAndActivate() } returns Tasks.forException(RuntimeException("network failure"))

        val repository = ConfigRepository(remoteConfig)
        val result = repository.resolveCheckoutKey("invalid_key")

        assertEquals(ConfigRepository.RazorpayKeySource.NONE, result.source)
        assertNull(result.key)
    }
}
