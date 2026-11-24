package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.capture.state.StateEventEmitter
import io.embrace.android.embracesdk.internal.capture.state.StateRecorder
import io.embrace.android.embracesdk.internal.capture.state.StateSnapshot
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.config.resolved.StateExportMode
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.state.StateAssociation
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.data.LogRecordData
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * [stateCount] states that telemetry is associated with: a span by the sequence number of the last state change before
 * it started and before it ended, and a log by one attribute for each state. [mode] is what an app's own exporters are
 * given beyond that, or null where telemetry is not associated with state at all, which is the floor to compare with.
 *
 * It runs the SDK's real recorder, span stack and logging pipeline, with an app's span exporter and log exporter that
 * read what they are handed and keep nothing. It persists nothing, so what a benchmark measures here is the association
 * alone. The recorder has a journal that takes any number of changes and discards them, as it would behave with a
 * session part in progress.
 *
 * State events are emitted on a worker of the fixture's own, which [holdEvents] keeps from running.
 */
internal class StateAssociationFixture(
    val stateCount: Int,
    private val mode: StateExportMode?,
    private val clock: Clock = NormalizedIntervalClock(),
) : AutoCloseable {

    private val eventExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var eventsHeld: CountDownLatch? = null
    private val transitionCounts = IntArray(stateCount)

    private lateinit var recorder: StateRecorder

    private val harness = TelemetryDestinationHarness(
        useKotlinSdk = true,
        clock = clock,
        externalOtelConfig = ExternalOtelConfig().apply {
            addSpanExporter(ReadingSpanExporter)
            addLogExporter(ReadingLogRecordExporter)
        },
        stateAssociation = mode?.let {
            StateAssociation(
                stateProvider = { recorder.snapshot() },
                valuesOnExportedSpans = it == StateExportMode.VERBOSE,
                exportsEvents = it != StateExportMode.OFF,
                stateAt = { timeMs -> recorder.snapshotAt(timeMs) },
            )
        },
    )
    private val spanService: SpanService = harness.createUncappedSpanService()

    init {
        recorder = StateRecorder(
            clock = clock,
            maxChanges = Int.MAX_VALUE,
            listener = if (mode == StateExportMode.ASSOCIATION || mode == StateExportMode.VERBOSE) {
                StateEventEmitter(
                    logger = harness.stateEventLogger,
                    worker = BackgroundWorker(eventExecutor),
                    processIdentifierProvider = { PROCESS_IDENTIFIER },
                    includeValues = mode == StateExportMode.VERBOSE,
                )
            } else {
                null
            },
            keepRecent = mode != null,
        )
        repeat(stateCount, ::transition)
        recorder.beginJournal(clock.now()) { }
        awaitEvents()
    }

    /**
     * Starts a span now and ends it, as an app's or an instrumentation's span that no state changed during.
     */
    fun startAndEndSpan() {
        spanService.startSpan(name = SPAN_NAME, internal = false).stop()
    }

    /**
     * Lets go of the spans that have ended, which Embrace's payload would otherwise hold until a session part ends.
     */
    fun clearCompletedSpans() {
        harness.spanRepository.flushOtelSpans()
        harness.spanRepository.clearCompletedEmbraceSpans()
    }

    /**
     * What every log record is stamped with to say what value each state had: one attribute for each state and the place
     * of the log in the order of state changes, as a whole number.
     */
    fun stampLog(): Map<String, Any> = buildMap {
        val snapshot = recorder.snapshot()
        if (!snapshot.isEmpty()) {
            putAll(snapshot.attributes)
            put(StateSnapshot.SEQUENCE_ATTRIBUTE, snapshot.sequence)
        }
    }

    /**
     * The same as one string that holds every value, which is what the proof of concept stamped before.
     */
    fun stampLogPacked(): Map<String, Any> = buildMap {
        val snapshot = recorder.snapshot()
        if (!snapshot.isEmpty()) {
            put(StateSnapshot.ATTRIBUTE, snapshot.encoded)
            put(StateSnapshot.SEQUENCE_ATTRIBUTE, snapshot.sequence.toString())
        }
    }

    /**
     * Changes the value of the state at [stateIndex], as its instrumentation would report it.
     */
    fun transition(stateIndex: Int) {
        val count = ++transitionCounts[stateIndex]
        recorder.onEvent(
            StateUpdate(
                state = StateCaptureFixture.STATE_NAMES[stateIndex],
                value = StateCaptureFixture.STATE_VALUES[count % StateCaptureFixture.STATE_VALUES.size],
                timeMs = clock.now(),
            ),
        )
    }

    /**
     * Waits for every event queued so far to be emitted and exported.
     */
    fun awaitEvents() {
        eventExecutor.submit { }.get(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /**
     * Waits for every event queued so far to be emitted, then keeps the worker from emitting any more until
     * [releaseEvents]. What is done in between is then the work of the calling thread alone.
     */
    fun holdEvents() {
        check(eventsHeld == null) { "events are already held" }
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        eventExecutor.execute {
            reached.countDown()
            release.await()
        }
        check(reached.await(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "the worker did not reach the hold" }
        eventsHeld = release
    }

    /**
     * Lets the worker run again after [holdEvents], and waits for it to emit everything queued meanwhile.
     */
    fun releaseEvents() {
        checkNotNull(eventsHeld) { "events are not held" }.countDown()
        eventsHeld = null
        awaitEvents()
    }

    override fun close() {
        eventsHeld?.countDown()
        eventExecutor.shutdown()
    }

    /**
     * An app's span exporter that reads every attribute of what it is handed, which is when the values are put on a span
     * that carries them, and keeps nothing.
     */
    private object ReadingSpanExporter : SpanExporter {
        @Volatile
        var attributesRead = 0

        override suspend fun export(telemetry: List<SpanData>): OperationResultCode {
            telemetry.forEach { attributesRead += it.attributes.size }
            return OperationResultCode.Success
        }

        override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
        override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
    }

    private object ReadingLogRecordExporter : LogRecordExporter {
        @Volatile
        var attributesRead = 0

        override suspend fun export(telemetry: List<LogRecordData>): OperationResultCode {
            telemetry.forEach { attributesRead += it.attributes.size }
            return OperationResultCode.Success
        }

        override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
        override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
    }

    private companion object {
        const val SPAN_NAME = "benchmark-span"
        const val PROCESS_IDENTIFIER = "benchmark-process"
        const val EVENT_TIMEOUT_SECONDS = 30L
    }
}
