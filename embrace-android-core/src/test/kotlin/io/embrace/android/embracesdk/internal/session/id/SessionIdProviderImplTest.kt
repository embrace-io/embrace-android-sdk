package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeSessionPartTracker
import io.embrace.android.embracesdk.fakes.fakeSessionPartToken
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class SessionIdProviderImplTest {

    private lateinit var sessionOrchestrator: FakeSessionOrchestrator
    private lateinit var sessionPartTracker: FakeSessionPartTracker
    private lateinit var provider: SessionIdProviderImpl

    @Before
    fun setUp() {
        sessionOrchestrator = FakeSessionOrchestrator()
        sessionPartTracker = FakeSessionPartTracker()
        provider = SessionIdProviderImpl({ sessionOrchestrator }, sessionPartTracker)
    }

    @Test
    fun `returns empty string when no active user session`() {
        assertEquals("", provider.getCurrentSessionPartId())
        assertEquals("", provider.getCurrentUserSessionId())
    }

    @Test
    fun `returns session part id when active session part exists`() {
        sessionPartTracker.currentSession = fakeSessionPartToken()
        assertEquals("fakeSessionId", provider.getCurrentSessionPartId())
    }

    @Test
    fun `returns user session id when active user session exists`() {
        sessionOrchestrator.currentSession = UserSessionMetadata(
            startTimeMs = 1000L,
            userSessionId = "user-session-uuid",
            userSessionNumber = 1L,
            maxDurationSecs = 3600L,
            inactivityTimeoutSecs = 300L,
            partNumber = 1,
            lastActivityMs = 1000L,
        )
        assertEquals("user-session-uuid", provider.getCurrentUserSessionId())
    }
}
