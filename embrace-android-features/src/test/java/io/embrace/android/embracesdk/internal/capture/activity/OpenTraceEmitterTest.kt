package io.embrace.android.embracesdk.internal.capture.activity

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.opentelemetry.api.trace.SpanId
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min

@RunWith(AndroidJUnit4::class)
internal class OpenTraceEmitterTest {
    private lateinit var clock: FakeClock
    private lateinit var spanSink: SpanSink
    private lateinit var spanService: SpanService
    private lateinit var traceEmitter: OpenTraceEmitter

    @Before
    fun setUp() {
        clock = FakeClock()
        val initModule = FakeInitModule(clock = clock)
        spanSink = initModule.openTelemetryModule.spanSink
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.now())
        clock.tick(100L)
        traceEmitter = OpenTraceEmitter(
            spanService = spanService,
            versionChecker = BuildVersionChecker,
        )
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify cold open trace from another activity in U`() {
        verifyOpen(
            previousState = PreviousState.FROM_ACTIVITY,
            openType = OpenType.COLD,
            firePreAndPost = true,
            hasRenderEvent = true,
        )
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify cold open trace from the same activity in U`() {
        verifyOpen(
            lastActivityName = ACTIVITY_NAME,
            previousState = PreviousState.FROM_ACTIVITY,
            openType = OpenType.COLD,
            firePreAndPost = true,
            hasRenderEvent = true,
        )
    }

    @Ignore("Not working yet")
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify cold open trace from an interrupted opening of another activity in U`() {
        verifyOpen(
            previousState = PreviousState.FROM_INTERRUPTED_ACTIVITY_OPEN,
            openType = OpenType.COLD,
            firePreAndPost = true,
            hasRenderEvent = true,
        )
    }

    @Ignore("Not working yet")
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify cold open trace from an interrupted opening of the same activity in U`() {
        verifyOpen(
            lastActivityName = ACTIVITY_NAME,
            previousState = PreviousState.FROM_INTERRUPTED_ACTIVITY_OPEN,
            openType = OpenType.COLD,
            firePreAndPost = true,
            hasRenderEvent = true,
        )
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify cold open trace from background in U`() {
        verifyOpen(
            previousState = PreviousState.FROM_BACKGROUND,
            openType = OpenType.COLD,
            firePreAndPost = true,
            hasRenderEvent = true,
        )
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify hot open trace in from background in U`() {
        verifyOpen(
            previousState = PreviousState.FROM_BACKGROUND,
            openType = OpenType.HOT,
            firePreAndPost = true,
            hasRenderEvent = true,
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold open trace in from another activity L`() {
        verifyOpen(
            previousState = PreviousState.FROM_ACTIVITY,
            openType = OpenType.COLD,
            firePreAndPost = false,
            hasRenderEvent = false,
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold open trace from background in L`() {
        verifyOpen(
            previousState = PreviousState.FROM_BACKGROUND,
            openType = OpenType.COLD,
            firePreAndPost = false,
            hasRenderEvent = false,
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify hot open trace in L from background`() {
        verifyOpen(
            previousState = PreviousState.FROM_BACKGROUND,
            openType = OpenType.HOT,
            firePreAndPost = false,
            hasRenderEvent = false,
        )
    }

    private fun verifyOpen(
        activityName: String = ACTIVITY_NAME,
        instanceId: Int = NEW_INSTANCE_ID,
        lastActivityName: String = LAST_ACTIVITY_NAME,
        lastInstanceId: Int = LAST_ACTIVITY_INSTANCE_ID,
        previousState: PreviousState,
        openType: OpenType,
        firePreAndPost: Boolean,
        hasRenderEvent: Boolean,
    ) {
        openActivity(
            activityName = activityName,
            instanceId = instanceId,
            lastActivityName = lastActivityName,
            lastInstanceId = lastInstanceId,
            previousState = previousState,
            openType = openType,
            firePreAndPost = firePreAndPost,
            hasRenderEvent = hasRenderEvent
        ).let { timestamps ->
            val spanMap = spanSink.completedSpans().associateBy { it.name }
            val trace = checkNotNull(spanMap["emb-$activityName-${openType.typeName}-open"])

            assertEmbraceSpanData(
                span = trace.toNewPayload(),
                expectedStartTimeMs = timestamps.first,
                expectedEndTimeMs = timestamps.second,
                expectedParentId = SpanId.getInvalid(),
                key = true,
            )

            val events = timestamps.third
            if (openType == OpenType.COLD) {
                checkNotNull(events[OpenTraceEmitter.LifecycleEvent.CREATE]).run {
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

            checkNotNull(events[OpenTraceEmitter.LifecycleEvent.START]).run {
                assertEmbraceSpanData(
                    span = checkNotNull(spanMap["emb-$activityName-start"]).toNewPayload(),
                    expectedStartTimeMs = startMs(),
                    expectedEndTimeMs = endMs(),
                    expectedParentId = trace.spanId
                )
            }

            if (hasRenderEvent) {
                checkNotNull(events[OpenTraceEmitter.LifecycleEvent.RESUME]).run {
                    assertEmbraceSpanData(
                        span = checkNotNull(spanMap["emb-$activityName-resume"]).toNewPayload(),
                        expectedStartTimeMs = startMs(),
                        expectedEndTimeMs = endMs(),
                        expectedParentId = trace.spanId
                    )
                }
                checkNotNull(events[OpenTraceEmitter.LifecycleEvent.RENDER]).run {
                    assertEmbraceSpanData(
                        span = checkNotNull(spanMap["emb-$activityName-render"]).toNewPayload(),
                        expectedStartTimeMs = startMs(),
                        expectedEndTimeMs = endMs(),
                        expectedParentId = trace.spanId
                    )
                }
            } else {
                assertNull(spanMap["emb-$activityName-resume"])
                assertNull(spanMap["emb-$activityName-render"])
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
        openType: OpenType,
        firePreAndPost: Boolean,
        hasRenderEvent: Boolean,
    ): Triple<Long, Long, Map<OpenTraceEmitter.LifecycleEvent, LifecycleEvents>> {
        val events = mutableMapOf<OpenTraceEmitter.LifecycleEvent, LifecycleEvents>()
        val lastActivityExitMs = clock.now()
        clock.tick(100L)

        when (previousState) {
            PreviousState.FROM_ACTIVITY -> {
                traceEmitter.resetTrace(lastInstanceId, lastActivityName, lastActivityExitMs)
            }

            PreviousState.FROM_BACKGROUND -> {
                traceEmitter.resetTrace(lastInstanceId, lastActivityName, lastActivityExitMs)
                traceEmitter.hibernate(lastInstanceId, lastActivityName, clock.now())
            }

            PreviousState.FROM_INTERRUPTED_ACTIVITY_OPEN -> {
                activityCreate(
                    activityName = lastActivityName,
                    instanceId = lastInstanceId,
                    firePreAndPost = firePreAndPost
                )
                activityStart(
                    activityName = lastActivityName,
                    instanceId = lastInstanceId,
                    firePreAndPost = firePreAndPost
                )
            }
        }

        val createEvents = if (openType == OpenType.COLD) {
            activityCreate(
                activityName = activityName,
                instanceId = instanceId,
                firePreAndPost = firePreAndPost
            )
        } else {
            null
        }?.apply {
            events[OpenTraceEmitter.LifecycleEvent.CREATE] = this
        }

        val startEvents = activityStart(
            activityName = activityName,
            instanceId = instanceId,
            firePreAndPost = firePreAndPost
        ).apply {
            events[OpenTraceEmitter.LifecycleEvent.START] = this
        }

        val resumeEvents = activityResume(
            activityName = activityName,
            instanceId = instanceId,
            firePreAndPost = firePreAndPost
        ).apply {
            events[OpenTraceEmitter.LifecycleEvent.RESUME] = this
        }

        val renderEvents = if (hasRenderEvent) {
            activityRender(
                activityName = activityName,
                instanceId = instanceId,
                firePreAndPost = firePreAndPost
            )
        } else {
            null
        }?.apply {
            events[OpenTraceEmitter.LifecycleEvent.RENDER] = this
        }

        val traceStartMs = if (previousState != PreviousState.FROM_BACKGROUND) {
            lastActivityExitMs
        } else {
            createEvents?.run {
                if (firePreAndPost) {
                    pre
                } else {
                    eventStart
                }
            } ?: if (firePreAndPost) {
                startEvents.pre
            } else {
                startEvents.eventStart
            }
        }

        val traceEndMs = renderEvents?.run {
            endMs()
        } ?: resumeEvents.startMs()

        return Triple(traceStartMs, traceEndMs, events)
    }

    private fun activityCreate(
        activityName: String,
        instanceId: Int,
        firePreAndPost: Boolean = true
    ): LifecycleEvents {
        return runLifecycleEvent(
            activityName = activityName,
            instanceId = instanceId,
            startCallback = traceEmitter::create,
            endCallback = traceEmitter::createEnd,
            firePreAndPost = firePreAndPost,
        )
    }

    private fun activityStart(
        activityName: String,
        instanceId: Int,
        firePreAndPost: Boolean = true
    ): LifecycleEvents {
        return runLifecycleEvent(
            activityName = activityName,
            instanceId = instanceId,
            startCallback = traceEmitter::start,
            endCallback = traceEmitter::startEnd,
            firePreAndPost = firePreAndPost,
        )
    }

    private fun activityResume(
        activityName: String,
        instanceId: Int,
        firePreAndPost: Boolean = true
    ): LifecycleEvents {
        return runLifecycleEvent(
            activityName = activityName,
            instanceId = instanceId,
            startCallback = traceEmitter::resume,
            endCallback = traceEmitter::resumeEnd,
            firePreAndPost = firePreAndPost,
        )
    }

    private fun activityRender(
        activityName: String,
        instanceId: Int,
        firePreAndPost: Boolean = true
    ): LifecycleEvents {
        return runLifecycleEvent(
            activityName = activityName,
            instanceId = instanceId,
            startCallback = traceEmitter::render,
            endCallback = traceEmitter::renderEnd,
            firePreAndPost = firePreAndPost,
        )
    }

    private fun runLifecycleEvent(
        activityName: String,
        instanceId: Int,
        startCallback: (instanceId: Int, activityName: String, startMs: Long) -> Unit,
        endCallback: (instanceId: Int, startMs: Long) -> Unit,
        firePreAndPost: Boolean = true
    ): LifecycleEvents {
        val events = LifecycleEvents()
        if (firePreAndPost) {
            events.pre = clock.now()
            clock.tick()
        }
        events.eventStart = clock.now()
        startCallback(instanceId, activityName, events.startMs())
        events.eventEnd = clock.tick(100L)
        if (firePreAndPost) {
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
        FROM_INTERRUPTED_ACTIVITY_OPEN
    }

    companion object {
        const val NEW_INSTANCE_ID = 1
        const val ACTIVITY_NAME = "com.my.CoolActivity"
        const val LAST_ACTIVITY_NAME = "com.my.Activity"
        const val LAST_ACTIVITY_INSTANCE_ID = 99
    }
}
