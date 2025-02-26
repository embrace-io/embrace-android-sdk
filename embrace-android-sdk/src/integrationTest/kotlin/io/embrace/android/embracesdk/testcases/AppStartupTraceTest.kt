package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeActivity
import io.embrace.android.embracesdk.fakes.FakeSplashScreenActivity
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
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.ACTIVITY_GAP
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.LIFECYCLE_EVENT_GAP
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.POST_ACTIVITY_ACTION_DWELL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
@RunWith(AndroidJUnit4::class)
internal class AppStartupTraceTest {
    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

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
                with(checkNotNull(spans["emb-app-startup-cold"])) {
                    assertEquals("yes", attributes.toNewPayload().findAttributeValue("custom-attribute"))
                }
                assertTrue(spans.containsKey("emb-embrace-init"))
                with(checkNotNull(spans["emb-activity-init-delay"])) {
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
                assertTrue(spans.containsKey("emb-activity-init"))
                assertTrue(spans.containsKey("emb-activity-load"))
            }
        )
    }

    @Test
    fun `warm startup`() {
        var startupActivityInitMs: Long? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = true
                )
            ),
            testCaseAction = {
                val initGap = 10000L
                clock.tick(initGap)
                startupActivityInitMs = clock.now()
                simulateOpeningActivities(
                    addStartupActivity = false
                )
            },
            otelExportAssertion = {
                val spans = awaitSpansWithType(3, EmbType.Performance.Default).associateBy { it.name }
                assertTrue(spans.isNotEmpty())
                with(checkNotNull(spans["emb-app-startup-warm"])) {
                    assertEquals(startupActivityInitMs, startEpochNanos.nanosToMillis())
                }
                with(checkNotNull(spans["emb-activity-init"])) {
                    assertEquals(startupActivityInitMs, startEpochNanos.nanosToMillis())
                }
                assertTrue(spans.containsKey("emb-activity-load"))
            }
        )
    }

    @Test
    fun `cold startup with long splash screen`() {
        var sdkStartTimeMs: Long? = null
        var firstActivityInitMs: Long? = null
        var startupActivityInitMs: Long? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = true
                )
            ),
            testCaseAction = {
                val splashScreenDwellTime = 5000L
                sdkStartTimeMs = clock.now()
                firstActivityInitMs = clock.tick()
                startupActivityInitMs = clock.now() + (3 * LIFECYCLE_EVENT_GAP) + POST_ACTIVITY_ACTION_DWELL +
                    ACTIVITY_GAP + splashScreenDwellTime
                simulateOpeningActivities(
                    addStartupActivity = false,
                    activitiesAndActions = listOf(
                        Robolectric.buildActivity(FakeSplashScreenActivity::class.java) to {
                            clock.tick(
                                splashScreenDwellTime
                            )
                        },
                        Robolectric.buildActivity(FakeActivity::class.java) to {},
                    )
                )
            },
            otelExportAssertion = {
                val spans = awaitSpansWithType(5, EmbType.Performance.Default).associateBy { it.name }
                assertTrue(spans.isNotEmpty())
                assertTrue(spans.containsKey("emb-app-startup-cold"))
                assertTrue(spans.containsKey("emb-embrace-init"))
                with(checkNotNull(spans["emb-activity-init-delay"])) {
                    assertEquals(sdkStartTimeMs, startEpochNanos.nanosToMillis())
                    assertEquals(firstActivityInitMs, endEpochNanos.nanosToMillis())
                }
                with(checkNotNull(spans["emb-activity-init"])) {
                    assertEquals(startupActivityInitMs, startEpochNanos.nanosToMillis())
                }
                assertTrue(spans.containsKey("emb-activity-load"))
            }
        )
    }

    @Test
    fun `warm startup with long splash screen`() {
        var firstActivityInitMs: Long? = null
        var startupActivityInitMs: Long? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = true
                )
            ),
            testCaseAction = {
                val initGap = 10000L
                val splashScreenDwellTime = 5000L
                firstActivityInitMs = clock.tick(initGap)
                startupActivityInitMs = clock.now() + (3 * LIFECYCLE_EVENT_GAP) + POST_ACTIVITY_ACTION_DWELL +
                    ACTIVITY_GAP + splashScreenDwellTime
                simulateOpeningActivities(
                    addStartupActivity = false,
                    activitiesAndActions = listOf(
                        Robolectric.buildActivity(FakeSplashScreenActivity::class.java) to {
                            clock.tick(
                                splashScreenDwellTime
                            )
                        },
                        Robolectric.buildActivity(FakeActivity::class.java) to {},
                    )
                )
            },
            otelExportAssertion = {
                val spans = awaitSpansWithType(3, EmbType.Performance.Default).associateBy { it.name }
                assertTrue(spans.isNotEmpty())
                with(checkNotNull(spans["emb-app-startup-warm"])) {
                    assertEquals(firstActivityInitMs, startEpochNanos.nanosToMillis())
                }
                with(checkNotNull(spans["emb-activity-init"])) {
                    assertEquals(startupActivityInitMs, startEpochNanos.nanosToMillis())
                }
                assertTrue(spans.containsKey("emb-activity-load"))
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
                with(checkNotNull(spans["emb-activity-init-delay"])) {
                    assertEquals(applicationEndTimeMs, startEpochNanos.nanosToMillis())
                    assertEquals(activityInitStartMs, endEpochNanos.nanosToMillis())
                }
            }
        )
    }
}
