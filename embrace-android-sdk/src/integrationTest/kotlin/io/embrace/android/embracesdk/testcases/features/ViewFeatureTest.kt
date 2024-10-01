package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.findSpansOfType
import io.embrace.android.embracesdk.getSentSessions
import io.embrace.android.embracesdk.getSingleSession
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ViewFeatureTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `view feature`() {
        with(testRule) {
            var startTimeMs: Long = 0
            harness.recordSession {
                startTimeMs = harness.overriddenClock.now()
                embrace.startView("MyView")
                harness.overriddenClock.tick(1000L)
                embrace.startView("AnotherView")
                harness.overriddenClock.tick(2000L)
                embrace.endView("MyView")
                embrace.endView("AnotherView")
            }

            val message = harness.getSingleSession()
            val viewSpans = message.findSpansOfType(EmbType.Ux.View)
            assertEquals(2, viewSpans.size)

            val span1 = viewSpans[0]

            with(span1) {
                assertEquals("MyView", attributes?.findAttributeValue("view.name"))
                assertEquals(startTimeMs, startTimeNanos?.nanosToMillis())
                assertEquals(startTimeMs + 3000L, endTimeNanos?.nanosToMillis())
            }

            val span2 = viewSpans[1]
            with(span2) {
                assertEquals("AnotherView", attributes?.findAttributeValue("view.name"))
                assertEquals(startTimeMs + 1000L, startTimeNanos?.nanosToMillis())
                assertEquals(startTimeMs + 3000L, endTimeNanos?.nanosToMillis())
            }
        }
    }
}
