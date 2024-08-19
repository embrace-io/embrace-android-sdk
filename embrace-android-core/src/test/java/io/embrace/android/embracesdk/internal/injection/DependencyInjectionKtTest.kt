package io.embrace.android.embracesdk.internal.injection

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

internal class DependencyInjectionKtTest {

    private val factoryCounter = AtomicInteger(0)
    private val eagerSingletonCounter = AtomicInteger(0)
    private val lazySingletonCounter = AtomicInteger(0)

    private val factory by factory { factoryCounter.incrementAndGet() }
    private val eagerSingleton by singleton(LoadType.EAGER) { eagerSingletonCounter.incrementAndGet() }
    private val lazySingleton by singleton(LoadType.LAZY) { lazySingletonCounter.incrementAndGet() }

    @Test
    fun testInjection() {
        // assert defaults
        assertEquals(0, factoryCounter.get())
        assertEquals(1, eagerSingletonCounter.get())
        assertEquals(0, lazySingletonCounter.get())

        // get values from properties
        assertEquals(1, factory)
        assertEquals(1, eagerSingleton)
        assertEquals(1, lazySingleton)

        // get values again from properties
        assertEquals(2, factory)
        assertEquals(1, eagerSingleton)
        assertEquals(1, lazySingleton)
    }
}
