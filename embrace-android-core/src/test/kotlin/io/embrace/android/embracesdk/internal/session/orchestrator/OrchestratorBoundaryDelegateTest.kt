package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.fakes.FakeUserSessionPropertiesService
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class OrchestratorBoundaryDelegateTest {

    private lateinit var delegate: OrchestratorBoundaryDelegate
    private lateinit var userSessionPropertiesService: FakeUserSessionPropertiesService

    @Before
    fun setUp() {
        userSessionPropertiesService = FakeUserSessionPropertiesService()
        delegate = OrchestratorBoundaryDelegate(
            userSessionPropertiesService
        )
    }

    @Test
    fun cleanupAfterSessionEnd() {
        delegate.cleanupAfterSessionEnd()
        assertEquals(1, userSessionPropertiesService.cleanupAfterSessionEndCallCount)
        assertEquals(0, userSessionPropertiesService.prepareNewSessionCallCount)
    }

    @Test
    fun `prepare new session`() {
        delegate.prepareForNewSession()
        assertEquals(0, userSessionPropertiesService.cleanupAfterSessionEndCallCount)
        assertEquals(1, userSessionPropertiesService.prepareNewSessionCallCount)
    }
}
