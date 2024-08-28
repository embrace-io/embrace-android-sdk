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
            fromBackground = false,
            openType = OpenEvents.OpenType.COLD,
            firePreAndPost = true,
            hasRenderEvent = true,
        )
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify cold open trace from background in U`() {
        verifyOpen(
            fromBackground = true,
            openType = OpenEvents.OpenType.COLD,
            firePreAndPost = true,
            hasRenderEvent = true,
        )
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify hot open trace in from background in U`() {
        verifyOpen(
            fromBackground = true,
            openType = OpenEvents.OpenType.HOT,
            firePreAndPost = true,
            hasRenderEvent = true,
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold open trace in from another activity L`() {
        verifyOpen(
            fromBackground = false,
            openType = OpenEvents.OpenType.COLD,
            firePreAndPost = false,
            hasRenderEvent = false,
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold open trace from background in L`() {
        verifyOpen(
            fromBackground = true,
            openType = OpenEvents.OpenType.COLD,
            firePreAndPost = false,
            hasRenderEvent = false,
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify hot open trace in L from background`() {
        verifyOpen(
            fromBackground = true,
            openType = OpenEvents.OpenType.HOT,
            firePreAndPost = false,
            hasRenderEvent = false,
        )
    }

    private fun verifyOpen(
        fromBackground: Boolean,
        openType: OpenEvents.OpenType,
        firePreAndPost: Boolean,
        hasRenderEvent: Boolean,
    ) {
        openActivity(
            fromBackground = fromBackground,
            openType = openType,
            firePreAndPost = firePreAndPost,
            hasRenderEvent = hasRenderEvent
        ).let { timestamps ->
            val spanMap = spanSink.completedSpans().associateBy { it.name }
            val trace = checkNotNull(spanMap["emb-${ACTIVITY_NAME}-${openType.typeName}-open"])

            assertEmbraceSpanData(
                span = trace.toNewPayload(),
                expectedStartTimeMs = timestamps.first,
                expectedEndTimeMs = timestamps.second,
                expectedParentId = SpanId.getInvalid(),
                key = true,
            )

            val events = timestamps.third
            if (openType == OpenEvents.OpenType.COLD) {
                checkNotNull(events[OpenTraceEmitter.LifecycleEvent.CREATE]).run {
                    assertEmbraceSpanData(
                        span = checkNotNull(spanMap["emb-$ACTIVITY_NAME-create"]).toNewPayload(),
                        expectedStartTimeMs = startMs(),
                        expectedEndTimeMs = endMs(),
                        expectedParentId = trace.spanId
                    )
                }
            } else {
                assertNull(spanMap["emb-$ACTIVITY_NAME-create"])
            }

            checkNotNull(events[OpenTraceEmitter.LifecycleEvent.START]).run {
                assertEmbraceSpanData(
                    span = checkNotNull(spanMap["emb-$ACTIVITY_NAME-start"]).toNewPayload(),
                    expectedStartTimeMs = startMs(),
                    expectedEndTimeMs = endMs(),
                    expectedParentId = trace.spanId
                )
            }

            if (hasRenderEvent) {
                checkNotNull(events[OpenTraceEmitter.LifecycleEvent.RESUME]).run {
                    assertEmbraceSpanData(
                        span = checkNotNull(spanMap["emb-$ACTIVITY_NAME-resume"]).toNewPayload(),
                        expectedStartTimeMs = startMs(),
                        expectedEndTimeMs = endMs(),
                        expectedParentId = trace.spanId
                    )
                }
                checkNotNull(events[OpenTraceEmitter.LifecycleEvent.RENDER]).run {
                    assertEmbraceSpanData(
                        span = checkNotNull(spanMap["emb-$ACTIVITY_NAME-render"]).toNewPayload(),
                        expectedStartTimeMs = startMs(),
                        expectedEndTimeMs = endMs(),
                        expectedParentId = trace.spanId
                    )
                }
            } else {
                assertNull(spanMap["emb-$ACTIVITY_NAME-resume"])
                assertNull(spanMap["emb-$ACTIVITY_NAME-render"])
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "ComplexMethod")
    private fun openActivity(
        fromBackground: Boolean,
        openType: OpenEvents.OpenType,
        firePreAndPost: Boolean,
        hasRenderEvent: Boolean
    ): Triple<Long, Long, Map<OpenTraceEmitter.LifecycleEvent, LifecycleEvents>> {
        val events = mutableMapOf<OpenTraceEmitter.LifecycleEvent, LifecycleEvents>()
        val lastActivityExitMs = clock.now()
        traceEmitter.resetTrace(LAST_ACTIVITY_INSTANCE, LAST_ACTIVITY, lastActivityExitMs)
        clock.tick(100L)

        if (fromBackground) {
            traceEmitter.hibernate(LAST_ACTIVITY_INSTANCE, LAST_ACTIVITY, clock.now())
        }

        val createEvents = if (openType == OpenEvents.OpenType.COLD) {
            activityCreate(firePreAndPost)
        } else {
            null
        }?.apply {
            events[OpenTraceEmitter.LifecycleEvent.CREATE] = this
        }

        val startEvents = activityStart(firePreAndPost).apply {
            events[OpenTraceEmitter.LifecycleEvent.START] = this
        }

        val resumeEvents = activityResume(firePreAndPost).apply {
            events[OpenTraceEmitter.LifecycleEvent.RESUME] = this
        }

        val renderEvents = if (hasRenderEvent) {
            activityRender(firePreAndPost)
        } else {
            null
        }?.apply {
            events[OpenTraceEmitter.LifecycleEvent.RENDER] = this
        }

        val traceStartMs = if (!fromBackground) {
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

    private fun activityCreate(firePreAndPost: Boolean = true): LifecycleEvents {
        return runLifecycleEvent(
            startCallback = traceEmitter::create,
            endCallback = traceEmitter::createEnd,
            firePreAndPost = firePreAndPost,
        )
    }

    private fun activityStart(firePreAndPost: Boolean = true): LifecycleEvents {
        return runLifecycleEvent(
            startCallback = traceEmitter::start,
            endCallback = traceEmitter::startEnd,
            firePreAndPost = firePreAndPost,
        )
    }

    private fun activityResume(firePreAndPost: Boolean = true): LifecycleEvents {
        return runLifecycleEvent(
            startCallback = traceEmitter::resume,
            endCallback = traceEmitter::resumeEnd,
            firePreAndPost = firePreAndPost,
        )
    }

    private fun activityRender(firePreAndPost: Boolean = true): LifecycleEvents {
        return runLifecycleEvent(
            startCallback = traceEmitter::render,
            endCallback = traceEmitter::renderEnd,
            firePreAndPost = firePreAndPost,
        )
    }

    private fun runLifecycleEvent(
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
        startCallback(INSTANCE_1, ACTIVITY_NAME, events.startMs())
        events.eventEnd = clock.tick(100L)
        if (firePreAndPost) {
            events.post = clock.tick()
        }
        endCallback(INSTANCE_1, events.endMs())
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

    companion object {
        const val INSTANCE_1 = 1
        const val ACTIVITY_NAME = "com.my.CoolActivity"
        const val LAST_ACTIVITY = "com.my.Activity"
        const val LAST_ACTIVITY_INSTANCE = 99
    }
}
