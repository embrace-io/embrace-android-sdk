package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.getSentSessionMessages
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.verifySessionHappened
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        assertTrue(testRule.harness.getSentSessionMessages().isEmpty())
    }

    @Test
    fun `calling endSession ends stateful session`() {
        with(testRule) {
            harness.recordSession {
                embrace.endSession()
            }
            val message = harness.getSentSessionMessages().single()
            verifySessionHappened(message)
            assertEquals(1, message.session.number)
        }
    }

    @Test
    fun `calling endSession when session control enabled does not end sessions`() {
        with(testRule) {
            harness.fakeConfigService.sessionBehavior = fakeSessionBehavior {
                RemoteConfig(sessionConfig = SessionRemoteConfig(isEnabled = true))
            }
            harness.recordSession {
                harness.fakeClock.tick(10000)
                embrace.endSession()
            }
            val messages = harness.getSentSessionMessages()
            assertEquals(1, messages.size)
            verifySessionHappened(messages[0])
        }
    }

    @Test
    fun `calling endSession when state session is below 5s has no effect`() {
        with(testRule) {
            harness.fakeConfigService.sessionBehavior = fakeSessionBehavior {
                RemoteConfig(sessionConfig = SessionRemoteConfig(isEnabled = true))
            }
            harness.recordSession {
                harness.fakeClock.tick(1000) // not enough to trigger new session
                embrace.endSession()
            }
            val message = harness.getSentSessionMessages().single()
            verifySessionHappened(message)
            assertEquals(1, message.session.number)
        }
    }
}
