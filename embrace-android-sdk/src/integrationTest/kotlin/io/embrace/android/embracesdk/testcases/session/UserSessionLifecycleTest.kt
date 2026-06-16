package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.assertions.getUserSessionTerminationReason
import io.embrace.android.embracesdk.assertions.isFinalSessionPart
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UserSessionRemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_SESSION_PART_ID
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_ID
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_NUMBER
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_PART_INDEX
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_START_TS
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbUserSessionTerminationReasonValues.BACKGROUND_ONLY_USER_SESSION_FOREGROUNDED
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbUserSessionTerminationReasonValues.INACTIVITY
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbUserSessionTerminationReasonValues.MANUAL
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule.Companion.DEFAULT_SDK_START_TIME_MS
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/**
 * Asserts that the user session obeys rules about its lifecycle
 */
@RunWith(AndroidJUnit4::class)
internal class UserSessionLifecycleTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(
            workersToFake = listOf(Worker.Background.NonIoRegWorker),
        ).apply {
            getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
        }
    }

    @Test
    fun `user session created at process start resolves to the foreground session`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(100f)),
            testCaseAction = { recordSession() },
            assertAction = {
                val bgSession = getSingleSessionEnvelope(AppState.BACKGROUND)
                val fgSession = getSingleSessionEnvelope(AppState.FOREGROUND)

                // the pre-foreground part belongs to the user session created eagerly at process
                // start, whose pending flavour resolved to the regular user session of this
                // launch when the app entered the foreground
                val bgSessionSpan = bgSession.findSessionSpan()
                val bgAttrs = bgSessionSpan.attributes
                assertEquals(fgSession.getUserSessionId(), bgAttrs?.findAttributeValue(EMB_USER_SESSION_ID))
                assertFalse(bgAttrs?.findAttributeValue(EMB_SESSION_PART_ID).isNullOrBlank())
                assertEquals("1", bgAttrs?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("1", bgAttrs?.findAttributeValue(EMB_USER_SESSION_PART_INDEX))
                assertNotNull(bgAttrs?.findAttributeValue(EMB_USER_SESSION_START_TS))

                val fgSessionSpan = fgSession.findSessionSpan()
                assertNotNull(fgSession.getUserSessionId())
                assertEquals(fgSession.getUserSessionId(), fgSession.getSessionId())
                assertEquals("1", fgSessionSpan.attributes?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertFalse(fgSession.isFinalSessionPart())
                assertNull(fgSession.getUserSessionTerminationReason())
            }
        )
    }

    @Test
    fun `process foregrounding after the background-startup window splits into background-only then regular session`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(100f)),
            testCaseAction = {
                // The process starts in the background and does not foreground within the 5s
                // background-startup window, so the eagerly-created process-start session is
                // classified background-only when the window elapses.
                clock.tick(6_000L)
                unblockTimerThread()
                // The app foregrounds only afterwards (e.g. a slow background launch the OS later
                // surfaced). This ends the background-only session and starts a regular one.
                recordSession()
            },
            assertAction = {
                val bgSession = getSingleSessionEnvelope(AppState.BACKGROUND)
                val fgSession = getSingleSessionEnvelope(AppState.FOREGROUND)

                // The pre-foreground part is the background-only session (number 1): it carries the
                // marker and ends with the background-only termination reason when the app foregrounds.
                val bgAttrs = bgSession.findSessionSpan().attributes
                assertEquals("1", bgAttrs?.findAttributeValue(EMB_IS_BACKGROUND_ONLY_PART))
                assertEquals("1", bgAttrs?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertTrue(bgSession.isFinalSessionPart())
                assertEquals(BACKGROUND_ONLY_USER_SESSION_FOREGROUNDED, bgSession.getUserSessionTerminationReason())

                // The foreground part is a distinct, regular user session (number 2) - no marker.
                val fgAttrs = fgSession.findSessionSpan().attributes
                assertNotEquals(bgSession.getUserSessionId(), fgSession.getUserSessionId())
                assertNull(fgAttrs?.findAttributeValue(EMB_IS_BACKGROUND_ONLY_PART))
                assertEquals("2", fgAttrs?.findAttributeValue(EMB_USER_SESSION_NUMBER))

                // The late foreground is recorded as a warm startup, not cold, and lands in the
                // regular foreground session - the accepted consequence of the fixed window.
                val perfSpanNames = fgSession.findSpansOfType(EmbType.Performance.Default).map { it.name }
                assertTrue(perfSpanNames.contains("emb-app-startup-warm"))
                assertFalse(perfSpanNames.contains("emb-app-startup-cold"))
            }
        )
    }

    @Test
    fun `new user session can be manually created`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    clock.tick(10_000)
                    embrace.endUserSession()
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)

                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
                assertTrue(sessions[0].isFinalSessionPart())
                assertEquals(MANUAL, sessions[0].getUserSessionTerminationReason())
                assertFalse(sessions[1].isFinalSessionPart())
                assertNull(sessions[1].getUserSessionTerminationReason())
            }
        )
    }

    @Test
    fun `new user session can be created via inactivity timeout with background activity disabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(inactivityTimeoutSeconds = 30)
            ),
            testCaseAction = {
                val inactivityMs = 30L * 1_000L
                recordSession()
                clock.tick(inactivityMs + 1_000L)
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)

                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())

                // session orchestrator cannot be sure of final session reason, so does not set it
                assertFalse(sessions[0].isFinalSessionPart())
                assertNull(sessions[0].getUserSessionTerminationReason())
                assertFalse(sessions[1].isFinalSessionPart())
                assertNull(sessions[1].getUserSessionTerminationReason())
            }
        )
    }

    @Test
    fun `new user session can be created via inactivity timeout with background activity enabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                backgroundActivityConfig = BackgroundActivityRemoteConfig(100f),
                userSession = UserSessionRemoteConfig(inactivityTimeoutSeconds = 30),
            ),
            testCaseAction = {
                val inactivityMs = 30L * 1_000L
                recordSession()
                clock.tick(inactivityMs + 1_000L)
                recordSession()
            },
            assertAction = {
                val fgSessions = getSessionEnvelopes(2, AppState.FOREGROUND)
                val bgSessions = getSessionEnvelopes(2, AppState.BACKGROUND)

                assertNotEquals(fgSessions[0].getUserSessionId(), fgSessions[1].getUserSessionId())
                assertTrue(bgSessions[1].isFinalSessionPart())
                assertEquals(INACTIVITY, bgSessions[1].getUserSessionTerminationReason())
                assertFalse(bgSessions[0].isFinalSessionPart())
                assertFalse(fgSessions[0].isFinalSessionPart())
                assertNull(fgSessions[0].getUserSessionTerminationReason())
                assertFalse(fgSessions[1].isFinalSessionPart())
                assertNull(fgSessions[1].getUserSessionTerminationReason())
            }
        )
    }

    @Test
    fun `user session recovers from inactivity timeout with background activity disabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(inactivityTimeoutSeconds = 30)
            ),
            testCaseAction = {
                val inactivityMs = 30L * 1_000L
                recordSession()
                clock.tick(inactivityMs - 1_000L)
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)

                assertEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
                assertFalse(sessions[0].isFinalSessionPart())
                assertNull(sessions[0].getUserSessionTerminationReason())
                assertFalse(sessions[1].isFinalSessionPart())
                assertNull(sessions[1].getUserSessionTerminationReason())
            }
        )
    }

    @Test
    fun `user session recovers from inactivity timeout with background activity enabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                backgroundActivityConfig = BackgroundActivityRemoteConfig(100f),
                userSession = UserSessionRemoteConfig(inactivityTimeoutSeconds = 30),
            ),
            testCaseAction = {
                val inactivityMs = 30L * 1_000L
                recordSession()
                clock.tick(inactivityMs - 1_000L)
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2, AppState.FOREGROUND)

                assertEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
                assertFalse(sessions[0].isFinalSessionPart())
                assertNull(sessions[0].getUserSessionTerminationReason())
                assertFalse(sessions[1].isFinalSessionPart())
                assertNull(sessions[1].getUserSessionTerminationReason())
            }
        )
    }

    @Test
    fun `load persisted user session in foreground state`() {
        val persistedId = "aabbccdd11223344aabbccdd11223344"
        val startMs = DEFAULT_SDK_START_TIME_MS - 1_000L
        val lastActivityMs = DEFAULT_SDK_START_TIME_MS - 100L
        testRule.runTest(
            setupAction = {
                persistUserSession(userSessionId = persistedId, startMs = startMs, lastActivityMs = lastActivityMs)
            },
            testCaseAction = { recordSession() },
            assertAction = {
                val session = getSingleSessionEnvelope()
                assertEquals(persistedId, session.getUserSessionId())
                assertEquals(persistedId, session.getSessionId())
                assertFalse(session.isFinalSessionPart())
                assertNull(session.getUserSessionTerminationReason())
            }
        )
    }

    @Test
    fun `load persisted user session in inactivity timeout state`() {
        val persistedId = "aabbccdd11223344aabbccdd11223344"
        val startMs = DEFAULT_SDK_START_TIME_MS - 3_000_000L
        // lastActivityMs more than inactivity timeout before clock.now(), so the session is inactive
        val defaultInactivityMs = 1800L * 1_000L
        val lastActivityMs = DEFAULT_SDK_START_TIME_MS - defaultInactivityMs - 1L
        testRule.runTest(
            setupAction = {
                persistUserSession(userSessionId = persistedId, startMs = startMs, lastActivityMs = lastActivityMs)
            },
            testCaseAction = { recordSession() },
            assertAction = {
                val session = getSingleSessionEnvelope()
                assertNotEquals(persistedId, session.getUserSessionId())
                assertNotEquals(persistedId, session.getSessionId())
                assertFalse(session.isFinalSessionPart())
                assertNull(session.getUserSessionTerminationReason())
            }
        )
    }

    @Test
    fun `load persisted user session that exceeds max duration`() {
        val persistedId = "aabbccdd11223344aabbccdd11223344"
        // startMs more than max duration before clock.now(), so the session exceeds max duration
        val defaultMaxDurationMs = 43200L * 1_000L
        val startMs = DEFAULT_SDK_START_TIME_MS - defaultMaxDurationMs - 1L
        // lastActivityMs is recent so inactivity check does not fire first
        val lastActivityMs = DEFAULT_SDK_START_TIME_MS - 1_000L
        testRule.runTest(
            setupAction = {
                persistUserSession(userSessionId = persistedId, startMs = startMs, lastActivityMs = lastActivityMs)
            },
            testCaseAction = { recordSession() },
            assertAction = {
                val session = getSingleSessionEnvelope()
                assertNotEquals(persistedId, session.getUserSessionId())
                assertNotEquals(persistedId, session.getSessionId())
                assertFalse(session.isFinalSessionPart())
                assertNull(session.getUserSessionTerminationReason())
            }
        )
    }

    @Test
    fun `new user session can be created via max duration`() {
        val cfg = UserSessionRemoteConfig(
            maxDurationSeconds = 3600,
            inactivityTimeoutSeconds = 3600,
        )
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                backgroundActivityConfig = BackgroundActivityRemoteConfig(threshold = 100.0f),
                userSession = cfg
            ),
            testCaseAction = {
                recordSession()
                val seconds = checkNotNull(cfg.maxDurationSeconds) * 5
                clock.tick(seconds * 1000L)
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val bgActivities = getSessionEnvelopes(2, state = AppState.BACKGROUND)
                val firstBg = bgActivities[0].findSessionSpan()
                val secondBg = bgActivities[1].findSessionSpan()
                val firstSession = sessions[0].findSessionSpan()
                val secondSession = sessions[1].findSessionSpan()

                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
                // the cold-start background part belongs to the first user session, created
                // eagerly at process start and resolved to regular at the first foreground
                assertEquals("1", firstBg.attributes?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("1", firstSession.attributes?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("1", secondBg.attributes?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("2", secondSession.attributes?.findAttributeValue(EMB_USER_SESSION_NUMBER))
            }
        )
    }

    @Test
    fun `manual session end kill switch does not suppress timer-driven inactivity session transition`() {
        val inactivityTimeoutSeconds = 30
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                sessionConfig = SessionRemoteConfig(isEnabled = true),
                userSession = UserSessionRemoteConfig(inactivityTimeoutSeconds = inactivityTimeoutSeconds),
            ),
            testCaseAction = {
                recordSession()
                clock.tick(inactivityTimeoutSeconds * 1_000L + 1L)
                unblockTimerThread()
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
            },
        )
    }

    @Test
    fun `inactivity timer at exact boundary expires the user session`() {
        val inactivityTimeoutSeconds = 30
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(inactivityTimeoutSeconds = inactivityTimeoutSeconds),
            ),
            testCaseAction = {
                recordSession()
                clock.tick(inactivityTimeoutSeconds * 1_000L)
                unblockTimerThread()
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
            },
        )
    }

    @Test
    fun `max duration timer at exact boundary expires the user session`() {
        val maxDurationSeconds = 3600
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(maxDurationSeconds = maxDurationSeconds),
            ),
            testCaseAction = {
                recordSession()
                clock.tick(maxDurationSeconds * 1_000L)
                unblockTimerThread()
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
            },
        )
    }

    @Test
    fun `manual end after max duration but before the job is processed terminates with MANUAL`() {
        val maxDurationSeconds = 600
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(maxDurationSeconds = maxDurationSeconds),
            ),
            testCaseAction = {
                recordSession {
                    clock.tick(maxDurationSeconds * 1_000L)
                    embrace.endUserSession()
                }
                unblockTimerThread()
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(3)
                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
                assertEquals(sessions[1].getUserSessionId(), sessions[2].getUserSessionId())
                assertEquals(MANUAL, sessions[0].getUserSessionTerminationReason())
            },
        )
    }

    @Test
    fun `foregrounding before inactivity timeout continues the same user session`() {
        runPartsBoundaryTest(
            inactivityTimeoutSeconds = 30,
            partsGapSeconds = 29
        ) { sessions ->
            assertEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
        }
    }

    @Test
    fun `foregrounding at inactivity boundary creates a new user session`() {
        runPartsBoundaryTest(
            inactivityTimeoutSeconds = 60,
            partsGapSeconds = 60
        ) { sessions ->
            assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
        }
    }

    @Test
    fun `foregrounding after inactivity boundary creates a new user session`() {
        runPartsBoundaryTest(
            inactivityTimeoutSeconds = 30,
            partsGapSeconds = 31
        ) { sessions ->
            assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
        }
    }

    private fun runPartsBoundaryTest(
        inactivityTimeoutSeconds: Int,
        partsGapSeconds: Int = 0,
        assertions: (sessions: List<Envelope<SessionPartPayload>>) -> Unit,
    ) {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(inactivityTimeoutSeconds = inactivityTimeoutSeconds),
            ),
            testCaseAction = {
                recordSession()
                clock.tick(partsGapSeconds.seconds.inWholeMilliseconds)
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertions(sessions)
                val reason = sessions[0].getUserSessionTerminationReason()
                if (reason != null && partsGapSeconds >= inactivityTimeoutSeconds) {
                    assertEquals(INACTIVITY, reason)
                }
            },
        )
    }

    private fun unblockTimerThread() {
        testRule.setup.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).runCurrentlyBlocked()
    }
}
