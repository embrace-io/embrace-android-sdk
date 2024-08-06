package io.embrace.android.embracesdk.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

internal class IdGeneratorTest {

    @Test
    fun `check format conforms to expected standard`() {
        // Can't exhaustively verify that the generated traceparents will always fit, so lets just do a bunch to verify the unlikeliness
        val generator = IdGenerator()
        repeat(1000) {
            assertTrue(validPattern.matches(generator.generateTraceparent()))
        }
    }

    @Test
    fun `check exact traceparent generated with Random with a known seed`() {
        val knownGenerator = IdGenerator(random = Random(1881))
        assertEquals("00-f3805483a79dec663e81467524fc2f7d-9001c43540253a1a-01", knownGenerator.generateTraceparent())
    }

    @Test
    fun `check random returning 0s won't generate invalid traceheader`() {
        val zeroGenerator = IdGenerator(random = TestRandom())
        assertNotEquals("00-00000000000000000000000000000000-0000000000000000-01", zeroGenerator.generateTraceparent())
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

        /**
         * [Random] that returns 0s for the first 10 invocations of [nextLong] before generating a random long based on the default impl
         */
        class TestRandom : Random() {
            private var zeroCount = 0

            override fun nextLong(): Long {
                return if (zeroCount < 10) {
                    zeroCount++
                    0
                } else {
                    super.nextLong()
                }
            }

            override fun nextBits(bitCount: Int): Int = Default.nextBits(bitCount)
        }
    }
}
