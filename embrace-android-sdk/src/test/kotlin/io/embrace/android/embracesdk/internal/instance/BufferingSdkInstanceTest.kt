package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.experiments.TrackedFeatureFlag
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeLogRecordProcessor
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentApiCall
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentKind
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedData
import io.embrace.android.embracesdk.internal.config.resolved.ExperimentConfig
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class BufferingSdkInstanceTest {

    private lateinit var clock: FakeClock
    private lateinit var logger: FakeInternalLogger
    private lateinit var target: RecordingSdkApi
    private lateinit var replayed: MutableList<List<ExperimentApiCall>>
    private lateinit var instance: BufferingSdkInstance

    @Before
    fun setUp() {
        clock = FakeClock(currentTime = 1000L)
        logger = FakeInternalLogger(throwOnInternalError = false)
        target = RecordingSdkApi()
        replayed = mutableListOf()
        instance = BufferingSdkInstance(clock, logger)
    }

    @Test
    fun `otel config calls are replayed in order when drained`() {
        val spanExporter = FakeSpanExporter()
        val spanProcessor = FakeSpanProcessor()
        val logExporter = FakeLogRecordExporter()
        val logProcessor = FakeLogRecordProcessor()
        instance.addSpanExporter(spanExporter)
        instance.setResourceAttribute("key", "value")
        instance.addSpanProcessor(spanProcessor)
        instance.addLogRecordExporter(logExporter)
        instance.addLogRecordProcessor(logProcessor)
        assertTrue(target.calls.isEmpty())

        instance.drainOTelConfig(target)
        assertEquals(
            listOf(spanExporter, "key=value", spanProcessor, logExporter, logProcessor),
            target.calls,
        )
    }

    @Test
    fun `otel config calls after draining are forwarded`() {
        instance.drainOTelConfig(target)
        instance.setResourceAttribute("key", "value")
        assertEquals(listOf("key=value"), target.calls)
    }

    @Test
    fun `completed spans are buffered and replayed when drained`() {
        assertTrue(instance.recordCompletedSpan("span", 1, 2))
        assertTrue(target.calls.isEmpty())

        instance.drainCompletedSpans(target)
        assertEquals(listOf("span:1-2"), target.calls)
    }

    @Test
    fun `completed spans after draining are forwarded with the target result`() {
        instance.drainCompletedSpans(target)
        target.recordResult = false
        assertFalse(instance.recordCompletedSpan("span", 1, 2))
        assertEquals(listOf("span:1-2"), target.calls)
    }

    @Test
    fun `completed span buffer is bounded`() {
        repeat(MAX_BUFFERED_SPAN_CALLS) {
            assertTrue(instance.recordCompletedSpan("span-$it", 1, 2))
        }
        assertFalse(instance.recordCompletedSpan("dropped", 1, 2))

        instance.drainCompletedSpans(target)
        assertEquals(MAX_BUFFERED_SPAN_CALLS, target.calls.size)
    }

    @Test
    fun `experiment calls are replayed in one go when drained`() {
        instance.trackExperiments(
            listOf(
                instance.createExperiment("exp1", "v1", 10L),
                instance.createExperiment("exp2", null, 11L),
            ),
        )
        instance.trackExperiment("exp3", "v2", 12L)
        instance.trackFeatureFlag("flag1", "on", 13L)
        instance.trackFeatureFlags(listOf(instance.createFeatureFlag("flag2", null, 14L)))
        instance.untrackExperiments(listOf("exp1", "exp2"), 20L)
        instance.untrackExperiment("exp3", 21L)
        instance.untrackFeatureFlags(listOf("flag1"), 22L)
        instance.untrackFeatureFlag("flag2", 23L)
        drainExperiments()

        assertEquals(
            listOf(
                listOf(
                    ExperimentApiCall.Track(
                        listOf(TrackedData.experiment("exp1", 10L, "v1"), TrackedData.experiment("exp2", 11L, null)),
                    ),
                    ExperimentApiCall.Track(listOf(TrackedData.experiment("exp3", 12L, "v2"))),
                    ExperimentApiCall.Track(listOf(TrackedData.featureFlag("flag1", 13L, "on"))),
                    ExperimentApiCall.Track(listOf(TrackedData.featureFlag("flag2", 14L, null))),
                    ExperimentApiCall.Untrack(ExperimentKind.EXPERIMENT, listOf("exp1", "exp2"), 20L),
                    ExperimentApiCall.Untrack(ExperimentKind.EXPERIMENT, listOf("exp3"), 21L),
                    ExperimentApiCall.Untrack(ExperimentKind.FEATURE_FLAG, listOf("flag1"), 22L),
                    ExperimentApiCall.Untrack(ExperimentKind.FEATURE_FLAG, listOf("flag2"), 23L),
                ),
            ),
            replayed,
        )
        assertTrue(target.calls.isEmpty())
    }

    @Test
    fun `omitted experiment timestamps are captured when the call is buffered`() {
        val callTime = clock.now()
        instance.trackExperiment("exp1")
        instance.untrackFeatureFlag("flag1")
        clock.tick(500)
        drainExperiments()

        assertEquals(
            listOf(
                ExperimentApiCall.Track(listOf(TrackedData.experiment("exp1", callTime, null))),
                ExperimentApiCall.Untrack(ExperimentKind.FEATURE_FLAG, listOf("flag1"), callTime),
            ),
            replayed.single(),
        )
    }

    @Test
    fun `experiment buffer admits entries up to the absolute record limit, keeping the earliest`() {
        repeat(MAX_BUFFERED_EXPERIMENT_ENTRIES - 1) {
            instance.trackExperiment("exp-$it", startedAt = 1L)
        }
        instance.trackExperiments(
            listOf(instance.createExperiment("exp-kept", startedAt = 1L), instance.createExperiment("exp-dropped")),
        )
        instance.trackExperiment("exp-after-full", startedAt = 1L)

        drainExperiments()

        val ids = replayed.single().flatMap { (it as ExperimentApiCall.Track).data }.map { it.id }
        assertEquals(MAX_BUFFERED_EXPERIMENT_ENTRIES, ids.size)
        assertTrue(ids.contains("exp-kept"))
        assertFalse(ids.contains("exp-dropped"))
        assertFalse(ids.contains("exp-after-full"))
    }

    @Test
    fun `empty experiment calls are not buffered`() {
        instance.trackExperiments(emptyList())
        instance.untrackExperiments(emptyList())
        instance.trackFeatureFlags(emptyList())
        instance.untrackFeatureFlags(emptyList())
        drainExperiments()
        assertTrue(replayed.isEmpty())
    }

    @Test
    fun `experiment calls after draining are forwarded`() {
        drainExperiments()
        instance.trackExperiment("exp1", "v1", 10L)
        instance.untrackFeatureFlag("flag1", 20L)

        assertEquals(listOf("track_experiments:exp1", "untrack_feature_flags:flag1@20"), target.calls)
        assertTrue(replayed.isEmpty())
    }

    @Test
    fun `groups drain independently`() {
        instance.setResourceAttribute("key", "value")
        instance.recordCompletedSpan("span", 1, 2)
        instance.trackExperiment("exp1", startedAt = 1L)
        instance.drainOTelConfig(target)

        assertEquals(listOf("key=value"), target.calls)
        assertTrue(replayed.isEmpty())
    }

    @Test
    fun `a failed replay still hands over to the target`() {
        instance.trackExperiment("exp1", startedAt = 1L)
        runCatching {
            instance.drainExperimentCalls(target) { error("replay failed") }
        }
        instance.trackExperiment("exp2", startedAt = 2L)

        assertEquals(listOf("track_experiments:exp2"), target.calls)
    }

    @Test
    fun `calls made during a replay are replayed in a later round, in order`() {
        instance.trackExperiment("exp1", startedAt = 1L)

        instance.drainExperimentCalls(target) { calls ->
            replayed.add(calls)
            if (replayed.size == 1) {
                instance.trackExperiment("exp2", startedAt = 2L)
            }
        }
        instance.trackExperiment("exp3", startedAt = 3L)

        assertEquals(
            listOf(
                listOf(ExperimentApiCall.Track(listOf(TrackedData.experiment("exp1", 1L, null)))),
                listOf(ExperimentApiCall.Track(listOf(TrackedData.experiment("exp2", 2L, null)))),
            ),
            replayed,
        )
        assertEquals(listOf("track_experiments:exp3"), target.calls)
    }

    @Test
    fun `callers do not wait for a replay to finish`() {
        val replayStarted = CountDownLatch(1)
        val releaseReplay = CountDownLatch(1)
        instance.trackExperiment("exp1", startedAt = 1L)

        val drainer = thread {
            instance.drainExperimentCalls(target) { calls ->
                replayed.add(calls)
                if (replayed.size == 1) {
                    replayStarted.countDown()
                    releaseReplay.await(5, TimeUnit.SECONDS)
                }
            }
        }
        assertTrue(replayStarted.await(5, TimeUnit.SECONDS))

        val caller = thread { instance.trackExperiment("exp2", startedAt = 2L) }
        caller.join(5000)
        assertFalse(caller.isAlive)

        releaseReplay.countDown()
        drainer.join(5000)
        assertFalse(drainer.isAlive)
        assertEquals(
            listOf(ExperimentApiCall.Track(listOf(TrackedData.experiment("exp2", 2L, null)))),
            replayed[1],
        )
        assertTrue(target.calls.isEmpty())
    }

    @Test
    fun `span cap is a running total across drain rounds`() {
        repeat(MAX_BUFFERED_SPAN_CALLS) {
            instance.recordCompletedSpan("span-$it", 1, 2)
        }
        var lateResult: Boolean? = null
        target.onRecordCompletedSpan = {
            if (lateResult == null) {
                lateResult = instance.recordCompletedSpan("late", 1, 2)
            }
        }

        instance.drainCompletedSpans(target)

        assertEquals(false, lateResult)
        assertEquals(MAX_BUFFERED_SPAN_CALLS, target.calls.size)
    }

    @Test
    fun `buffered spans are not affected by later changes to the caller's data`() {
        val attributes = mutableMapOf("key" to "value")
        val eventAttributes = mutableMapOf("eventKey" to "eventValue")
        val events = mutableListOf(checkNotNull(EmbraceSpanEvent.create("event", 5L, eventAttributes)))
        instance.recordCompletedSpan("span", 1, 2, attributes = attributes, events = events)

        attributes["key"] = "changed"
        eventAttributes["eventKey"] = "changed"
        events.clear()
        instance.drainCompletedSpans(target)

        assertEquals(listOf(mapOf("key" to "value")), target.spanAttributes)
        assertEquals(
            listOf(listOf(Triple("event", TimeUnit.MILLISECONDS.toNanos(5L), mapOf("eventKey" to "eventValue")))),
            target.spanEvents,
        )
    }

    @Test
    fun `buffered experiment calls are not affected by later changes to the caller's data`() {
        val experiments = mutableListOf(instance.createExperiment("exp1", "v1", 1L))
        val ids = mutableListOf("exp1")
        instance.trackExperiments(experiments)
        instance.untrackExperiments(ids, 2L)

        experiments.clear()
        ids[0] = "changed"
        drainExperiments()

        assertEquals(
            listOf(
                ExperimentApiCall.Track(listOf(TrackedData.experiment("exp1", 1L, "v1"))),
                ExperimentApiCall.Untrack(ExperimentKind.EXPERIMENT, listOf("exp1"), 2L),
            ),
            replayed.single(),
        )
    }

    @Test
    fun `a throw while buffering a span is reported and does not use up the cap`() {
        val throwingAttributes = object : AbstractMap<String, String>() {
            override val entries: Set<Map.Entry<String, String>> get() = error("bad map")
        }

        assertFalse(instance.recordCompletedSpan("bad", 1, 2, attributes = throwingAttributes))
        repeat(MAX_BUFFERED_SPAN_CALLS) {
            assertTrue(instance.recordCompletedSpan("span-$it", 1, 2))
        }
        instance.drainCompletedSpans(target)

        assertEquals(MAX_BUFFERED_SPAN_CALLS, target.calls.size)
        assertInternalErrorReported()
    }

    @Test
    fun `a throw while buffering an experiment is reported and later calls still buffer`() {
        val throwingExperiment = object : TrackedExperiment {
            override val id: String get() = error("bad id")
            override val variant: String? = null
            override val startedAt: Long? = null
        }

        instance.trackExperiments(listOf(throwingExperiment))
        instance.trackExperiment("exp1", startedAt = 1L)
        drainExperiments()

        assertEquals(
            listOf(ExperimentApiCall.Track(listOf(TrackedData.experiment("exp1", 1L, null)))),
            replayed.single(),
        )
        assertInternalErrorReported()
    }

    @Test
    fun `a replayed call that throws does not stop the others replaying`() {
        instance.recordCompletedSpan("first", 1, 2)
        instance.recordCompletedSpan("second", 3, 4)
        var throwNext = true
        target.onRecordCompletedSpan = {
            if (throwNext) {
                throwNext = false
                error("target failed")
            }
        }

        instance.drainCompletedSpans(target)

        assertEquals(listOf("first:1-2", "second:3-4"), target.calls)
        assertInternalErrorReported()
    }

    @Test
    fun `a forwarded call that throws is reported rather than reaching the caller`() {
        instance.drainCompletedSpans(target)
        target.onRecordCompletedSpan = { error("target failed") }

        assertFalse(instance.recordCompletedSpan("span", 1, 2))
        assertInternalErrorReported()
    }

    @Test
    fun `calls that cannot be buffered are dropped with a log`() {
        instance.addBreadcrumb("crumb")
        instance.logInfo("msg")
        assertEquals(1, logger.infoMessages.size)
        assertTrue(target.calls.isEmpty())
    }

    private fun assertInternalErrorReported() {
        assertEquals(
            listOf(InternalErrorType.BufferedApiCallFail.toString()),
            logger.internalErrorMessages.map { it.msg },
        )
        assertEquals(1, logger.errorMessages.size)
    }

    private fun drainExperiments() {
        instance.drainExperimentCalls(target) { replayed.add(it) }
    }

    private class RecordingSdkApi : SdkApi by NoopSdkInstance(FakeInternalLogger(), "") {
        val calls = mutableListOf<Any>()
        var recordResult = true
        var onRecordCompletedSpan: () -> Unit = {}
        val spanAttributes = mutableListOf<Map<String, String>>()
        val spanEvents = mutableListOf<List<Triple<String, Long, Map<String, String>>>>()

        override fun addSpanExporter(spanExporter: SpanExporter) {
            calls.add(spanExporter)
        }

        override fun addSpanProcessor(spanProcessor: SpanProcessor) {
            calls.add(spanProcessor)
        }

        override fun addLogRecordExporter(logRecordExporter: LogRecordExporter) {
            calls.add(logRecordExporter)
        }

        override fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor) {
            calls.add(logRecordProcessor)
        }

        override fun setResourceAttribute(key: String, value: String) {
            calls.add("$key=$value")
        }

        override fun recordCompletedSpan(
            name: String,
            startTimeMs: Long,
            endTimeMs: Long,
            errorCode: ErrorCode?,
            parent: EmbraceSpan?,
            attributes: Map<String, String>,
            events: List<EmbraceSpanEvent>,
        ): Boolean {
            calls.add("$name:$startTimeMs-$endTimeMs")
            spanAttributes.add(attributes.toMap())
            spanEvents.add(events.map { Triple(it.name, it.timestampNanos, it.attributes.toMap()) })
            onRecordCompletedSpan()
            return recordResult
        }

        override fun trackExperiments(experiments: List<TrackedExperiment>) {
            calls.add("track_experiments:${experiments.joinToString { it.id }}")
        }

        override fun untrackExperiments(ids: List<String>, endedAt: Long?) {
            calls.add("untrack_experiments:${ids.joinToString()}@$endedAt")
        }

        override fun trackFeatureFlags(flags: List<TrackedFeatureFlag>) {
            calls.add("track_feature_flags:${flags.joinToString { it.id }}")
        }

        override fun untrackFeatureFlags(ids: List<String>, endedAt: Long?) {
            calls.add("untrack_feature_flags:${ids.joinToString()}@$endedAt")
        }
    }

    private companion object {
        private const val MAX_BUFFERED_SPAN_CALLS = 1000
        private const val MAX_BUFFERED_EXPERIMENT_ENTRIES = ExperimentConfig.MAX_COUNT_LIMIT
    }
}
