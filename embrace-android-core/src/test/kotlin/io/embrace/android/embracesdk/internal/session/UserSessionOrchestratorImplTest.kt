package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.behavior.FakeUserSessionBehavior
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class UserSessionOrchestratorImplTest {

    private lateinit var clock: FakeClock
    private lateinit var logger: FakeInternalLogger
    private lateinit var orchestrator: UserSessionOrchestratorImpl

    private val maxDurationMs = TimeUnit.MINUTES.toMillis(10)
    private val inactivityMs = TimeUnit.MINUTES.toMillis(5)

    @Before
    fun setUp() {
        clock = FakeClock(currentTime = 0L)
        logger = FakeInternalLogger(throwOnInternalError = false)
        orchestrator = UserSessionOrchestratorImpl(
            clock = clock,
            configService = FakeConfigService(
                sessionBehavior = FakeUserSessionBehavior(
                    maxSessionDurationMs = maxDurationMs,
                    sessionInactivityTimeoutMs = inactivityMs,
                ),
            ),
            logger = logger,
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

    @Test
    fun `listener notified when new part creates user session`() {
        var callCount = 0
        orchestrator.addListener { callCount++ }

        orchestrator.onNewSessionPart()

        assertEquals(1, callCount)
    }

    @Test
    fun `listener not notified when new part is within same user session`() {
        var callCount = 0
        orchestrator.addListener { callCount++ }

        orchestrator.onNewSessionPart()
        clock.tick(maxDurationMs - 1)
        orchestrator.onNewSessionPart()

        assertEquals(1, callCount)
    }

    @Test
    fun `listener notified when max duration exceeded`() {
        var callCount = 0
        orchestrator.addListener { callCount++ }

        orchestrator.onNewSessionPart()
        clock.tick(maxDurationMs)
        orchestrator.onNewSessionPart()

        assertEquals(2, callCount)
    }

    @Test
    fun `listener notified twice on manual end`() {
        var callCount = 0
        orchestrator.addListener { callCount++ }

        orchestrator.onManualEnd()
        orchestrator.onManualEnd()

        assertEquals(2, callCount)
    }

    @Test
    fun `multiple listeners all receive callback`() {
        var firstCount = 0
        var secondCount = 0
        orchestrator.addListener { firstCount++ }
        orchestrator.addListener { secondCount++ }

        orchestrator.onNewSessionPart()

        assertEquals(1, firstCount)
        assertEquals(1, secondCount)
    }

    @Test
    fun `throwing listener does not prevent subsequent invocations`() {
        var subsequentCount = 0
        orchestrator.addListener { throw RuntimeException("boom") }
        orchestrator.addListener { subsequentCount++ }

        orchestrator.onNewSessionPart()

        assertEquals(1, subsequentCount)
        assertTrue(logger.internalErrorMessages.isNotEmpty())
        assertEquals(
            InternalErrorType.USER_SESSION_CALLBACK_FAIL.name,
            logger.internalErrorMessages.first().msg,
        )
    }
}
