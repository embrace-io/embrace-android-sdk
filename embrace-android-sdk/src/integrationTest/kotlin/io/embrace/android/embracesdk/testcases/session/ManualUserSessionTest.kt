package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbUserSessionTerminationReasonValues.MANUAL
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertDistinctUserSessions
import io.embrace.android.embracesdk.testframework.assertions.assertFinalPart
import io.embrace.android.embracesdk.testframework.assertions.assertNotFinalPart
import io.embrace.android.embracesdk.testframework.assertions.assertUserSessionNumbers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that a manual session can be recorded.
 */
@RunWith(AndroidJUnit4::class)
internal class ManualUserSessionTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `calling endUserSession ends stateful session`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    clock.tick(10000) // enough to trigger new session
                    embrace.endUserSession()
                }
            },
            assertAction = {
                val messages = getSessionEnvelopes(2)
                val stateSession = messages[0] // started via state, ended manually
                val manualSession = messages[1] // started manually, ended via state
                checkNotNull(stateSession.findSessionPartSpan())
                checkNotNull(manualSession.findSessionPartSpan())
                assertUserSessionNumbers(
                    envelopes = messages,
                    userSessionNumbers = listOf(1, 2),
                    sessionPartNumbers = listOf(1, 2)
                )
            }
        )
    }

    @Test
    fun `calling endUserSession when session control enabled does not end sessions`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(sessionConfig = SessionRemoteConfig(isEnabled = true)),
            testCaseAction = {
                recordSession {
                    clock.tick(10000)
                    embrace.endUserSession()
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                checkNotNull(message.findSessionPartSpan())
            }
        )
    }

    @Test
    fun `calling endUserSession when state session is below 5s has no effect`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(sessionConfig = SessionRemoteConfig(isEnabled = true)),
            testCaseAction = {
                recordSession {
                    clock.tick(1000) // not enough to trigger new session
                    embrace.endUserSession()
                }
            },
            assertAction = {
                checkNotNull(getSingleSessionEnvelope().findSessionPartSpan())
            }
        )
    }

    @Test
    fun `endUserSession from background should end user session when the background activity feature is enabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                backgroundActivityConfig = BackgroundActivityRemoteConfig(threshold = 100f),
            ),
            testCaseAction = {
                recordSession()
                embrace.endUserSession()
                recordSession()
            },
            assertAction = {
                val fgSessions = getSessionEnvelopes(2)
                assertDistinctUserSessions(fgSessions[0], fgSessions[1])
            },
        )
    }

    @Test
    fun `manually ending a user session in the background while background activity is disabled does not create a new user session`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()
                embrace.endUserSession()
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertDistinctUserSessions(sessions[0], sessions[1])
                assertUserSessionNumbers(
                    envelopes = sessions,
                    userSessionNumbers = listOf(1, 2),
                    sessionPartNumbers = listOf(1, 2)
                )
            },
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

                assertDistinctUserSessions(sessions[0], sessions[1])
                assertUserSessionNumbers(
                    envelopes = sessions,
                    userSessionNumbers = listOf(1, 2),
                    sessionPartNumbers = listOf(1, 2)
                )
                sessions[0].assertFinalPart(MANUAL)
                sessions[1].assertNotFinalPart()
            }
        )
    }

    @Test
    fun `endUserSession past the manual cooldown window ends a second user session`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    // user session old enough for the first manual end to take effect
                    clock.tick(10_000)
                    embrace.endUserSession()
                    // past the 5s cooldown, so the second manual end also takes effect
                    clock.tick(6_000)
                    embrace.endUserSession()
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(3)
                assertDistinctUserSessions(sessions[0], sessions[1])
                assertDistinctUserSessions(sessions[1], sessions[2])
                assertUserSessionNumbers(
                    envelopes = sessions,
                    userSessionNumbers = listOf(1, 2, 3),
                    sessionPartNumbers = listOf(1, 2, 3)
                )
                sessions[0].assertFinalPart(MANUAL)
                sessions[1].assertFinalPart(MANUAL)
                sessions[2].assertNotFinalPart()
            }
        )
    }
}
