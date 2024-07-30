package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.payload.EventType
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.getSessionId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that moments (including startup moments) remain between session boundaries.
 */
@RunWith(AndroidJUnit4::class)
internal class MomentBoundaryTest {

    companion object {
        private const val MOMENT_NAME = "my_moment"
    }

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `startup moment completes within one session`() {
        with(testRule) {
            val message = harness.recordSession {
                embrace.endAppStartup()
                embrace.startMoment(MOMENT_NAME)
                embrace.endMoment(MOMENT_NAME)
            }
            checkNotNull(message)

            val moments = fetchDeliveredEvents()
            assertEquals(4, moments.size)
            val startMoment = moments[0]
            val endMoment = moments[1]
            val myStartMoment = moments[2]
            val myEndMoment = moments[3]

            assertEquals("_startup", startMoment.event.name)
            assertEquals(EventType.START, startMoment.event.type)
            assertEquals(message.getSessionId(), startMoment.event.sessionId)

            assertEquals("_startup", endMoment.event.name)
            assertEquals(EventType.END, endMoment.event.type)
            assertEquals(message.getSessionId(), endMoment.event.sessionId)

            assertEquals(MOMENT_NAME, myStartMoment.event.name)
            assertEquals(EventType.START, myStartMoment.event.type)
            assertEquals(message.getSessionId(), myStartMoment.event.sessionId)

            assertEquals(MOMENT_NAME, myEndMoment.event.name)
            assertEquals(EventType.END, myEndMoment.event.type)
            assertEquals(message.getSessionId(), myEndMoment.event.sessionId)
        }
    }

    @Test
    fun `startup moment completes within two sessions`() {
        with(testRule) {
            val firstMessage = harness.recordSession {
                embrace.startMoment(MOMENT_NAME)
            }
            val secondMessage = harness.recordSession {
                embrace.endAppStartup()
                embrace.endMoment(MOMENT_NAME)
            }
            checkNotNull(firstMessage)
            checkNotNull(secondMessage)

            val moments = fetchDeliveredEvents()
            assertEquals(4, moments.size)
            val startMoment = moments[0]
            val myStartMoment = moments[1]
            val endMoment = moments[2]
            val myEndMoment = moments[3]

            assertEquals("_startup", startMoment.event.name)
            assertEquals(EventType.START, startMoment.event.type)
            assertEquals(firstMessage.getSessionId(), startMoment.event.sessionId)

            assertEquals(MOMENT_NAME, myStartMoment.event.name)
            assertEquals(EventType.START, myStartMoment.event.type)
            assertEquals(firstMessage.getSessionId(), myStartMoment.event.sessionId)

            assertEquals("_startup", endMoment.event.name)
            assertEquals(EventType.END, endMoment.event.type)
            assertEquals(secondMessage.getSessionId(), endMoment.event.sessionId)

            assertEquals(MOMENT_NAME, myEndMoment.event.name)
            assertEquals(EventType.END, myEndMoment.event.type)
            assertEquals(secondMessage.getSessionId(), myEndMoment.event.sessionId)
        }
    }

    private fun IntegrationTestRule.fetchDeliveredEvents() =
        harness.overriddenDeliveryModule.deliveryService.sentMoments
}
