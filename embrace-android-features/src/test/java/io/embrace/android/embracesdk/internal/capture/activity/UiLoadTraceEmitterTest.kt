package io.embrace.android.embracesdk.internal.capture.activity

import android.os.Build.VERSION_CODES
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.SpanId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min

@RunWith(AndroidJUnit4::class)
internal class UiLoadTraceEmitterTest {
    private lateinit var clock: FakeClock
    private lateinit var spanSink: SpanSink
    private lateinit var spanService: SpanService
    private lateinit var traceEmitter: UiLoadTraceEmitter
    private var hasRenderEvent: Boolean = false
    private var hasPreAndPostEvents: Boolean = false

    @Before
    fun setUp() {
        clock = FakeClock()
        hasRenderEvent = hasRenderEvent(BuildVersionChecker)
        hasPreAndPostEvents = hasPrePostEvents(BuildVersionChecker)
        val initModule = FakeInitModule(clock = clock)
        spanSink = initModule.openTelemetryModule.spanSink
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.now())
        clock.tick(100L)
        traceEmitter = UiLoadTraceEmitter(
            spanService = spanService,
            versionChecker = BuildVersionChecker,
        )
    }

    @Config(sdk = [VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify cold ui load trace from another activity in U`() {
        verifyOpen(
            previousState = PreviousState.FROM_ACTIVITY,
            uiLoadType = UiLoadType.COLD,
        )
    }

    @Config(sdk = [VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify cold ui load trace from the same activity in U`() {
        verifyOpen(
            lastActivityName = ACTIVITY_NAME,
            previousState = PreviousState.FROM_ACTIVITY,
            uiLoadType = UiLoadType.COLD,
        )
    }

    @Config(sdk = [VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify cold ui load trace from an interrupted opening of another activity in U`() {
        verifyOpen(
            previousState = PreviousState.FROM_INTERRUPTED_LOAD,
            uiLoadType = UiLoadType.COLD,
        )
    }

    @Config(sdk = [VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify cold ui load trace from an interrupted opening of the same activity in U`() {
        verifyOpen(
            lastActivityName = ACTIVITY_NAME,
            previousState = PreviousState.FROM_INTERRUPTED_LOAD,
            uiLoadType = UiLoadType.COLD,
        )
    }

    @Config(sdk = [VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify cold open trace from background in U`() {
        verifyOpen(
            previousState = PreviousState.FROM_BACKGROUND,
            uiLoadType = UiLoadType.COLD,
        )
    }

    @Config(sdk = [VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify hot ui load trace in from background in U`() {
        verifyOpen(
            previousState = PreviousState.FROM_BACKGROUND,
            uiLoadType = UiLoadType.HOT,
        )
    }

    @Config(sdk = [VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify cold ui load trace to be ended manually in U`() {
        verifyOpen(
            previousState = PreviousState.FROM_ACTIVITY,
            uiLoadType = UiLoadType.COLD,
            manualEnd = true,
        )
    }

    @Config(sdk = [VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify hot ui load trace to be ended manually in U`() {
        verifyOpen(
            previousState = PreviousState.FROM_ACTIVITY,
            uiLoadType = UiLoadType.HOT,
            manualEnd = true,
        )
    }

    @Config(sdk = [VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold ui load trace in from another activity L`() {
        verifyOpen(
            previousState = PreviousState.FROM_ACTIVITY,
            uiLoadType = UiLoadType.COLD,
        )
    }

    @Config(sdk = [VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold ui load trace from an interrupted opening of another activity in L`() {
        verifyOpen(
            previousState = PreviousState.FROM_INTERRUPTED_LOAD,
            uiLoadType = UiLoadType.COLD,
        )
    }

    @Config(sdk = [VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold ui load trace from an interrupted opening of the same activity in L`() {
        verifyOpen(
            lastActivityName = ACTIVITY_NAME,
            previousState = PreviousState.FROM_INTERRUPTED_LOAD,
            uiLoadType = UiLoadType.COLD,
        )
    }

    @Config(sdk = [VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold ui load trace from background in L`() {
        verifyOpen(
            previousState = PreviousState.FROM_BACKGROUND,
            uiLoadType = UiLoadType.COLD,
        )
    }

    @Config(sdk = [VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify hot ui load trace in L from background`() {
        verifyOpen(
            previousState = PreviousState.FROM_BACKGROUND,
            uiLoadType = UiLoadType.HOT,
        )
    }

    @Config(sdk = [VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold ui load trace to be ended manually in L`() {
        verifyOpen(
            previousState = PreviousState.FROM_ACTIVITY,
            uiLoadType = UiLoadType.COLD,
            manualEnd = true,
        )
    }

    @Config(sdk = [VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify hot ui load trace to be ended manually in L`() {
        verifyOpen(
            previousState = PreviousState.FROM_ACTIVITY,
            uiLoadType = UiLoadType.HOT,
            manualEnd = true,
        )
    }

    private fun verifyOpen(
        activityName: String = ACTIVITY_NAME,
        instanceId: Int = NEW_INSTANCE_ID,
        lastActivityName: String = LAST_ACTIVITY_NAME,
        lastInstanceId: Int = LAST_ACTIVITY_INSTANCE_ID,
        previousState: PreviousState,
        uiLoadType: UiLoadType,
        manualEnd: Boolean = false,
    ) {
        openActivity(
            activityName = activityName,
            instanceId = instanceId,
            lastActivityName = lastActivityName,
            lastInstanceId = lastInstanceId,
            previousState = previousState,
            uiLoadType = uiLoadType,
            manualEnd = manualEnd,
        ).let { timestamps ->
            val spanMap = spanSink.completedSpans().associateBy { it.name }
            val trace = checkNotNull(spanMap["emb-$activityName-${uiLoadType.typeName}-time-to-initial-display"])
            val expectedEvent = checkNotNull(
                EmbraceSpanEvent.create(
                    name = "custom-event",
                    timestampMs = timestamps.first,
                    attributes = customAttributes
                )
            ).toNewPayload()

            assertEmbraceSpanData(
                span = trace.toNewPayload(),
                expectedStartTimeMs = timestamps.first,
                expectedEndTimeMs = timestamps.second,
                expectedParentId = SpanId.getInvalid(),
                expectedCustomAttributes = customAttributes,
            )

            assertEmbraceSpanData(
                span = checkNotNull(spanMap["custom-span"]).toNewPayload(),
                expectedStartTimeMs = timestamps.first,
                expectedEndTimeMs = timestamps.first + 1,
                expectedParentId = trace.spanId,
                expectedCustomAttributes = customAttributes,
                expectedEvents = listOf(expectedEvent),
                expectedErrorCode = ErrorCode.FAILURE
            )

            assertTrue(trace.attributes.keys.none { it == "before-start" || it == "after-end" })

            val events = timestamps.third
            if (uiLoadType == UiLoadType.COLD) {
                checkNotNull(events[LifecycleStage.CREATE]).run {
                    assertEmbraceSpanData(
                        span = checkNotNull(spanMap["emb-$activityName-create"]).toNewPayload(),
                        expectedStartTimeMs = startMs(),
                        expectedEndTimeMs = endMs(),
                        expectedParentId = trace.spanId
                    )
                }
            } else {
                assertNull(spanMap["emb-$activityName-create"])
            }

            checkNotNull(events[LifecycleStage.START]).run {
                assertEmbraceSpanData(
                    span = checkNotNull(spanMap["emb-$activityName-start"]).toNewPayload(),
                    expectedStartTimeMs = startMs(),
                    expectedEndTimeMs = endMs(),
                    expectedParentId = trace.spanId
                )
            }

            if (hasPreAndPostEvents) {
                checkNotNull(events[LifecycleStage.RESUME]).run {
                    assertEmbraceSpanData(
                        span = checkNotNull(spanMap["emb-$activityName-resume"]).toNewPayload(),
                        expectedStartTimeMs = startMs(),
                        expectedEndTimeMs = endMs(),
                        expectedParentId = trace.spanId
                    )
                }
            } else {
                assertNull(spanMap["emb-$activityName-resume"])
            }

            if (hasRenderEvent) {
                checkNotNull(events[LifecycleStage.RENDER]).run {
                    assertEmbraceSpanData(
                        span = checkNotNull(spanMap["emb-$activityName-render"]).toNewPayload(),
                        expectedStartTimeMs = startMs(),
                        expectedEndTimeMs = endMs(),
                        expectedParentId = trace.spanId
                    )
                }
            } else {
                assertNull(spanMap["emb-$activityName-render"])
            }

            val lastEventEndTimeMs = if (hasRenderEvent) {
                checkNotNull(events[LifecycleStage.RENDER]).endMs()
            } else {
                if (hasPreAndPostEvents) {
                    checkNotNull(events[LifecycleStage.RESUME]).endMs()
                } else {
                    checkNotNull(events[LifecycleStage.RESUME]).startMs()
                }
            }

            if (manualEnd) {
                assertNotEquals(trace.endTimeNanos.nanosToMillis(), lastEventEndTimeMs)
            } else {
                assertEquals(trace.endTimeNanos.nanosToMillis(), lastEventEndTimeMs)
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "ComplexMethod")
    private fun openActivity(
        activityName: String,
        instanceId: Int,
        lastActivityName: String,
        lastInstanceId: Int,
        previousState: PreviousState,
        uiLoadType: UiLoadType,
        manualEnd: Boolean,
    ): Triple<Long, Long, Map<LifecycleStage, LifecycleEvents>> {
        val events = mutableMapOf<LifecycleStage, LifecycleEvents>()
        val lastActivityExitMs = clock.now()
        clock.tick(100L)

        // set state of tracker to simulate that at least one activity has been opened
        when (previousState) {
            PreviousState.FROM_ACTIVITY -> {
                traceEmitter.discard(
                    instanceId = lastInstanceId,
                    timestampMs = lastActivityExitMs
                )
            }

            PreviousState.FROM_BACKGROUND -> {
                activityCreate(
                    activityName = lastActivityName,
                    instanceId = lastInstanceId,
                )
            }

            PreviousState.FROM_INTERRUPTED_LOAD -> {
                activityCreate(
                    activityName = lastActivityName,
                    instanceId = lastInstanceId,
                )
                activityStart(
                    activityName = lastActivityName,
                    instanceId = lastInstanceId,
                )
            }
        }

        clock.tick()

        traceEmitter.addAttribute(instanceId, "before-start", "stub")

        val createEvents = if (uiLoadType == UiLoadType.COLD) {
            activityCreate(
                activityName = activityName,
                instanceId = instanceId,
                manualEnd = manualEnd,
            )
        } else {
            null
        }?.apply {
            events[LifecycleStage.CREATE] = this
        }

        clock.tick()

        val startEvents = activityStart(
            activityName = activityName,
            instanceId = instanceId,
            manualEnd = manualEnd,
        ).apply {
            events[LifecycleStage.START] = this
        }

        clock.tick()

        traceEmitter.addAttribute(instanceId, "custom-attribute", "custom-value")

        val traceStartMs = createEvents?.run {
            if (hasPreAndPostEvents) {
                pre
            } else {
                eventStart
            }
        } ?: if (hasPreAndPostEvents) {
            startEvents.pre
        } else {
            startEvents.eventStart
        }

        traceEmitter.addChildSpan(
            instanceId = instanceId,
            name = "custom-span",
            startTimeMs = traceStartMs,
            endTimeMs = traceStartMs + 1L,
            attributes = customAttributes,
            events = listOf(
                checkNotNull(
                    EmbraceSpanEvent.create(
                        name = "custom-event",
                        timestampMs = traceStartMs,
                        attributes = customAttributes
                    )
                )
            ),
            errorCode = ErrorCode.FAILURE
        )

        val resumeEvents = activityResume(instanceId).apply {
            events[LifecycleStage.RESUME] = this
        }

        clock.tick()

        val renderEvents = if (hasRenderEvent) {
            activityRender(instanceId = instanceId)
        } else {
            null
        }?.apply {
            events[LifecycleStage.RENDER] = this
        }

        val traceEndMs = if (manualEnd) {
            clock.tick(500L).also { time ->
                traceEmitter.complete(instanceId, time)
            }
        } else {
            renderEvents?.endMs()
                ?: if (hasPreAndPostEvents) {
                    resumeEvents.endMs()
                } else {
                    resumeEvents.startMs()
                }
        }

        traceEmitter.addAttribute(instanceId, "after-end", "stub")

        return Triple(traceStartMs, traceEndMs, events)
    }

    private fun activityCreate(
        activityName: String,
        instanceId: Int,
        manualEnd: Boolean = false,
    ): LifecycleEvents {
        return runLifecycleEvent(
            instanceId = instanceId,
            startCallback = traceEmitter::create,
            endCallback = traceEmitter::createEnd,
            activityName = activityName,
            manualEnd = manualEnd,
        )
    }

    private fun activityStart(
        activityName: String,
        instanceId: Int,
        manualEnd: Boolean = false,
    ): LifecycleEvents {
        return runLifecycleEvent(
            instanceId = instanceId,
            startCallback = traceEmitter::start,
            endCallback = traceEmitter::startEnd,
            activityName = activityName,
            manualEnd = manualEnd,
        )
    }

    @Suppress("FunctionParameterNaming", "UNUSED_PARAMETER", "EmptyFunctionBlock")
    private fun activityResume(
        instanceId: Int,
    ): LifecycleEvents {
        return runLifecycleEvent(
            instanceId = instanceId,
            startCallback = fun(instanceId: Int, _: String, startMs: Long, _: Boolean) {
                traceEmitter.resume(instanceId, startMs)
            },
            endCallback = if (hasPreAndPostEvents) traceEmitter::resumeEnd else fun(_, _) {},
        )
    }

    private fun activityRender(instanceId: Int): LifecycleEvents {
        val events = LifecycleEvents()
        events.eventStart = clock.now()
        traceEmitter.render(instanceId, events.startMs())
        events.eventEnd = clock.tick(100L)
        traceEmitter.renderEnd(instanceId, events.endMs())
        return events
    }

    private fun runLifecycleEvent(
        instanceId: Int,
        startCallback: (instanceId: Int, activityName: String, startMs: Long, manualEnd: Boolean) -> Unit,
        endCallback: (instanceId: Int, startMs: Long) -> Unit,
        activityName: String = "",
        manualEnd: Boolean = false,
    ): LifecycleEvents {
        val events = LifecycleEvents()
        if (hasPreAndPostEvents) {
            events.pre = clock.now()
            clock.tick()
        }
        events.eventStart = clock.now()
        startCallback(instanceId, activityName, events.startMs(), manualEnd)
        events.eventEnd = clock.tick(100L)
        if (hasPreAndPostEvents) {
            events.post = clock.tick()
        }
        endCallback(instanceId, events.endMs())
        return events
    }

    private data class LifecycleEvents(
        var pre: Long = Long.MAX_VALUE,
        var eventStart: Long = Long.MAX_VALUE,
        var eventEnd: Long = Long.MIN_VALUE,
        var post: Long = Long.MIN_VALUE,
    )

    private fun LifecycleEvents.startMs(): Long = min(pre, eventStart)

    private fun LifecycleEvents.endMs(): Long = max(post, eventEnd)

    private enum class PreviousState {
        FROM_ACTIVITY,
        FROM_BACKGROUND,
        FROM_INTERRUPTED_LOAD
    }

    companion object {
        const val NEW_INSTANCE_ID = 1
        const val ACTIVITY_NAME = "com.my.CoolActivity"
        const val LAST_ACTIVITY_NAME = "com.my.Activity"
        const val LAST_ACTIVITY_INSTANCE_ID = 99

        val customAttributes = mapOf("custom-attribute" to "custom-value")
    }
}
