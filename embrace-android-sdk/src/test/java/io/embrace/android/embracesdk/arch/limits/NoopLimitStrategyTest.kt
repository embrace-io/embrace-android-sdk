package io.embrace.android.embracesdk.arch.limits

import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
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
