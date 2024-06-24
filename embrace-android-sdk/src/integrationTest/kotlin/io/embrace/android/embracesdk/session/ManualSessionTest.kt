package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getSentSessions
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.opentelemetry.embSessionNumber
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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

    @Before
    fun setUp() {
        assertTrue(testRule.harness.getSentSessions().isEmpty())
    }

    @Test
    fun `calling endSession ends stateful session`() {
        with(testRule) {
            harness.recordSession {
                harness.overriddenClock.tick(10000) // enough to trigger new session
                embrace.endSession()
            }
            val messages = harness.getSentSessions()
            assertEquals(2, messages.size)
            val stateSession = messages[0] // started via state, ended manually
            val manualSession = messages[1] // started manually, ended via state

            val stateAttrs = checkNotNull(stateSession.findSessionSpan().attributes)
            assertEquals("1", stateAttrs.findAttributeValue(embSessionNumber.name))

            val manualAttrs = checkNotNull(manualSession.findSessionSpan().attributes)
            assertEquals("2", manualAttrs.findAttributeValue(embSessionNumber.name))
        }
    }

    @Test
    fun `calling endSession when session control enabled does not end sessions`() {
        with(testRule) {
            harness.overriddenConfigService.sessionBehavior = fakeSessionBehavior {
                RemoteConfig(sessionConfig = SessionRemoteConfig(isEnabled = true))
            }
            harness.recordSession {
                harness.overriddenClock.tick(10000)
                embrace.endSession()
            }
            val messages = harness.getSentSessions()
            assertEquals(1, messages.size)
        }
    }

    @Test
    fun `calling endSession when state session is below 5s has no effect`() {
        with(testRule) {
            harness.overriddenConfigService.sessionBehavior = fakeSessionBehavior {
                RemoteConfig(sessionConfig = SessionRemoteConfig(isEnabled = true))
            }
            harness.recordSession {
                harness.overriddenClock.tick(1000) // not enough to trigger new session
                embrace.endSession()
            }
            val message = harness.getSentSessions().single()
            val attrs = checkNotNull(message.findSessionSpan().attributes)
            assertEquals("1", attrs.findAttributeValue(embSessionNumber.name))
        }
    }
}
