package io.embrace.android.embracesdk.internal.instrumentation.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class TraceparentGeneratorTest {

    @Test
    fun `check format conforms to expected standard`() {
        // Can't exhaustively verify that the generated traceparents will always fit, so lets just do a bunch to verify the unlikeliness
        val generator = DefaultTraceparentGenerator
        repeat(1000) {
            assertTrue(validPattern.matches(generator.generateW3cTraceparent()))
        }
    }

    @Test
    fun validateRegex() {
        assertTrue(validPattern.matches("00-b583a45b2c7c813e0ebc6aa0835b9d98-b5475c618bb98e67-01"))
        assertFalse(validPattern.matches("00-b583a45b2c7c813e0ebc6aa0835b9d98-b5475c618bb98e67-00"))
        assertFalse(validPattern.matches("01-b583a45b2c7c813e0ebc6aa0835b9d98-b5475c618bb98e67-01"))
        assertFalse(validPattern.matches("00-b583a45b2c7c813e0ebc6aa0835b9d98s-b5475c618bb98e67-01"))
        assertFalse(validPattern.matches("00-b583a45b2c7c813e0ebc6aa0835b9d98-b5475c618bb98e67s-01"))
        assertFalse(validPattern.matches("00-b583a45b2c7c813e0ebc6aa0835b9d98-5475c618bb98e67-01"))
        assertFalse(validPattern.matches("00-b583a45b2c7c813e0ebc6aa0835b9d9-b5475c618bb98e67-01"))
        assertFalse(validPattern.matches("00-g583a45b2c7c813e0ebc6aa0835b9d98-b5475c618bb98e67-01"))
        assertFalse(validPattern.matches("00-b583a45b2c7c813e0ebc6aa0835b9d98-h5475c618bb98e67-01"))
    }

    companion object {
        val validPattern = Regex("^00-" + "[0-9a-fA-F]{32}" + "-" + "[0-9a-fA-F]{16}" + "-01$")
    }
}
