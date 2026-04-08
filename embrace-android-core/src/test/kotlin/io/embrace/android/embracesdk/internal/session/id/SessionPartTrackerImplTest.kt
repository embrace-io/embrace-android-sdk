package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.fakes.fakeSessionPartToken
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.logging.InternalLoggerImpl
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class SessionPartTrackerImplTest {
    private lateinit var tracker: SessionPartTracker

    @Before
    fun setUp() {
        tracker = SessionPartTrackerImpl(null, InternalLoggerImpl())
    }

    @Test
    fun `active session tracking`() {
        assertNull(tracker.getActiveSession())
        assertNull(tracker.getActiveSessionId())

        val newSession = fakeSessionPartToken()
        tracker.newActiveSession(
            endSessionCallback = {},
            startSessionCallback = { newSession },
            postTransitionAppState = AppState.FOREGROUND
        )

        assertEquals(newSession, tracker.getActiveSession())
        assertEquals(newSession.sessionId, tracker.getActiveSessionId())

        val anotherSession = SessionPartToken(
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
        val newSessions = listOf(fakeSessionPartToken(), fakeSessionPartToken(), null)
        val callbackInvocations = mutableListOf<String>()
        tracker.addSessionPartChangeListener {
            callbackInvocations.add("change-listener")
        }
        tracker.addSessionPartEndListener {
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
                "new-session",
                "change-listener",
            ),
            callbackInvocations.toList()
        )
    }
}
