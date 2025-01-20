package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.internal.spans.toStatus
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
        var sdkStartTimeMs: Long? = null
        var activityInitStartMs: Long? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = true
                )
            ),
            testCaseAction = {
                sdkStartTimeMs = clock.now()
                val customStartTimeMs = clock.tick()
                val customEndTimeMs = clock.tick(95L)
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
                clock.tick(55)
                activityInitStartMs = clock.now()
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    endInBackground = false,
                )
            },
            otelExportAssertion = {
                val spans = awaitSpansWithType(7, EmbType.Performance.Default).associateBy { it.name }
                assertTrue(spans.isNotEmpty())
                with(checkNotNull(spans["emb-cold-time-to-initial-display"])) {
                    assertEquals("yes", attributes.toNewPayload().findAttributeValue("custom-attribute"))
                }
                assertTrue(spans.containsKey("emb-embrace-init"))
                with(checkNotNull(spans["emb-activity-init-gap"])) {
                    assertEquals(sdkStartTimeMs, startEpochNanos.nanosToMillis())
                    assertEquals(activityInitStartMs, endEpochNanos.nanosToMillis())
                }
                assertTrue(spans.containsKey("custom-span"))
                with(checkNotNull(spans["custom-span-with-stuff"])) {
                    val attributesList = attributes.toNewPayload()
                    assertEquals("attribute", attributesList.findAttributeValue("custom"))
                    assertEquals(true, attributesList.hasFixedAttribute(ErrorCodeAttribute.Failure))
                    assertNotNull(events?.single())
                    assertEquals(Span.Status.ERROR, status.statusCode.toStatus())
                }
                assertTrue(spans.containsKey("emb-activity-create"))
                assertTrue(spans.containsKey("emb-activity-resume"))
            }
        )
    }

    @Test
    fun `applicationInitEnd call adds extra information`() {
        var applicationEndTimeMs: Long? = null
        var activityInitStartMs: Long? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = true
                )
            ),
            testCaseAction = {
                clock.tick(44)
                applicationEndTimeMs = clock.now()
                embrace.applicationInitEnd()
                clock.tick(33)
                activityInitStartMs = clock.now()
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    endInBackground = false,
                )
            },
            otelExportAssertion = {
                val spans = awaitSpansWithType(6, EmbType.Performance.Default).associateBy { it.name }
                with(checkNotNull(spans["emb-process-init"])) {
                    assertEquals(applicationEndTimeMs, endEpochNanos.nanosToMillis())
                }
                with(checkNotNull(spans["emb-activity-init-gap"])) {
                    assertEquals(applicationEndTimeMs, startEpochNanos.nanosToMillis())
                    assertEquals(activityInitStartMs, endEpochNanos.nanosToMillis())
                }
            }
        )
    }
}
