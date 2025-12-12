package io.embrace.android.embracesdk.internal.registry

import io.embrace.android.embracesdk.fakes.FakeAppStateTracker
import io.embrace.android.embracesdk.fakes.FakeSessionTracker
import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionEndListener
import io.embrace.android.embracesdk.internal.arch.state.AppStateListener
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
        assertEquals(expected, registry.sessionEndListeners)
        assertEquals(expected, registry.sessionChangeListeners)
    }

    @Test
    fun testListeners() {
        val registry = ServiceRegistry()
        val service = FakeService()
        registry.registerService(lazy { service })
        val expected = listOf(service)

        val activityService = FakeAppStateTracker()
        registry.registerAppStateListeners(activityService)
        assertEquals(expected, activityService.listeners)

        val sessionTracker = FakeSessionTracker()
        registry.registerSessionEndListeners(sessionTracker)
        assertEquals(expected, sessionTracker.sessionEndListeners)
        registry.registerSessionChangeListeners(sessionTracker)
        assertEquals(expected, sessionTracker.sessionChangeListeners)

        assertFalse(service.closed)
        registry.close()
        assertTrue(service.closed)
    }

    private class FakeService :
        Closeable,
        SessionEndListener,
        SessionChangeListener,
        AppStateListener {

        var closed = false

        override fun close() {
            closed = true
        }

        override fun onPreSessionEnd() {
        }

        override fun onPostSessionChange() {
        }

        override fun onBackground() {
        }

        override fun onForeground() {
        }
    }
}
