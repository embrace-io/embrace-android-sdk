package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getSentBackgroundActivities
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.opentelemetry.embSessionNumber
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.getSessionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that a stateful background activity can be recorded.
 */
@RunWith(AndroidJUnit4::class)
internal class BackgroundActivityTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `bg activity messages are recorded`() {
        with(testRule) {
            harness.recordSession()
            harness.overriddenClock.tick(30000)
            harness.recordSession()

            // filter out dupes from overwritten saves
            val bgActivities = harness.getSentBackgroundActivities().distinctBy { it.getSessionId() }
            assertEquals(2, bgActivities.size)

            // verify first bg activity
            val first = bgActivities[0]
            val firstAttrs = checkNotNull(first.findSessionSpan().attributes)
            assertEquals("1", firstAttrs.findAttributeValue(embSessionNumber.name))

            // verify second bg activity
            val second = bgActivities[1]
            val secondAttrs = checkNotNull(second.findSessionSpan().attributes)
            assertEquals("2", secondAttrs.findAttributeValue(embSessionNumber.name))

            // ID should be different for each
            assertNotEquals(first.getSessionId(), second.getSessionId())
        }
    }
}
