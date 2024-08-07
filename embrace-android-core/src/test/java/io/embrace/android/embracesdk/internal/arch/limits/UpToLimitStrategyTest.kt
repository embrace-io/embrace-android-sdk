package io.embrace.android.embracesdk.internal.arch.limits

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class UpToLimitStrategyTest {

    private val provider = { 10 }

    @Test
    fun shouldCapture() {
        val strategy = UpToLimitStrategy(provider)
        repeat(10) {
            assertTrue(strategy.shouldCapture())
        }
        repeat(10) {
            assertFalse(strategy.shouldCapture())
        }

        // reset & try again
        strategy.resetDataCaptureLimits()

        repeat(10) {
            assertTrue(strategy.shouldCapture())
        }
        repeat(10) {
            assertFalse(strategy.shouldCapture())
        }
    }
}
