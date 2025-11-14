package io.embrace.android.embracesdk.internal.registry

import io.embrace.android.embracesdk.fakes.FakeActivityTracker
import io.embrace.android.embracesdk.fakes.FakeAppStateTracker
import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerService
import io.embrace.android.embracesdk.internal.arch.state.AppStateListener
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
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
        val obj = lazy { "test_obj" }
        registry.registerServices(lazy { service }, obj, lazy { null })

        val expected = listOf(service)
        assertEquals(expected, registry.closeables)
        assertEquals(expected, registry.appStateListeners)
        assertEquals(expected, registry.activityLifecycleListeners)
        assertEquals(expected, registry.memoryCleanerListeners)
    }

    @Test
    fun testListeners() {
        val registry = ServiceRegistry()
        val service = FakeService()
        registry.registerService(lazy { service })
        val expected = listOf(service)

        val activityService = FakeAppStateTracker()
        registry.registerActivityListeners(activityService)
        assertEquals(expected, activityService.listeners)

        val activityLifecycleTracker = FakeActivityTracker()
        registry.registerActivityLifecycleListeners(activityLifecycleTracker)
        assertEquals(expected, activityLifecycleTracker.listeners)

        val memoryCleanerService = FakeMemoryCleanerService()
        registry.registerMemoryCleanerListeners(memoryCleanerService)
        assertEquals(expected, memoryCleanerService.listeners)

        assertFalse(service.closed)
        registry.close()
        assertTrue(service.closed)
    }

    @Test(expected = IllegalStateException::class)
    fun testClosedRegistration() {
        val registry = ServiceRegistry()
        registry.closeRegistration()
        registry.registerService(lazy { FakeService() })
    }

    private class FakeService :
        Closeable,
        MemoryCleanerListener,
        AppStateListener,
        ActivityLifecycleListener {

        var closed = false

        override fun close() {
            closed = true
        }

        override fun cleanCollections() {
        }
    }
}
