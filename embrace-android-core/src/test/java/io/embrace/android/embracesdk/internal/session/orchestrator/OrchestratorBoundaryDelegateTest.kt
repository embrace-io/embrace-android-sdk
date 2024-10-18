package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerService
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeUserService
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class OrchestratorBoundaryDelegateTest {

    private lateinit var delegate: OrchestratorBoundaryDelegate
    private lateinit var memoryCleanerService: FakeMemoryCleanerService
    private lateinit var userService: FakeUserService
    private lateinit var sessionPropertiesService: FakeSessionPropertiesService

    @Before
    fun setUp() {
        memoryCleanerService = FakeMemoryCleanerService()
        userService = FakeUserService()
        sessionPropertiesService = FakeSessionPropertiesService()
        delegate = OrchestratorBoundaryDelegate(
            memoryCleanerService,
            userService,
            sessionPropertiesService
        )
    }

    @Test
    fun `cleanupAfterSessionEnd clear user info true`() {
        delegate.cleanupAfterSessionEnd(clearUserInfo = true)
        assertEquals(1, memoryCleanerService.callCount)
        assertEquals(1, sessionPropertiesService.cleanupAfterSessionEndCallCount)
        assertEquals(0, sessionPropertiesService.prepareNewSessionCallCount)
        assertEquals(1, userService.clearedCount)
    }

    @Test
    fun `cleanupAfterSessionEnd clear user info false`() {
        delegate.cleanupAfterSessionEnd(clearUserInfo = false)
        assertEquals(1, memoryCleanerService.callCount)
        assertEquals(1, sessionPropertiesService.cleanupAfterSessionEndCallCount)
        assertEquals(0, sessionPropertiesService.prepareNewSessionCallCount)
        assertEquals(0, userService.clearedCount)
    }

    @Test
    fun `prepare new session`() {
        delegate.prepareForNewSession()
        assertEquals(0, memoryCleanerService.callCount)
        assertEquals(0, sessionPropertiesService.cleanupAfterSessionEndCallCount)
        assertEquals(1, sessionPropertiesService.prepareNewSessionCallCount)
        assertEquals(0, userService.clearedCount)
    }
}
