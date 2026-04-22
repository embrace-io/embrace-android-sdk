package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UserSessionRemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_NUMBER
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbUserSessionTerminationReasonValues.INACTIVITY
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbUserSessionTerminationReasonValues.MANUAL
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that the user session obeys rules about its lifecycle
 */
@RunWith(AndroidJUnit4::class)
internal class UserSessionLifecycleTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `user session created only once the app enters the foreground`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(100f)),
            testCaseAction = { recordSession() },
            assertAction = {
                val bgSession = getSingleSessionEnvelope(AppState.BACKGROUND)
                val fgSession = getSingleSessionEnvelope(AppState.FOREGROUND)
                assertEquals(bgSession.getUserSessionId(), fgSession.getUserSessionId())

                val fgSessionSpan = fgSession.findSessionSpan()
                assertEquals("1", fgSessionSpan.attributes?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertNull(fgSessionSpan.attributes?.findAttributeValue(EMB_IS_FINAL_SESSION_PART))
                assertNull(fgSessionSpan.attributes?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON))
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
                val firstSessionSpan = sessions[0].findSessionSpan()
                val secondSessionSpan = sessions[1].findSessionSpan()

                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
                assertEquals("1", firstSessionSpan.attributes?.findAttributeValue(EMB_IS_FINAL_SESSION_PART))
                assertEquals(MANUAL, firstSessionSpan.attributes?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON))
                assertNull(secondSessionSpan.attributes?.findAttributeValue(EMB_IS_FINAL_SESSION_PART))
                assertNull(secondSessionSpan.attributes?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON))
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
                val firstSessionSpan = sessions[0].findSessionSpan()
                val secondSessionSpan = sessions[1].findSessionSpan()

                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())

                // session orchestrator cannot be sure of final session reason, so does not set it
                assertNull(firstSessionSpan.attributes?.findAttributeValue(EMB_IS_FINAL_SESSION_PART))
                assertNull(firstSessionSpan.attributes?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON))
                assertNull(secondSessionSpan.attributes?.findAttributeValue(EMB_IS_FINAL_SESSION_PART))
                assertNull(secondSessionSpan.attributes?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON))
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
                assertEquals("1", bgSessions[1].findSessionSpan().attributes?.findAttributeValue(EMB_IS_FINAL_SESSION_PART))
                assertEquals(
                    INACTIVITY,
                    bgSessions[1].findSessionSpan().attributes?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON)
                )
                assertNull(bgSessions[0].findSessionSpan().attributes?.findAttributeValue(EMB_IS_FINAL_SESSION_PART))
                assertNull(fgSessions[0].findSessionSpan().attributes?.findAttributeValue(EMB_IS_FINAL_SESSION_PART))
                assertNull(fgSessions[0].findSessionSpan().attributes?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON))
                assertNull(fgSessions[1].findSessionSpan().attributes?.findAttributeValue(EMB_IS_FINAL_SESSION_PART))
                assertNull(fgSessions[1].findSessionSpan().attributes?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON))
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
                val firstSessionSpan = sessions[0].findSessionSpan()
                val secondSessionSpan = sessions[1].findSessionSpan()

                assertEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
                assertNull(firstSessionSpan.attributes?.findAttributeValue(EMB_IS_FINAL_SESSION_PART))
                assertNull(firstSessionSpan.attributes?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON))
                assertNull(secondSessionSpan.attributes?.findAttributeValue(EMB_IS_FINAL_SESSION_PART))
                assertNull(secondSessionSpan.attributes?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON))
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
                val firstSessionSpan = sessions[0].findSessionSpan()
                val secondSessionSpan = sessions[1].findSessionSpan()

                assertEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
                assertNull(firstSessionSpan.attributes?.findAttributeValue(EMB_IS_FINAL_SESSION_PART))
                assertNull(firstSessionSpan.attributes?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON))
                assertNull(secondSessionSpan.attributes?.findAttributeValue(EMB_IS_FINAL_SESSION_PART))
                assertNull(secondSessionSpan.attributes?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON))
            }
        )
    }

    @Test
    fun `load persisted user session in foreground state`() {
        val persistedId = "aabbccdd11223344aabbccdd11223344"
        val startMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 1_000L
        val lastActivityMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 100L
        testRule.runTest(
            setupAction = {
                persistUserSession(sessionId = persistedId, startMs = startMs, lastActivityMs = lastActivityMs)
            },
            testCaseAction = { recordSession() },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val sessionSpan = session.findSessionSpan()
                assertEquals(persistedId, session.getUserSessionId())
                assertEquals(persistedId, session.getSessionId())
                assertNull(sessionSpan.attributes?.findAttributeValue(EMB_IS_FINAL_SESSION_PART))
                assertNull(sessionSpan.attributes?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON))
            }
        )
    }

    @Test
    fun `load persisted user session in inactivity timeout state`() {
        val persistedId = "aabbccdd11223344aabbccdd11223344"
        val startMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 3_000_000L
        // lastActivityMs more than inactivity timeout before clock.now(), so the session is inactive
        val defaultInactivityMs = 1800L * 1_000L
        val lastActivityMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - defaultInactivityMs - 1L
        testRule.runTest(
            setupAction = {
                persistUserSession(sessionId = persistedId, startMs = startMs, lastActivityMs = lastActivityMs)
            },
            testCaseAction = { recordSession() },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val sessionSpan = session.findSessionSpan()
                assertNotEquals(persistedId, session.getUserSessionId())
                assertNotEquals(persistedId, session.getSessionId())
                assertNull(sessionSpan.attributes?.findAttributeValue(EMB_IS_FINAL_SESSION_PART))
                assertNull(sessionSpan.attributes?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON))
            }
        )
    }

    @Test
    fun `load persisted user session that exceeds max duration`() {
        val persistedId = "aabbccdd11223344aabbccdd11223344"
        // startMs more than max duration before clock.now(), so the session exceeds max duration
        val defaultMaxDurationMs = 43200L * 1_000L
        val startMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - defaultMaxDurationMs - 1L
        // lastActivityMs is recent so inactivity check does not fire first
        val lastActivityMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 1_000L
        testRule.runTest(
            setupAction = {
                persistUserSession(sessionId = persistedId, startMs = startMs, lastActivityMs = lastActivityMs)
            },
            testCaseAction = { recordSession() },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val sessionSpan = session.findSessionSpan()
                assertNotEquals(persistedId, session.getUserSessionId())
                assertNotEquals(persistedId, session.getSessionId())
                assertNull(sessionSpan.attributes?.findAttributeValue(EMB_IS_FINAL_SESSION_PART))
                assertNull(sessionSpan.attributes?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON))
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
                assertEquals("1", firstBg.attributes?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("1", firstSession.attributes?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("1", secondBg.attributes?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("2", secondSession.attributes?.findAttributeValue(EMB_USER_SESSION_NUMBER))
            }
        )
    }
}
