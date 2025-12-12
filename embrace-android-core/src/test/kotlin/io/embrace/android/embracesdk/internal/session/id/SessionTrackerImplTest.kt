package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.fakes.fakeSessionToken
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionToken
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
    fun `active session tracking`() {
        assertNull(tracker.getActiveSession())
        assertNull(tracker.getActiveSessionId())

        val newSession = fakeSessionToken()
        tracker.newActiveSession(
            endSessionCallback = {},
            startSessionCallback = { newSession },
            postTransitionAppState = AppState.FOREGROUND
        )

        assertEquals(newSession, tracker.getActiveSession())
        assertEquals(newSession.sessionId, tracker.getActiveSessionId())

        val anotherSession = SessionToken(
            sessionId = "fake",
            startTime = 11L,
            number = 3,
            appState = AppState.BACKGROUND,
            isColdStart = false,
            startType = LifeEventType.MANUAL
        )
        tracker.newActiveSession(
            endSessionCallback = {},
            startSessionCallback = { anotherSession },
            postTransitionAppState = AppState.BACKGROUND
        )

        assertEquals(anotherSession, tracker.getActiveSession())
        assertEquals(anotherSession.sessionId, tracker.getActiveSessionId())

        tracker.newActiveSession(
            endSessionCallback = {},
            startSessionCallback = { null },
            postTransitionAppState = AppState.FOREGROUND
        )

        assertNull(tracker.getActiveSession())
        assertNull(tracker.getActiveSessionId())
    }

    @Test
    fun `callback invocation`() {
        val newSessions = listOf(fakeSessionToken(), fakeSessionToken(), null)
        val callbackInvocations = mutableListOf<String>()
        tracker.addSessionChangeListener {
            callbackInvocations.add("change-listener")
        }
        tracker.addSessionEndListener {
            callbackInvocations.add("end-listener")
        }

        repeat(newSessions.size) { i ->
            tracker.newActiveSession(
                endSessionCallback = {
                    callbackInvocations.add("end-session")
                },
                startSessionCallback = {
                    callbackInvocations.add("new-session")
                    newSessions[i]
                },
                postTransitionAppState = AppState.FOREGROUND
            )
        }

        assertEquals(
            listOf(
                "new-session",
                "change-listener",
                "end-listener",
                "end-session",
                "new-session",
                "change-listener",
                "end-listener",
                "end-session",
                "new-session"
            ),
            callbackInvocations.toList()
        )
    }
}
