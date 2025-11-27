package io.embrace.android.embracesdk.internal.injection

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

internal class DependencyInjectionKtTest {

    private val lazySingletonCounter = AtomicInteger(0)
    private val lazySingleton by singleton { lazySingletonCounter.incrementAndGet() }

    @Test
    fun testInjection() {
        assertEquals(0, lazySingletonCounter.get())
        assertEquals(1, lazySingleton)
        assertEquals(1, lazySingleton)
    }
}
