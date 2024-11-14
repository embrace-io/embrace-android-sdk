package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionNumber
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that a manual session can be recorded.
 */
@RunWith(AndroidJUnit4::class)
internal class ManualSessionTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `calling endSession ends stateful session`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    clock.tick(10000) // enough to trigger new session
                    embrace.endSession()
                }
            },
            assertAction = {
                val messages = getSessionEnvelopes(2)
                val stateSession = messages[0] // started via state, ended manually
                val manualSession = messages[1] // started manually, ended via state

                stateSession.findSessionSpan().attributes?.assertMatches {
                    embSessionNumber.name to 1
                }
                manualSession.findSessionSpan().attributes?.assertMatches {
                    embSessionNumber.name to 2
                }
            }
        )
    }

    @Test
    fun `calling endSession when session control enabled does not end sessions`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(sessionConfig = SessionRemoteConfig(isEnabled = true)),
            testCaseAction = {
                recordSession {
                    clock.tick(10000)
                    embrace.endSession()
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                checkNotNull(message.findSessionSpan())
            }
        )
    }

    @Test
    fun `calling endSession when state session is below 5s has no effect`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(sessionConfig = SessionRemoteConfig(isEnabled = true)),
            testCaseAction = {
                recordSession {
                    clock.tick(1000) // not enough to trigger new session
                    embrace.endSession()
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                message.findSessionSpan().attributes?.assertMatches {
                    embSessionNumber.name to 1
                }
            }
        )
    }
}
