package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeUserSessionOrchestrationModule
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
            essentialServiceModuleProvider = { error("not constructed") },
            userSessionOrchestrationModuleProvider = { error("not constructed") },
            instrumentationModuleProvider = { error("not constructed") },
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
            essentialServiceModuleProvider = { FakeEssentialServiceModule(userService = userService) },
            userSessionOrchestrationModuleProvider = {
                FakeUserSessionOrchestrationModule(sessionIdsProvider = sessionIdsProvider)
            },
            instrumentationModuleProvider = { error("unused") },
        )
        context.markReady()
        assertSame(sessionIdsProvider, context.getSessionIdsProvider())
        assertEquals("user-id", context.getUserId())
    }
}
