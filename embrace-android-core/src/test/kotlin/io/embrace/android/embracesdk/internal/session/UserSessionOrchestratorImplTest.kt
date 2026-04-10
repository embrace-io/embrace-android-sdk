package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeOrdinalStore
import io.embrace.android.embracesdk.fakes.behavior.FakeUserSessionBehavior
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class UserSessionOrchestratorImplTest {

    private lateinit var clock: FakeClock
    private lateinit var orchestrator: UserSessionOrchestratorImpl

    private val maxDurationMs = TimeUnit.MINUTES.toMillis(10)
    private val inactivityMs = TimeUnit.MINUTES.toMillis(5)

    @Before
    fun setUp() {
        clock = FakeClock(currentTime = 0L)
        orchestrator = UserSessionOrchestratorImpl(
            clock = clock,
            configService = FakeConfigService(
                sessionBehavior = FakeUserSessionBehavior(
                    maxSessionDurationMs = maxDurationMs,
                    sessionInactivityTimeoutMs = inactivityMs,
                ),
            ),
            ordinalStore = FakeOrdinalStore(),
        )
    }

    @Test
    fun `user session max duration boundary`() {
        assertNull(orchestrator.currentUserSession())

        // create an initial user session
        orchestrator.onNewSessionPart()
        val first = checkNotNull(orchestrator.currentUserSession())
        assertEquals(1L, first.userSessionNumber)
        assertEquals(0L, first.startTimeMs)
        assertEquals(TimeUnit.MILLISECONDS.toMinutes(maxDurationMs), first.maxDurationMins)
        assertEquals(TimeUnit.MILLISECONDS.toMinutes(inactivityMs), first.inactivityTimeoutMins)
        assertNotNull(first.userSessionId)

        // create a new part within user session
        clock.tick(maxDurationMs - 1)
        orchestrator.onNewSessionPart()
        val repeat = checkNotNull(orchestrator.currentUserSession())
        assertEquals(first.userSessionId, repeat.userSessionId)

        // exceed max duration and trigger new user session
        clock.tick(1)
        orchestrator.onNewSessionPart()
        val second = checkNotNull(orchestrator.currentUserSession())
        assertNotEquals(first.userSessionId, second.userSessionId)
        assertEquals(2L, second.userSessionNumber)
        assertEquals(maxDurationMs, second.startTimeMs)
    }

    @Test
    fun `user session manual end`() {
        // onManualEnd from NO_ACTIVE_USER_SESSION still creates a session
        orchestrator.onManualEnd()
        val first = checkNotNull(orchestrator.currentUserSession())
        assertEquals(1L, first.userSessionNumber)

        // onManualEnd ends the active session (< maxDuration) and immediately starts a new one
        clock.tick(maxDurationMs - 1)
        orchestrator.onManualEnd()
        val second = checkNotNull(orchestrator.currentUserSession())
        assertEquals(2L, second.userSessionNumber)
        assertNotEquals(first.userSessionId, second.userSessionId)
        assertEquals(maxDurationMs - 1, second.startTimeMs)
    }

    @Test
    fun `user session ordinal persistence`() {
        val store = FakeOrdinalStore()
        val first = UserSessionOrchestratorImpl(
            clock = clock,
            configService = FakeConfigService(
                sessionBehavior = FakeUserSessionBehavior(
                    maxSessionDurationMs = maxDurationMs,
                    sessionInactivityTimeoutMs = inactivityMs,
                ),
            ),
            ordinalStore = store,
        )
        first.onNewSessionPart()
        assertEquals(1L, checkNotNull(first.currentUserSession()).userSessionNumber)

        // create a new orchestrator sharing the same store
        val second = UserSessionOrchestratorImpl(
            clock = clock,
            configService = FakeConfigService(
                sessionBehavior = FakeUserSessionBehavior(
                    maxSessionDurationMs = maxDurationMs,
                    sessionInactivityTimeoutMs = inactivityMs,
                ),
            ),
            ordinalStore = store,
        )
        second.onNewSessionPart()
        assertEquals(2L, checkNotNull(second.currentUserSession()).userSessionNumber)
    }

    @Test
    fun `user session metadata attributes`() {
        orchestrator.onNewSessionPart()
        val session = checkNotNull(orchestrator.currentUserSession())
        val attrs = session.attributes

        assertEquals(session.startTimeMs, attrs[EmbSessionAttributes.EMB_USER_SESSION_START_TS])
        assertEquals(session.userSessionId, attrs[EmbSessionAttributes.EMB_USER_SESSION_ID])
        assertEquals(session.userSessionNumber, attrs[EmbSessionAttributes.EMB_USER_SESSION_NUMBER])
        assertEquals(session.maxDurationMins, attrs[EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_MINUTES])
        assertEquals(session.inactivityTimeoutMins, attrs[EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_MINUTES])
    }
}
