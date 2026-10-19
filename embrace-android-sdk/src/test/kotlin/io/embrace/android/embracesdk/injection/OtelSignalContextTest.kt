package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.internal.injection.OtelSignalContext
import io.embrace.android.embracesdk.internal.payload.UserInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

internal class OtelSignalContextTest {

    @Test
    fun `no context is supplied before ready`() {
        val context = OtelSignalContext(
            sessionIdsProvider = { error("not constructed") },
            userServiceProvider = { error("not constructed") },
            sessionPartTrackerProvider = { error("not constructed") },
            processStateTrackerProvider = { error("not constructed") },
            experimentTrackingServiceProvider = { error("not constructed") },
            userSessionPropertiesServiceProvider = { error("not constructed") },
            instrumentationRegistryProvider = { error("not constructed") },
        )
        assertNull(context.getSessionIdsProvider())
        assertNull(context.getUserId())
        assertEquals(emptyMap<String, String>(), context.getEventMetadata())
    }

    @Test
    fun `context is supplied once ready`() {
        val sessionIdsProvider = FakeSessionIdsProvider()
        val userService = FakeUserService().apply { obj = UserInfo(userId = "user-id") }
        val context = OtelSignalContext(
            sessionIdsProvider = { sessionIdsProvider },
            userServiceProvider = { userService },
            sessionPartTrackerProvider = { error("unused") },
            processStateTrackerProvider = { error("unused") },
            experimentTrackingServiceProvider = { error("unused") },
            userSessionPropertiesServiceProvider = { error("unused") },
            instrumentationRegistryProvider = { error("unused") },
        )
        context.markReady()
        assertSame(sessionIdsProvider, context.getSessionIdsProvider())
        assertEquals("user-id", context.getUserId())
    }
}
