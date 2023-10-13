package io.embrace.android.embracesdk.registry

import io.embrace.android.embracesdk.config.ConfigListener
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.fakes.FakeActivityService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerService
import io.embrace.android.embracesdk.session.ActivityListener
import io.embrace.android.embracesdk.session.MemoryCleanerListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable

internal class ServiceRegistryTest {

    @Test
    fun testServiceRegistration() {
        val registry = ServiceRegistry()
        val service = FakeService()
        val obj = "test_obj"
        registry.registerServices(service, obj)

        val expected = listOf(service)
        assertEquals(expected, registry.closeables)
        assertEquals(expected, registry.activityListeners)
        assertEquals(expected, registry.memoryCleanerListeners)
        assertEquals(expected, registry.configListeners)
    }

    @Test
    fun testListeners() {
        val registry = ServiceRegistry()
        val service = FakeService()
        registry.registerService(service)
        val expected = listOf(service)

        val activityService = FakeActivityService()
        registry.registerActivityListeners(activityService)
        assertEquals(expected, activityService.listeners)

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
        val registry = ServiceRegistry()
        registry.closeRegistration()
        registry.registerService(FakeService())
    }

    private class FakeService :
        Closeable,
        ConfigListener,
        MemoryCleanerListener,
        ActivityListener {

        var closed = false

        override fun close() {
            closed = true
        }

        override fun cleanCollections() {
        }

        override fun onConfigChange(configService: ConfigService) {
        }
    }
}
