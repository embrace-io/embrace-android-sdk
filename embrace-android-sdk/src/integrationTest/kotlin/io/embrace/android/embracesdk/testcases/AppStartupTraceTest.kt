package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.arch.assertError
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.fakes.FakeActivity
import io.embrace.android.embracesdk.fakes.FakeSplashScreenActivity
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.toEmbracePayload
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.ACTIVITY_GAP
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.LIFECYCLE_EVENT_GAP
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.POST_ACTIVITY_ACTION_DWELL
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(ignoredInternalErrors = emptyList())
    }

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
                with(awaitSpansWithType(7, EmbType.Performance.Default).associateBy { it.name }) {
                    assertEquals("yes", coldAppStartupRootSpan().attributes.toEmbracePayload().findAttributeValue("custom-attribute"))
                    assertNotNull(embraceInitSpan())
                    with(initGapSpan()) {
                        assertEquals(sdkStartTimeMs, startEpochNanos.nanosToMillis())
                        assertEquals(activityInitStartMs, endEpochNanos.nanosToMillis())
                    }
                    assertNotNull(getSpan("custom-span"))
                    with(getSpan("custom-span-with-stuff")) {
                        val attributesList = attributes.toEmbracePayload()
                        assertEquals("attribute", attributesList.findAttributeValue("custom"))
                        assertEquals(true, attributesList.hasEmbraceAttribute(ErrorCodeAttribute.Failure))
                        assertNotNull(events?.single())
                        assertEquals(Span.Status.ERROR.name, status.statusCode.name)
                    }
                    assertNotNull(activityInitSpan())
                    assertNotNull(activityResumeSpan())
                }
            }
        )
    }

    @Test
    fun `cold startup manually ended`() {
        val appLoadingWait = 2400L
        var traceEndTimeMs: Long? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = true,
                    endStartupWithAppReady = true
                )
            ),
            testCaseAction = {
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    endInBackground = false,
                )
                traceEndTimeMs = clock.tick(appLoadingWait)
                embrace.appReady()
            },
            otelExportAssertion = {
                with(awaitSpansWithType(6, EmbType.Performance.Default).associateBy { it.name }) {
                    assertEquals(traceEndTimeMs, coldAppStartupRootSpan().endEpochNanos.nanosToMillis())
                    with(appReadySpan()) {
                        assertEquals(activityResumeSpan().endEpochNanos, startEpochNanos)
                        assertEquals(traceEndTimeMs, endEpochNanos.nanosToMillis())
                    }
                }
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
                with(awaitSpansWithType(3, EmbType.Performance.Default).associateBy { it.name }) {
                    assertEquals(startupActivityInitMs, warmAppStartupRootSpan().startEpochNanos.nanosToMillis())
                    assertEquals(startupActivityInitMs, activityInitSpan().startEpochNanos.nanosToMillis())
                    assertNotNull(activityResumeSpan())
                }
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
                with(awaitSpansWithType(5, EmbType.Performance.Default).associateBy { it.name }) {
                    assertNotNull(coldAppStartupRootSpan())
                    assertNotNull(embraceInitSpan())
                    with(initGapSpan()) {
                        assertEquals(sdkStartTimeMs, startEpochNanos.nanosToMillis())
                        assertEquals(firstActivityInitMs, endEpochNanos.nanosToMillis())
                    }
                    assertEquals(startupActivityInitMs, activityInitSpan().startEpochNanos.nanosToMillis())
                    assertNotNull(activityResumeSpan())
                }
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
                with(awaitSpansWithType(3, EmbType.Performance.Default).associateBy { it.name }) {
                    assertEquals(firstActivityInitMs, warmAppStartupRootSpan().startEpochNanos.nanosToMillis())
                    assertEquals(startupActivityInitMs, activityInitSpan().startEpochNanos.nanosToMillis())
                    assertNotNull(activityResumeSpan())
                }
            }
        )
    }

    @Test
    fun `applicationInitStart changes start time in L if applicationInitEnd also called`() {
        var applicationStartTimeMs: Long? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = true
                )
            ),
            preSdkStartAction = {
                applicationStartTimeMs = clock.now()
                embrace.applicationInitStart()
                clock.tick(13)
            },
            testCaseAction = {
                clock.tick(44)
                embrace.applicationInitEnd()
                clock.tick(33)
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    endInBackground = false,
                )
            },
            otelExportAssertion = {
                with(awaitSpansWithType(6, EmbType.Performance.Default).associateBy { it.name }) {
                    assertEquals(applicationStartTimeMs, coldAppStartupRootSpan().startEpochNanos.nanosToMillis())
                    assertEquals(applicationStartTimeMs, processInitSpan().startEpochNanos.nanosToMillis())
                }
            }
        )
    }


    @Test
    fun `start time in L matches SDK startup time if applicationInitEnd is not called`() {
        var sdkStartTime: Long? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = true
                )
            ),
            preSdkStartAction = {
                embrace.applicationInitStart()
                clock.tick(13)
            },
            testCaseAction = {
                sdkStartTime = clock.now()
                clock.tick(44)
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    endInBackground = false,
                )
            },
            otelExportAssertion = {
                with(awaitSpansWithType(5, EmbType.Performance.Default).associateBy { it.name }) {
                    assertEquals(sdkStartTime, coldAppStartupRootSpan().startEpochNanos.nanosToMillis())
                }
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
                with(awaitSpansWithType(6, EmbType.Performance.Default).associateBy { it.name }) {
                    assertEquals(applicationEndTimeMs, processInitSpan().endEpochNanos.nanosToMillis())
                    with(initGapSpan()) {
                        assertEquals(applicationEndTimeMs, startEpochNanos.nanosToMillis())
                        assertEquals(activityInitStartMs, endEpochNanos.nanosToMillis())
                    }
                }
            }
        )
    }

    @Test
    fun `cold startup waiting for app ready ended by a crash`() {
        var crashTimestamp: Long? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = true,
                    endStartupWithAppReady = true
                )
            ),
            testCaseAction = {
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    endInBackground = false,
                )
                crashTimestamp = clock.now()
                simulateJvmUncaughtException(RuntimeException())
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val spans = session.findSpansOfType(EmbType.Performance.Default).associateBy { it.name }
                with(checkNotNull(spans["emb-app-startup-cold"])) {
                    assertError(ErrorCode.FAILURE)
                    assertEquals(crashTimestamp, checkNotNull(endTimeNanos).nanosToMillis())
                }

            }
        )
    }

    @Test
    fun `app backgrounded during cold startup while waiting for app ready`() {
        var backgroundTime: Long? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = true,
                    endStartupWithAppReady = true
                )
            ),
            testCaseAction = {
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    endInBackground = true,
                )
                backgroundTime = clock.now()
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val spans = session.findSpansOfType(EmbType.Performance.Default).associateBy { it.name }
                with(checkNotNull(spans["emb-app-startup-cold"])) {
                    assertError(ErrorCode.USER_ABANDON)
                    assertEquals(backgroundTime, checkNotNull(endTimeNanos).nanosToMillis())
                }

            }
        )
    }

    @Test
    fun `warm startup waiting for app ready ended by a crash`() {
        var startupActivityInitMs: Long? = null
        var crashTimestamp: Long? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = true,
                    endStartupWithAppReady = true
                )
            ),
            testCaseAction = {
                clock.tick(10000L)
                startupActivityInitMs = clock.now()
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    endInBackground = false
                )
                crashTimestamp = clock.now()
                simulateJvmUncaughtException(RuntimeException())
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val spans = session.findSpansOfType(EmbType.Performance.Default).associateBy { it.name }
                with(checkNotNull(spans["emb-app-startup-warm"])) {
                    assertError(ErrorCode.FAILURE)
                    assertEquals(startupActivityInitMs, checkNotNull(startTimeNanos).nanosToMillis())
                    assertEquals(crashTimestamp, checkNotNull(endTimeNanos).nanosToMillis())
                }
            }
        )
    }

    @Test
    fun `app backgrounded during warm startup while waiting for app ready`() {
        var startupActivityInitMs: Long? = null
        var backgroundTime: Long? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = true,
                    endStartupWithAppReady = true
                )
            ),
            testCaseAction = {
                clock.tick(10000L)
                startupActivityInitMs = clock.now()
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    endInBackground = true,
                )
                backgroundTime = clock.now()
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val spans = session.findSpansOfType(EmbType.Performance.Default).associateBy { it.name }
                with(checkNotNull(spans["emb-app-startup-warm"])) {
                    assertError(ErrorCode.USER_ABANDON)
                    assertEquals(startupActivityInitMs, checkNotNull(startTimeNanos).nanosToMillis())
                    assertEquals(backgroundTime, checkNotNull(endTimeNanos).nanosToMillis())
                }

            }
        )
    }

    private fun Map<String, OtelJavaSpanData?>.coldAppStartupRootSpan() = getSpan("emb-app-startup-cold")
    private fun Map<String, OtelJavaSpanData?>.warmAppStartupRootSpan() = getSpan("emb-app-startup-warm")
    private fun Map<String, OtelJavaSpanData?>.processInitSpan() = getSpan("emb-process-init")
    private fun Map<String, OtelJavaSpanData?>.embraceInitSpan() = getSpan("emb-embrace-init")
    private fun Map<String, OtelJavaSpanData?>.initGapSpan() = getSpan("emb-activity-init-delay")
    private fun Map<String, OtelJavaSpanData?>.activityInitSpan() = getSpan("emb-activity-init")
    private fun Map<String, OtelJavaSpanData?>.activityResumeSpan() = getSpan("emb-activity-load")
    private fun Map<String, OtelJavaSpanData?>.appReadySpan() = getSpan("emb-app-ready")
    private fun Map<String, OtelJavaSpanData?>.getSpan(name: String) = this[name] ?: error("Span missing")
}
