package io.embrace.android.embracesdk.registry

import io.embrace.android.embracesdk.config.ConfigListener
import io.embrace.android.embracesdk.fakes.FakeActivityTracker
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.session.MemoryCleanerListener
import io.embrace.android.embracesdk.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable

internal class ServiceRegistryTest {

    @Test
    fun testServiceRegistration() {
        val registry = ServiceRegistry(InternalEmbraceLogger())
        val service = FakeService()
        val obj = "test_obj"
        registry.registerServices(service, obj)

        val expected = listOf(service)
        assertEquals(expected, registry.closeables)
        assertEquals(expected, registry.processStateListeners)
        assertEquals(expected, registry.activityLifecycleListeners)
        assertEquals(expected, registry.memoryCleanerListeners)
        assertEquals(expected, registry.configListeners)
    }

    @Test
    fun testListeners() {
        val registry = ServiceRegistry(InternalEmbraceLogger())
        val service = FakeService()
        registry.registerService(service)
        val expected = listOf(service)

        val activityService = FakeProcessStateService()
        registry.registerActivityListeners(activityService)
        assertEquals(expected, activityService.listeners)

        val activityLifecycleTracker = FakeActivityTracker()
        registry.registerActivityLifecycleListeners(activityLifecycleTracker)
        assertEquals(expected, activityLifecycleTracker.listeners)

        val memoryCleanerService = FakeMemoryCleanerService()
        registry.registerMemoryCleanerListeners(memoryCleanerService)
        assertEquals(expected, memoryCleanerService.listeners)

        val configService = FakeConfigService()
        registry.registerConfigListeners(configService)
        assertEquals(expected, configService.listeners.toList())

        assertFalse(service.closed)
        registry.close()
        assertTrue(service.closed)
    }

    @Test(expected = IllegalStateException::class)
    fun testClosedRegistration() {
        val registry = ServiceRegistry(InternalEmbraceLogger())
        registry.closeRegistration()
        registry.registerService(FakeService())
    }

    private class FakeService :
        Closeable,
        ConfigListener,
        MemoryCleanerListener,
        ProcessStateListener,
        ActivityLifecycleListener {

        var closed = false

        override fun close() {
            closed = true
        }

        override fun cleanCollections() {
        }

        override fun onConfigChange() {
        }
    }
}
