package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.FakeUserSessionPropertiesService
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class OrchestratorBoundaryDelegateTest {

    private lateinit var delegate: OrchestratorBoundaryDelegate
    private lateinit var userService: FakeUserService
    private lateinit var userSessionPropertiesService: FakeUserSessionPropertiesService

    @Before
    fun setUp() {
        userService = FakeUserService()
        userSessionPropertiesService = FakeUserSessionPropertiesService()
        delegate = OrchestratorBoundaryDelegate(
            userService,
            userSessionPropertiesService
        )
    }

    @Test
    fun `cleanupAfterSessionEnd clear user info true`() {
        delegate.cleanupAfterSessionEnd(clearUserInfo = true)
        assertEquals(1, userSessionPropertiesService.cleanupAfterSessionEndCallCount)
        assertEquals(0, userSessionPropertiesService.prepareNewSessionCallCount)
        assertEquals(1, userService.clearedCount)
    }

    @Test
    fun `cleanupAfterSessionEnd clear user info false`() {
        delegate.cleanupAfterSessionEnd(clearUserInfo = false)
        assertEquals(1, userSessionPropertiesService.cleanupAfterSessionEndCallCount)
        assertEquals(0, userSessionPropertiesService.prepareNewSessionCallCount)
        assertEquals(0, userService.clearedCount)
    }

    @Test
    fun `prepare new session`() {
        delegate.prepareForNewSession()
        assertEquals(0, userSessionPropertiesService.cleanupAfterSessionEndCallCount)
        assertEquals(1, userSessionPropertiesService.prepareNewSessionCallCount)
        assertEquals(0, userService.clearedCount)
    }
}
