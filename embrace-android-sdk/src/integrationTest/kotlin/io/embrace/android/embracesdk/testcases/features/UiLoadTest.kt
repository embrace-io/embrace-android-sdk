package io.embrace.android.embracesdk.testcases.features

import android.app.Activity
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.annotation.CustomTracedActivity
import io.embrace.android.embracesdk.annotation.NotTracedActivity
import io.embrace.android.embracesdk.annotation.TracedActivity
import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.assertions.findSpansByName
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.capture.activity.LifecycleStage
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.opentelemetry.api.trace.SpanId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
internal class UiLoadTest {

    @Rule
    @JvmField
    val testRule = IntegrationTestRule()

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `activity open does not create a trace if feature flag is disabled`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(uiLoadPerfCapture = false, bgActivityCapture = true)
            ),
            testCaseAction = {
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    activitiesAndActions = listOf(
                        Robolectric.buildActivity(Activity1::class.java) to {},
                        Robolectric.buildActivity(Activity2::class.java) to {},
                    )
                )
            },
            assertAction = {
                assertTrue(getSingleSessionEnvelope().findSpansOfType(EmbType.Performance.UiLoad).isEmpty())
            }
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `activity open creates a trace for unannotated activity if auto capture enabled`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    uiLoadPerfCapture = true,
                    uiLoadPerfAutoCapture = true,
                    bgActivityCapture = true
                )
            ),
            testCaseAction = {
                simulateOpeningActivities(
                    activitiesAndActions = listOf(
                        Robolectric.buildActivity(Activity::class.java) to {},
                    )
                )
            },
            assertAction = {
                assertEquals(1, getSingleSessionEnvelope().findSpansOfType(EmbType.Performance.UiLoad).size)
            }
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `activity open creates a trace for manually ended load`() {
        var preLaunchTimeMs = 0L
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    uiLoadPerfCapture = true,
                    uiLoadPerfAutoCapture = false,
                    bgActivityCapture = true
                )
            ),
            setupAction = {
                preLaunchTimeMs = overriddenClock.now()
            },
            testCaseAction = {
                simulateOpeningActivities(
                    invokeManualEnd = true,
                    activitiesAndActions = listOf(
                        Robolectric.buildActivity(ManualStopActivity::class.java) to {},
                    )
                )
            },
            assertAction = {
                with(getSingleSessionEnvelope()) {
                    val trace = findSpansOfType(EmbType.Performance.UiLoad).single()
                    assertEquals("emb-$MANUAL_STOP_ACTIVITY_NAME-cold-time-to-initial-display", trace.name)

                    val expectedTraceStartTime = preLaunchTimeMs + 50
                    assertEmbraceSpanData(
                        span = trace,
                        expectedStartTimeMs = expectedTraceStartTime,
                        expectedEndTimeMs = expectedTraceStartTime + 400,
                        expectedParentId = SpanId.getInvalid(),
                    )
                }
            }
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `opening activity configured to be ended manually creates an abandoned trace if activityLoaded not invoked`() {
        var preLaunchTimeMs = 0L
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    uiLoadPerfCapture = true,
                    uiLoadPerfAutoCapture = false,
                    bgActivityCapture = true
                )
            ),
            setupAction = {
                preLaunchTimeMs = overriddenClock.now()
            },
            testCaseAction = {
                simulateOpeningActivities(
                    activitiesAndActions = listOf(
                        Robolectric.buildActivity(ManualStopActivity::class.java) to {},
                    )
                )
            },
            assertAction = {
                with(getSingleSessionEnvelope()) {
                    val trace = findSpansOfType(EmbType.Performance.UiLoad).single()
                    assertEquals("emb-$MANUAL_STOP_ACTIVITY_NAME-cold-time-to-initial-display", trace.name)

                    val expectedTraceStartTime = preLaunchTimeMs + 50
                    assertEmbraceSpanData(
                        span = trace,
                        expectedStartTimeMs = expectedTraceStartTime,
                        expectedEndTimeMs = expectedTraceStartTime + 20301,
                        expectedParentId = SpanId.getInvalid(),
                        expectedStatus = Span.Status.ERROR,
                        expectedErrorCode = ErrorCode.USER_ABANDON,
                    )
                }
            }
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `activity open does not create a trace for explicitly disabled activity even if auto capture enabled`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    uiLoadPerfCapture = true,
                    uiLoadPerfAutoCapture = true,
                    bgActivityCapture = true
                )
            ),
            testCaseAction = {
                simulateOpeningActivities(
                    activitiesAndActions = listOf(
                        Robolectric.buildActivity(IgnoredActivity::class.java) to {},
                    )
                )
            },
            assertAction = {
                assertTrue(getSingleSessionEnvelope().findSpansOfType(EmbType.Performance.UiLoad).isEmpty())
            }
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `opening first non-startup activity creates cold ui load trace in L`() {
        var preLaunchTimeMs = 0L
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(uiLoadPerfCapture = true, bgActivityCapture = true)
            ),
            setupAction = {
                preLaunchTimeMs = overriddenClock.now()
            },
            testCaseAction = {
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    activitiesAndActions = listOf(
                        Robolectric.buildActivity(Activity1::class.java) to {},
                        Robolectric.buildActivity(Activity2::class.java) to {},
                    )
                )
            },
            assertAction = {
                val payload = getSingleSessionEnvelope()
                val trace = payload.findSpansOfType(EmbType.Performance.UiLoad).single()
                val rootSpanId = checkNotNull(trace.spanId)
                assertEquals("emb-$ACTIVITY2_NAME-cold-time-to-initial-display", trace.name)

                val expectedTraceStartTime = preLaunchTimeMs + 20351
                assertEmbraceSpanData(
                    span = trace,
                    expectedStartTimeMs = expectedTraceStartTime,
                    expectedEndTimeMs = expectedTraceStartTime + 200,
                    expectedParentId = SpanId.getInvalid(),
                )

                assertEmbraceSpanData(
                    span = payload.findSpansByName("emb-${LifecycleStage.CREATE.spanName(ACTIVITY2_NAME)}").single(),
                    expectedStartTimeMs = expectedTraceStartTime,
                    expectedEndTimeMs = expectedTraceStartTime + 100,
                    expectedParentId = rootSpanId,
                )

                assertEmbraceSpanData(
                    span = payload.findSpansByName("emb-${LifecycleStage.START.spanName(ACTIVITY2_NAME)}").single(),
                    expectedStartTimeMs = expectedTraceStartTime + 100,
                    expectedEndTimeMs = expectedTraceStartTime + 200,
                    expectedParentId = rootSpanId,
                )
            }
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `foregrounding and initializing new activity creates cold ui load trace in L`() {
        var preLaunchTimeMs = 0L
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(uiLoadPerfCapture = true, bgActivityCapture = true)
            ),
            setupAction = {
                preLaunchTimeMs = overriddenClock.now()
            },
            testCaseAction = {
                simulateOpeningActivities(
                    addStartupActivity = true,
                    startInBackground = true,
                    activitiesAndActions = listOf(
                        Robolectric.buildActivity(Activity1::class.java) to {}
                    )
                )
            },
            assertAction = {
                val payloads = getSessionEnvelopes(2)
                val payload = payloads[1]
                val trace = payload.findSpansOfType(EmbType.Performance.UiLoad).single()
                assertEquals("emb-$ACTIVITY1_NAME-cold-time-to-initial-display", trace.name)
                val rootSpanId = checkNotNull(trace.spanId)

                val expectedTraceStartTime = preLaunchTimeMs + 10000
                assertEmbraceSpanData(
                    span = trace,
                    expectedStartTimeMs = expectedTraceStartTime,
                    expectedEndTimeMs = expectedTraceStartTime + 200,
                    expectedParentId = SpanId.getInvalid(),
                )

                val lastBackgroundActivity = getSessionEnvelopes(2, ApplicationState.BACKGROUND)[1]
                assertEmbraceSpanData(
                    span = lastBackgroundActivity
                        .findSpansByName("emb-${LifecycleStage.CREATE.spanName(ACTIVITY1_NAME)}")
                        .single(),
                    expectedStartTimeMs = expectedTraceStartTime,
                    expectedEndTimeMs = expectedTraceStartTime + 100,
                    expectedParentId = rootSpanId,
                )

                assertEmbraceSpanData(
                    span = payload.findSpansByName("emb-${LifecycleStage.START.spanName(ACTIVITY1_NAME)}").single(),
                    expectedStartTimeMs = expectedTraceStartTime + 100,
                    expectedEndTimeMs = expectedTraceStartTime + 200,
                    expectedParentId = rootSpanId,
                )
            }
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `foregrounding already-created activity creates hot ui load trace in L`() {
        var preLaunchTimeMs = 0L
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(uiLoadPerfCapture = true, bgActivityCapture = true)
            ),
            setupAction = {
                preLaunchTimeMs = overriddenClock.now()
            },
            testCaseAction = {
                simulateOpeningActivities(
                    addStartupActivity = true,
                    startInBackground = true,
                    createFirstActivity = false,
                    activitiesAndActions = listOf(
                        Robolectric.buildActivity(Activity1::class.java) to {},
                    )
                )
            },
            assertAction = {
                val payload = getSessionEnvelopes(2)[1]
                val trace = payload.findSpansOfType(EmbType.Performance.UiLoad).single()
                assertEquals("emb-$ACTIVITY1_NAME-hot-time-to-initial-display", trace.name)
                val rootSpanId = checkNotNull(trace.spanId)

                val expectedTraceStartTime = preLaunchTimeMs + 10000
                assertEmbraceSpanData(
                    span = trace,
                    expectedStartTimeMs = expectedTraceStartTime,
                    expectedEndTimeMs = expectedTraceStartTime + 100,
                    expectedParentId = SpanId.getInvalid(),
                )

                assertEmbraceSpanData(
                    span = payload.findSpansByName("emb-${LifecycleStage.START.spanName(ACTIVITY1_NAME)}").single(),
                    expectedStartTimeMs = expectedTraceStartTime,
                    expectedEndTimeMs = expectedTraceStartTime + 100,
                    expectedParentId = rootSpanId,
                )
            }
        )
    }

    private companion object {
        @TracedActivity
        class Activity1 : Activity()

        @TracedActivity
        class Activity2 : Activity()

        @NotTracedActivity
        class IgnoredActivity : Activity()

        @CustomTracedActivity
        class ManualStopActivity : Activity()

        val ACTIVITY1_NAME = Robolectric.buildActivity(Activity1::class.java).get().localClassName
        val ACTIVITY2_NAME = Robolectric.buildActivity(Activity2::class.java).get().localClassName
        val MANUAL_STOP_ACTIVITY_NAME = Robolectric.buildActivity(ManualStopActivity::class.java).get().localClassName
    }
}
