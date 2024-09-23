package io.embrace.android.embracesdk.features

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.fakes.FakeBreadcrumbBehavior
import io.embrace.android.embracesdk.findSpansOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class ActivityFeatureTest {

    @Rule
    @JvmField
    val testRule = IntegrationTestRule { IntegrationTestRule.Harness(startImmediately = false) }

    @Test
    fun `automatically capture activities`() {
        with(testRule) {
            harness.overriddenConfigService.breadcrumbBehavior = FakeBreadcrumbBehavior(
                automaticActivityCaptureEnabled = true
            )
            startSdk()
            var startTimeMs: Long = 0
            val message = checkNotNull(harness.recordSession(simulateActivityCreation = true) {
                startTimeMs = harness.overriddenClock.now()
            })

            val viewSpans = message.findSpansOfType(EmbType.Ux.View)
            assertEquals(1, viewSpans.size)

            val span1 = viewSpans[0]

            with(span1) {
                assertEquals("android.app.Activity", attributes?.findAttributeValue("view.name"))
                assertEquals(startTimeMs, startTimeNanos?.nanosToMillis())
                assertEquals(startTimeMs + 30000L, endTimeNanos?.nanosToMillis())
            }
        }
    }
}