package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.recordSession
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
            assertEquals(message.session.sessionId, startMoment.event.sessionId)

            assertEquals("_startup", endMoment.event.name)
            assertEquals(EventType.END, endMoment.event.type)
            assertEquals(message.session.sessionId, endMoment.event.sessionId)

            assertEquals(MOMENT_NAME, myStartMoment.event.name)
            assertEquals(EventType.START, myStartMoment.event.type)
            assertEquals(message.session.sessionId, myStartMoment.event.sessionId)

            assertEquals(MOMENT_NAME, myEndMoment.event.name)
            assertEquals(EventType.END, myEndMoment.event.type)
            assertEquals(message.session.sessionId, myEndMoment.event.sessionId)
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
            assertEquals(firstMessage.session.sessionId, startMoment.event.sessionId)

            assertEquals(MOMENT_NAME, myStartMoment.event.name)
            assertEquals(EventType.START, myStartMoment.event.type)
            assertEquals(firstMessage.session.sessionId, myStartMoment.event.sessionId)

            assertEquals("_startup", endMoment.event.name)
            assertEquals(EventType.END, endMoment.event.type)
            assertEquals(secondMessage.session.sessionId, endMoment.event.sessionId)

            assertEquals(MOMENT_NAME, myEndMoment.event.name)
            assertEquals(EventType.END, myEndMoment.event.type)
            assertEquals(secondMessage.session.sessionId, myEndMoment.event.sessionId)
        }
    }

    private fun IntegrationTestRule.fetchDeliveredEvents() =
        harness.overriddenDeliveryModule.deliveryService.sentMoments
}
