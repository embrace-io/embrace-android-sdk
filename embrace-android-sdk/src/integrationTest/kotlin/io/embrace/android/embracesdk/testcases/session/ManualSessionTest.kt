package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.fakes.behavior.FakeSessionBehavior
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getSentSessions
import io.embrace.android.embracesdk.getSingleSession
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionNumber
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import org.junit.Assert.assertEquals
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
                val messages = harness.getSentSessions(2)
                val stateSession = messages[0] // started via state, ended manually
                val manualSession = messages[1] // started manually, ended via state

                val stateAttrs = checkNotNull(stateSession.findSessionSpan().attributes)
                assertEquals("1", stateAttrs.findAttributeValue(embSessionNumber.name))

                val manualAttrs = checkNotNull(manualSession.findSessionSpan().attributes)
                assertEquals("2", manualAttrs.findAttributeValue(embSessionNumber.name))
            }
        )
    }

    @Test
    fun `calling endSession when session control enabled does not end sessions`() {
        testRule.runTest(
            setupAction = {
                overriddenConfigService.sessionBehavior =
                    FakeSessionBehavior(sessionControlEnabled = true)
            },
            testCaseAction = {
                recordSession {
                    clock.tick(10000)
                    embrace.endSession()
                }
            },
            assertAction = {
                val messages = harness.getSentSessions(1)
                checkNotNull(messages.single())
            }
        )
    }

    @Test
    fun `calling endSession when state session is below 5s has no effect`() {
        testRule.runTest(
            setupAction = {
                overriddenConfigService.sessionBehavior = FakeSessionBehavior(sessionControlEnabled = true)
            },
            testCaseAction = {
                recordSession {
                    clock.tick(1000) // not enough to trigger new session
                    embrace.endSession()
                }
            },
            assertAction = {
                val message = harness.getSingleSession()
                val attrs = checkNotNull(message.findSessionSpan().attributes)
                assertEquals("1", attrs.findAttributeValue(embSessionNumber.name))
            }
        )
    }
}
