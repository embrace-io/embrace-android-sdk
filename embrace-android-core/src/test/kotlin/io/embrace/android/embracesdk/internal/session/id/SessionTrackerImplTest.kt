package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.fakes.fakeSessionToken
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class SessionTrackerImplTest {

    private lateinit var tracker: SessionTracker

    @Before
    fun setUp() {
        tracker = SessionTrackerImpl(null, EmbLoggerImpl())
    }

    @Test
    fun `verify tracker implementation`() {
        var count = 0
        tracker.addListener {
            count++
        }
        assertNull(tracker.getActiveSession())
        assertNull(tracker.getActiveSessionId())
        assertEquals(0, count)
        val newSession = fakeSessionToken()
        tracker.newActiveSession(
            endSessionCallback = {},
            startSessionCallback = { newSession },
            postTransitionAppState = AppState.FOREGROUND
        )

        assertEquals(newSession, tracker.getActiveSession())
        assertEquals(newSession.sessionId, tracker.getActiveSessionId())
        assertEquals(1, count)
    }
}
