package io.embrace.android.embracesdk.internal.capture.activity

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.arch.assertDoesNotHaveEmbraceAttribute
import io.embrace.android.embracesdk.arch.assertIsKeySpan
import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.arch.schema.KeySpan
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
internal class LoadTraceEmitterTest {
    private lateinit var clock: FakeClock
    private lateinit var spanSink: SpanSink
    private lateinit var spanService: SpanService
    private lateinit var backgroundWorker: BackgroundWorker
    private lateinit var traceEmitter: LoadTraceEmitter

    @Before
    fun setUp() {
        clock = FakeClock()
        val initModule = FakeInitModule(clock = clock)
        backgroundWorker = BackgroundWorker(BlockableExecutorService())
        spanSink = initModule.openTelemetryModule.spanSink
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.now())
        clock.tick(100L)
        traceEmitter = LoadTraceEmitter(
            spanService = spanService,
            backgroundWorker = backgroundWorker,
            versionChecker = BuildVersionChecker,
        )
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace with every event triggered in T`() {
        verifyColdStartWithRender()
    }

    private fun verifyColdStartWithRender() {
        val createStartMs = clock.tick(100L)
        traceEmitter.create(
            instanceId = INSTANCE_1,
            activityName = ACTIVITY_NAME,
            timestampMs = clock.now()
        )
        val createEndMs = clock.tick(15L)
        traceEmitter.createEnd(
            instanceId = INSTANCE_1,
            timestampMs = clock.now()
        )
        val startStartMs = clock.tick(1L)
        traceEmitter.start(
            instanceId = INSTANCE_1,
            activityName = ACTIVITY_NAME,
            timestampMs = clock.now()
        )
        val startEndMs = clock.tick(10L)
        traceEmitter.startEnd(
            instanceId = INSTANCE_1,
            timestampMs = clock.now()
        )
        val resumeStartMs = clock.tick(2L)
        traceEmitter.resume(
            instanceId = INSTANCE_1,
            timestampMs = clock.now()
        )
        val resumeEndMs = clock.tick(100L)
        traceEmitter.resumeEnd(
            instanceId = INSTANCE_1,
            timestampMs = clock.now()
        )

        val renderStartMs = clock.tick(2L)
        traceEmitter.firstRender(
            instanceId = INSTANCE_1,
            timestampMs = clock.now()
        )
        val renderEndMs = clock.tick(100L)
        traceEmitter.firstRenderEnd(
            instanceId = INSTANCE_1,
            timestampMs = clock.now()
        )

        assertEquals(5, spanSink.completedSpans().size)
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val trace = checkNotNull(spanMap["emb-${ACTIVITY_NAME}-cold-render"])
        val create = checkNotNull(spanMap["emb-${ACTIVITY_NAME}-create"])
        val start = checkNotNull(spanMap["emb-${ACTIVITY_NAME}-start"])
        val resume = checkNotNull(spanMap["emb-${ACTIVITY_NAME}-resume"])
        val firstRender = checkNotNull(spanMap["emb-${ACTIVITY_NAME}-render"])

        assertTraceRoot(
            input = trace,
            expectedStartTimeMs = createStartMs,
            expectedEndTimeMs = renderEndMs,
        )
        assertChildSpan(create, createStartMs, createEndMs)
        assertChildSpan(start, startStartMs, startEndMs)
        assertChildSpan(resume, resumeStartMs, resumeEndMs)
        assertChildSpan(firstRender, renderStartMs, renderEndMs)
    }

    private fun assertTraceRoot(
        input: EmbraceSpanData,
        expectedStartTimeMs: Long,
        expectedEndTimeMs: Long,
    ) {
        val trace = input.toNewPayload()
        assertEquals(expectedStartTimeMs, trace.startTimeNanos?.nanosToMillis())
        assertEquals(expectedEndTimeMs, trace.endTimeNanos?.nanosToMillis())
        trace.assertDoesNotHaveEmbraceAttribute(PrivateSpan)
        trace.assertIsKeySpan()
    }

    private fun assertChildSpan(span: EmbraceSpanData, expectedStartTimeNanos: Long, expectedEndTimeNanos: Long) {
        assertEquals(expectedStartTimeNanos, span.startTimeNanos.nanosToMillis())
        assertEquals(expectedEndTimeNanos, span.endTimeNanos.nanosToMillis())
        span.assertDoesNotHaveEmbraceAttribute(PrivateSpan)
        span.assertDoesNotHaveEmbraceAttribute(KeySpan)
    }

    companion object {
        const val INSTANCE_1 = 1
        const val ACTIVITY_NAME = "CoolActivity"
    }
}
