package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getSentSessions
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.findSpanSnapshotsOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.opentelemetry.embColdStart
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionNumber
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
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
    fun `cold start and session number are recorded correctly`() {
        with(testRule) {
            harness.recordSession()
            harness.recordSession()
            harness.recordSession()
            val sessions = harness.getSentSessions(3)
            val first = sessions[0]
            val second = sessions[1]
            val third = sessions[2]

            val firstAttrs = checkNotNull(first.findSessionSpan().attributes)
            assertEquals("1", firstAttrs.findAttributeValue(embSessionNumber.name))
            assertTrue(firstAttrs.findAttributeValue(embColdStart.name).toBoolean())
            assertEquals(0, first.findSpanSnapshotsOfType(EmbType.Ux.Session).size)

            val secondAttrs = checkNotNull(second.findSessionSpan().attributes)
            assertEquals("2", secondAttrs.findAttributeValue(embSessionNumber.name))
            assertFalse(secondAttrs.findAttributeValue(embColdStart.name).toBoolean())
            assertEquals(0, second.findSpanSnapshotsOfType(EmbType.Ux.Session).size)

            val thirdAttrs = checkNotNull(third.findSessionSpan().attributes)
            assertEquals("3", thirdAttrs.findAttributeValue(embSessionNumber.name))
            assertFalse(thirdAttrs.findAttributeValue(embColdStart.name).toBoolean())
            assertEquals(0, third.findSpanSnapshotsOfType(EmbType.Ux.Session).size)
        }
    }
}
