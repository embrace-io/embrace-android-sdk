package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
@RunWith(AndroidJUnit4::class)
internal class AppStartupTraceTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `startup spans recorded in foreground session when background activity is enabled`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = true
                )
            ),
            testCaseAction = {
                val customStartTimeMs = clock.now()
                val customEndTimeMs = clock.tick(100L)
                embrace.addStartupTraceChildSpan("custom-span", customStartTimeMs, customEndTimeMs)
                embrace.addStartupTraceChildSpan(
                    name = "custom-span-with-stuff",
                    startTimeMs = customStartTimeMs,
                    endTimeMs = customEndTimeMs,
                    attributes = mapOf("custom" to "attribute"),
                    events = listOf(
                        checkNotNull(
                            EmbraceSpanEvent.create(
                                name = "custom-event",
                                timestampMs = customEndTimeMs,
                                attributes = mapOf("custom" to "attribute")
                            )
                        )
                    ),
                    errorCode = ErrorCode.FAILURE
                )
                embrace.addStartupTraceAttribute("custom-attribute", "yes")
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true
                )
            },
            assertAction = {
                with(getSingleSessionEnvelope()) {
                    val spans = findSpansOfType(EmbType.Performance.Default).associateBy { it.name }
                    assertTrue(spans.isNotEmpty())
                    with(checkNotNull(spans["emb-cold-time-to-initial-display"])) {
                        assertEquals("yes", attributes?.findAttributeValue("custom-attribute"))
                    }
                    assertTrue(spans.containsKey("emb-embrace-init"))
                    assertTrue(spans.containsKey("custom-span"))
                    with(checkNotNull(spans["custom-span-with-stuff"])) {
                        assertEquals("attribute", attributes?.findAttributeValue("custom"))
                        assertEquals(true, attributes?.hasFixedAttribute(ErrorCodeAttribute.Failure))
                        assertNotNull(events?.single())
                        assertEquals(Span.Status.ERROR, status)
                    }
                    assertTrue(spans.containsKey("emb-activity-create"))
                    assertTrue(spans.containsKey("emb-activity-resume"))
                }
            }
        )
    }
}
