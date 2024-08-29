package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.opentelemetry.embColdStart
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionNumber
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
            val first = checkNotNull(harness.recordSession())
            val second = checkNotNull(harness.recordSession())
            val third = checkNotNull(harness.recordSession())

            val firstAttrs = checkNotNull(first.findSessionSpan().attributes)
            assertEquals("1", firstAttrs.findAttributeValue(embSessionNumber.name))
            assertTrue(firstAttrs.findAttributeValue(embColdStart.name).toBoolean())

            val secondAttrs = checkNotNull(second.findSessionSpan().attributes)
            assertEquals("2", secondAttrs.findAttributeValue(embSessionNumber.name))
            assertFalse(secondAttrs.findAttributeValue(embColdStart.name).toBoolean())

            val thirdAttrs = checkNotNull(third.findSessionSpan().attributes)
            assertEquals("3", thirdAttrs.findAttributeValue(embSessionNumber.name))
            assertFalse(thirdAttrs.findAttributeValue(embColdStart.name).toBoolean())
        }
    }
}
