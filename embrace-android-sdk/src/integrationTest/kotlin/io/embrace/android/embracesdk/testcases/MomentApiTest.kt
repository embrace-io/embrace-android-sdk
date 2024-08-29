package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.payload.EventType
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.getSentMoments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val MOMENT_NAME = "my_moment"

@RunWith(AndroidJUnit4::class)
internal class MomentApiTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    /**
     * Verifies that a custom moment is sent by the SDK.
     */
    @Test
    fun customMomentTest() {
        val delay = 5000L
        val props = mapOf("key" to "value")

        with(testRule) {
            // Send start moment
            val startTime = harness.overriddenClock.now()
            embrace.startMoment(MOMENT_NAME, null, props)
            harness.overriddenClock.tick(delay)
            embrace.endMoment(MOMENT_NAME)

            // retrieve payloads
            val messages = harness.getSentMoments(2)
            assertEquals(2, messages.size)
            val startMoment = messages[0].event
            val endMoment = messages[1].event

            // validate start moment
            assertEquals(MOMENT_NAME, startMoment.name)
            assertNull(startMoment.messageId)
            assertEquals(EventType.START, startMoment.type)
            assertEquals(startTime, startMoment.timestamp)
            assertEquals(props, startMoment.customProperties)

            // validate end moment
            assertEquals(MOMENT_NAME, endMoment.name)
            assertNull(endMoment.messageId)
            assertEquals(EventType.END, endMoment.type)
            assertEquals(startTime + delay, endMoment.timestamp)
            assertNull(endMoment.customProperties)

            // validate shared attributes
            assertEquals(startMoment.eventId, endMoment.eventId)
            assertEquals(startMoment.sessionId, endMoment.sessionId)
        }
    }
}
