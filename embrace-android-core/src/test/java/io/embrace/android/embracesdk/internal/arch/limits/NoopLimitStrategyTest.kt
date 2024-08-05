package io.embrace.android.embracesdk.internal.arch.limits

import org.junit.Assert.assertTrue
import org.junit.Test

internal class NoopLimitStrategyTest {

    @Test
    fun shouldCapture() {
        repeat(1000) {
            assertTrue(NoopLimitStrategy.shouldCapture())
        }
    }
}
