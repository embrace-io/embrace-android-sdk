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
            userSessionPropertiesService,
        )
    }

    @Test
    fun cleanupAfterUserSessionEnd() {
        delegate.cleanupAfterUserSessionEnd()
        assertEquals(1, userSessionPropertiesService.cleanupAfterUserSessionEndCallCount)
        assertEquals(0, userSessionPropertiesService.prepareNewSessionPartCallCount)
    }

    @Test
    fun `prepare new session part`() {
        delegate.prepareForNewSessionPart()
        assertEquals(0, userSessionPropertiesService.cleanupAfterUserSessionEndCallCount)
        assertEquals(1, userSessionPropertiesService.prepareNewSessionPartCallCount)
    }
}
