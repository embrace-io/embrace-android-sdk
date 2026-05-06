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
        assertNull(tracker.getActiveSessionPart())
        assertNull(tracker.getActiveSessionPartId())

        val newSession = fakeSessionPartToken()
        tracker.newActiveSessionPart(
            endSessionPartCallback = {},
            startSessionPartCallback = { newSession },
            postTransitionAppState = AppState.FOREGROUND
        )

        assertEquals(newSession, tracker.getActiveSessionPart())
        assertEquals(newSession.sessionPartId, tracker.getActiveSessionPartId())

        val anotherSession = SessionPartToken(
            sessionPartId = "fake",
            userSessionId = "fake-user-session-id",
            startTime = 11L,
            appState = AppState.BACKGROUND,
            isColdStart = false,
            startType = LifeEventType.MANUAL
        )
        tracker.newActiveSessionPart(
            endSessionPartCallback = {},
            startSessionPartCallback = { anotherSession },
            postTransitionAppState = AppState.BACKGROUND
        )

        assertEquals(anotherSession, tracker.getActiveSessionPart())
        assertEquals(anotherSession.sessionPartId, tracker.getActiveSessionPartId())

        tracker.newActiveSessionPart(
            endSessionPartCallback = {},
            startSessionPartCallback = { null },
            postTransitionAppState = AppState.FOREGROUND
        )

        assertNull(tracker.getActiveSessionPart())
        assertNull(tracker.getActiveSessionPartId())
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
            tracker.newActiveSessionPart(
                endSessionPartCallback = {
                    callbackInvocations.add("end-session")
                },
                startSessionPartCallback = {
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
