package com.meditrack.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RazorpayKeyValidatorFormatTest {

    @Test
    fun `accepts valid test key format`() {
        val key = "rzp_test_1234567890abcd"
        assertTrue(RazorpayKeyValidator.isValidKeyFormat(key))
        assertFalse(RazorpayKeyValidator.isPlaceholderKey(key))
    }

    @Test
    fun `accepts valid live key format`() {
        val key = "rzp_live_1234567890abcd"
        assertTrue(RazorpayKeyValidator.isValidKeyFormat(key))
        assertFalse(RazorpayKeyValidator.isPlaceholderKey(key))
    }

    @Test
    fun `rejects invalid key prefixes`() {
        assertFalse(RazorpayKeyValidator.isValidKeyFormat("rzp_invalid_1234567890"))
        assertFalse(RazorpayKeyValidator.isValidKeyFormat(""))
        assertFalse(RazorpayKeyValidator.isValidKeyFormat(null))
    }

    @Test
    fun `flags obvious placeholder values`() {
        assertTrue(RazorpayKeyValidator.isPlaceholderKey("rzp_live_XXXX"))
        assertTrue(RazorpayKeyValidator.isPlaceholderKey("rzp_test_placeholder_value"))
        assertTrue(RazorpayKeyValidator.isPlaceholderKey("rzp_live_dummy_key"))
    }
}
