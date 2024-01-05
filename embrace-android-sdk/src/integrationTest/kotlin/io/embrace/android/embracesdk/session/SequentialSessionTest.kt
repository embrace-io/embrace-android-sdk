package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.fakeDataCaptureEventBehavior
import io.embrace.android.embracesdk.getSentSessionMessages
import io.embrace.android.embracesdk.internal.MessageType
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that a stateful session can be recorded.
 */
@RunWith(AndroidJUnit4::class)
internal class SequentialSessionTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `session message type disabled`() {
        with(testRule) {
            harness.fakeConfigService.dataCaptureEventBehavior = fakeDataCaptureEventBehavior {
                RemoteConfig(disabledMessageTypes = setOf("session"))
            }
            harness.recordSession()
            val messages = harness.getSentSessionMessages()
            assertEquals(0, messages.size)
        }
    }

    @Test
    fun `cold start and session number are recorded correctly`() {
        with(testRule) {
            val first = checkNotNull(harness.recordSession())
            val second = checkNotNull(harness.recordSession())
            val third = checkNotNull(harness.recordSession())

            assertEquals(1, first.session.number)
            assertEquals(2, second.session.number)
            assertEquals(3, third.session.number)
            assertTrue(first.session.isColdStart)
            assertFalse(second.session.isColdStart)
            assertFalse(third.session.isColdStart)
        }
    }
}
