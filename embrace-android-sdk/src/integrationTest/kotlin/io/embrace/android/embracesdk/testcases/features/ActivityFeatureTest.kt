package io.embrace.android.embracesdk.testcases.features

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.fakes.FakeBreadcrumbBehavior
import io.embrace.android.embracesdk.findSpansOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
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
        var startTimeMs: Long = 0

        testRule.runTest(
            setupAction = {
                overriddenConfigService.breadcrumbBehavior = FakeBreadcrumbBehavior(
                    automaticActivityCaptureEnabled = true
                )
            },
            testCaseAction = {
                startSdk()
                recordSession(simulateActivityCreation = true) {
                    startTimeMs = clock.now()
                }
            },
            assertAction = {
                val message = getSingleSession()
                val viewSpans = message.findSpansOfType(EmbType.Ux.View)
                assertEquals(1, viewSpans.size)

                with(viewSpans[0]) {
                    assertEquals(
                        "android.app.Activity",
                        attributes?.findAttributeValue("view.name")
                    )
                    assertEquals(startTimeMs, startTimeNanos?.nanosToMillis())
                    assertEquals(startTimeMs + 30000L, endTimeNanos?.nanosToMillis())
                }
            }
        )
    }
}