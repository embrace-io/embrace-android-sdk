package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertNotEquals
import org.junit.Ignore
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
                checkNotNull(stateSession.findSessionSpan())
                checkNotNull(manualSession.findSessionSpan())
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
                checkNotNull(message.findSessionSpan())
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
                checkNotNull(getSingleSessionEnvelope().findSessionSpan())
            }
        )
    }

    @Ignore("you should be able to end a user session in the background if background activity is enabled")
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
                assertNotEquals(fgSessions[0].getUserSessionId(), fgSessions[1].getUserSessionId())
            },
        )
    }
}
