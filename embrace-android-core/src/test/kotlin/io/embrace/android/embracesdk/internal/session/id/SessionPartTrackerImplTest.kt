package io.embrace.android.embracesdk.internal.session.id

import android.app.ActivityManager
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.fakeSessionPartToken
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLoggerImpl
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SessionPartTrackerImplTest {
    private lateinit var tracker: SessionPartTracker

    @Before
    fun setUp() {
        tracker = SessionPartTrackerImpl(lazyOf<ActivityManager?>(null), InternalLoggerImpl())
    }

    @Test
    fun `a failing listener does not stop later listeners being notified`() {
        val logger = FakeInternalLogger(throwOnInternalError = false)
        tracker = SessionPartTrackerImpl(lazyOf<ActivityManager?>(null), logger)
        var endNotified = false
        var changeNotified = false
        tracker.addSessionPartEndListener { error("end listener failed") }
        tracker.addSessionPartEndListener { endNotified = true }
        tracker.addSessionPartChangeListener { error("change listener failed") }
        tracker.addSessionPartChangeListener { changeNotified = true }
        repeat(2) {
            tracker.newActiveSessionPart(
                endSessionPartCallback = {},
                startSessionPartCallback = { fakeSessionPartToken() },
                postTransitionProcessState = ProcessState.FOREGROUND,
            )
        }

        assertTrue(endNotified)
        assertTrue(changeNotified)
        assertEquals(
            List(3) { InternalErrorType.SessionPartListenerFail.toString() },
            logger.internalErrorMessages.map { it.msg },
        )
    }

    @Test
    fun `active session tracking`() {
        assertNull(tracker.getActiveSessionPart())
        assertNull(tracker.getActiveSessionPartId())

        val newSession = fakeSessionPartToken()
        tracker.newActiveSessionPart(
            endSessionPartCallback = {},
            startSessionPartCallback = { newSession },
            postTransitionProcessState = ProcessState.FOREGROUND,
        )

        assertEquals(newSession, tracker.getActiveSessionPart())
        assertEquals(newSession.sessionPartId, tracker.getActiveSessionPartId())

        val anotherSession = SessionPartToken(
            sessionPartId = "fake",
            userSessionId = "fake-user-session-id",
            startTime = 11L,
            processState = ProcessState.BACKGROUND,
            isColdStart = false,
            startType = LifeEventType.MANUAL,
        )
        tracker.newActiveSessionPart(
            endSessionPartCallback = {},
            startSessionPartCallback = { anotherSession },
            postTransitionProcessState = ProcessState.BACKGROUND,
        )

        assertEquals(anotherSession, tracker.getActiveSessionPart())
        assertEquals(anotherSession.sessionPartId, tracker.getActiveSessionPartId())

        tracker.newActiveSessionPart(
            endSessionPartCallback = {},
            startSessionPartCallback = { null },
            postTransitionProcessState = ProcessState.FOREGROUND,
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
                postTransitionProcessState = ProcessState.FOREGROUND,
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
            callbackInvocations.toList(),
        )
    }
}
