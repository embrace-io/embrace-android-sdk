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
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_NUMBER
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_PART_NUMBER
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
                assertEquals("1", fgSession.findSessionSpan().attributes
                    ?.findAttributeValue(EMB_USER_SESSION_NUMBER))
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
                assertEquals("2", sessions[1].findSessionSpan().attributes
                    ?.findAttributeValue(EMB_USER_SESSION_NUMBER))
            }
        )
    }

    @Test
    fun `new user session can be created via max duration`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(
                    maxDurationSeconds = 3600,
                    inactivityTimeoutSeconds = 3600,
                )
            ),
            testCaseAction = {
                recordSession()
                // tick past max duration but keep under inactivity timeout.
                clock.tick(3_580_000L)
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
                assertEquals("2", sessions[1].findSessionSpan().attributes
                    ?.findAttributeValue(EMB_USER_SESSION_NUMBER))
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
                recordSession()
                clock.tick(31_000L)
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
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
                recordSession()
                clock.tick(31_000L)
                recordSession()
            },
            assertAction = {
                val fgSessions = getSessionEnvelopes(2, AppState.FOREGROUND)
                assertNotEquals(fgSessions[0].getUserSessionId(), fgSessions[1].getUserSessionId())
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
                recordSession()
                clock.tick(29_000L)
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
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
                recordSession()
                clock.tick(29_000L)
                recordSession()
            },
            assertAction = {
                val fgSessions = getSessionEnvelopes(2, AppState.FOREGROUND)
                assertEquals(fgSessions[0].getUserSessionId(), fgSessions[1].getUserSessionId())
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
                assertEquals(persistedId, session.getUserSessionId())
                assertEquals(persistedId, session.getSessionId())
            }
        )
    }

    @Test
    fun `load persisted user session in inactivity timeout state`() {
        val persistedId = "aabbccdd11223344aabbccdd11223344"
        val startMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 3_000_000L
        // lastActivityMs more than 1800s before clock.now(), so the session is inactive
        val lastActivityMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 1_800_001L
        testRule.runTest(
            setupAction = {
                persistUserSession(sessionId = persistedId, startMs = startMs, lastActivityMs = lastActivityMs)
            },
            testCaseAction = { recordSession() },
            assertAction = {
                val session = getSingleSessionEnvelope()
                assertNotEquals(persistedId, session.getUserSessionId())
                assertNotEquals(persistedId, session.getSessionId())
            }
        )
    }

    @Test
    fun `load persisted user session that exceeds max duration`() {
        val persistedId = "aabbccdd11223344aabbccdd11223344"
        // startMs more than 43200s (12h) before clock.now(), so the session exceeds max duration
        val startMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 43_200_001L
        // lastActivityMs is recent so inactivity check does not fire first
        val lastActivityMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 1_000L
        testRule.runTest(
            setupAction = {
                persistUserSession(sessionId = persistedId, startMs = startMs, lastActivityMs = lastActivityMs)
            },
            testCaseAction = { recordSession() },
            assertAction = {
                val session = getSingleSessionEnvelope()
                assertNotEquals(persistedId, session.getUserSessionId())
                assertNotEquals(persistedId, session.getSessionId())
            }
        )
    }

    @Test
    fun `background activity exceeding max duration lives in its own user session`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                backgroundActivityConfig = BackgroundActivityRemoteConfig(100f),
                userSession = UserSessionRemoteConfig(
                    maxDurationSeconds = 3600,
                    inactivityTimeoutSeconds = 3600,
                ),
            ),
            testCaseAction = {
                recordSession()
                // tick past max duration but stay under inactivity timeout
                clock.tick(3_580_000L)
                recordSession()
            },
            assertAction = {
                val fgSessions = getSessionEnvelopes(2, AppState.FOREGROUND)
                val bgSessions = getSessionEnvelopes(2, AppState.BACKGROUND)

                val fg1UserId = fgSessions[0].getUserSessionId()
                val fg2UserId = fgSessions[1].getUserSessionId()
                val bg1UserId = bgSessions[0].getUserSessionId()
                val bg2UserId = bgSessions[1].getUserSessionId()

                // both background sessions ran within user session 1 (before max duration fired)
                assertEquals(fg1UserId, bg1UserId)
                assertEquals(fg1UserId, bg2UserId)

                // returning to foreground after max duration creates a new user session
                assertNotEquals(fg1UserId, fg2UserId)
            }
        )
    }

    @Test
    fun `user session sequence numbers`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()
                recordSession {
                    clock.tick(10_000)
                    embrace.endUserSession()
                }
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(4)
                val us1p1 = sessions[0].findSessionSpan().attributes
                val us1p2 = sessions[1].findSessionSpan().attributes
                val us2p1 = sessions[2].findSessionSpan().attributes
                val us2p2 = sessions[3].findSessionSpan().attributes

                assertEquals("1", us1p1?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("1", us1p1?.findAttributeValue(EMB_USER_SESSION_PART_NUMBER))

                assertEquals("1", us1p2?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("2", us1p2?.findAttributeValue(EMB_USER_SESSION_PART_NUMBER))

                assertEquals("2", us2p1?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("1", us2p1?.findAttributeValue(EMB_USER_SESSION_PART_NUMBER))

                assertEquals("2", us2p2?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("2", us2p2?.findAttributeValue(EMB_USER_SESSION_PART_NUMBER))
            },
        )
    }
}
