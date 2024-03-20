package io.embrace.android.embracesdk.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.arch.schema.SchemaKeys
import io.embrace.android.embracesdk.findEvent
import io.embrace.android.embracesdk.findEventAttribute
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class CustomBreadcrumbFeatureTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `custom breadcrumb feature`() {
        with(testRule) {
            var captureTime: Long = 0
            val message = checkNotNull(harness.recordSession {
                captureTime = harness.fakeClock.now()
                embrace.addBreadcrumb("Hello, world!")
            })

            val breadcrumb = checkNotNull(message.breadcrumbs?.customBreadcrumbs?.single())
            assertEquals("Hello, world!", breadcrumb.message)
            assertEquals(captureTime, breadcrumb.getStartTime())

            val span = message.findSessionSpan().findEvent(SchemaKeys.CUSTOM_BREADCRUMB)
            assertEquals("Hello, world!", span.findEventAttribute("message"))
        }
    }
}
