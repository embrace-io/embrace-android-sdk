package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.config.resolved.AppStateConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.embrace.android.embracesdk.internal.state.StateDefinition
import io.embrace.android.embracesdk.internal.state.StateJournal
import io.embrace.android.embracesdk.internal.state.StateRecorder
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
 * How telemetry is associated with state in a benchmark, by the two switches of the app state data model and what the app
 * registered to receive telemetry. The first step stamps logs and replays changes; the second links spans. [exportMode] is
 * the rendering the app's own exporters are given, as the config names it:
 * empty leaves the SDK to choose by what the app registered, which is the verbose rendering where the app has a span exporter
 * and no log exporter.
 */
enum class StateAssociationMode(
    internal val firstStep: Boolean,
    internal val secondStep: Boolean,
    internal val exportMode: String,
    internal val appLogExporter: Boolean = true,
) {

    /**
     * The model is off: nothing is stamped or linked, and there is no recorder. The floor to compare telemetry with.
     */
    NONE(firstStep = false, secondStep = false, exportMode = ""),

    /**
     * The first step alone: a log is stamped and every change is replayed to the app's log exporter, and a span is not linked.
     */
    FIRST_STEP(firstStep = true, secondStep = false, exportMode = ""),

    /**
     * Both steps, for an app that has a span exporter and no log exporter, which gets the values on its spans and no replay.
     */
    NO_APP_LOG_EXPORTER(firstStep = true, secondStep = true, exportMode = "", appLogExporter = false),

    /**
     * Both steps, with the association rendering: a span has the two numbers and a change event the change alone.
     */
    ASSOCIATION(firstStep = true, secondStep = true, exportMode = "association"),

    /**
     * Both steps, with the verbose rendering: values on a span and on a change event as well.
     */
    VERBOSE(firstStep = true, secondStep = true, exportMode = "verbose"),
    ;

    /**
     * Whether a change is replayed as an event for the app's log exporter, which the first step does for an app that has one.
     */
    internal val replaysChanges: Boolean get() = firstStep && appLogExporter
}

/**
 * [stateCount] states that telemetry is associated with as [mode] says: a span, with the second step, by the number of the last
 * state change before it started and before it ended, and a log by the packed attribute in Embrace's payload and, where an app
 * has a log exporter, one attribute for each state on what that exporter is handed. With the first step, changes are also
 * replayed to that exporter.
 *
 * It runs the SDK's real recorder, with the telemetry the SDK makes of it (`createStateTelemetry`, `startStateReplay`), its span
 * stack and its logging pipeline, with an app's span exporter and log exporter that read what they are handed and keep nothing. It
 * persists nothing, so what a benchmark measures here is the association and the replay alone. The recorder has a session part open and a
 * journal that takes any number of changes and discards them, as it would behave in an app. [startPart] opens the next part,
 * which a long run needs for the recorder's limit on the changes of one part.
 *
 * State events are emitted on a worker of the fixture's own, which [holdEvents] keeps from running.
 */
internal class StateAssociationFixture(
    val stateCount: Int,
    private val mode: StateAssociationMode,
    private val clock: Clock = NormalizedIntervalClock(),
) : AutoCloseable {

    private val eventExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var eventsHeld: CountDownLatch? = null
    private val transitionCounts = IntArray(stateCount)
    private var partNumber = 0

    private val spanExporter = ReadingSpanExporter()
    private val logExporter = ReadingLogRecordExporter()
    private val harness = TelemetryDestinationHarness(
        useKotlinSdk = true,
        clock = clock,
        externalOtelConfig = ExternalOtelConfig().apply {
            addSpanExporter(spanExporter)
            if (mode.appLogExporter) {
                addLogExporter(logExporter)
            }
        },
        config = EmbraceConfig(
            appState = {
                AppStateConfig(
                    enabled = { mode.firstStep },
                    associationEnabled = { mode.secondStep },
                    exportMode = { mode.exportMode },
                )
            },
            persistence = { PersistenceConfig(multiFileEnabled = { true }) },
        ),
    )
    private val spanService: SpanService = harness.createUncappedSpanService()
    private val recorder: StateRecorder? = harness.stateTelemetry?.recorder

    init {
        recorder?.let { recorder ->
            harness.startReplay(BackgroundWorker(eventExecutor))
            // the journal discards what it is handed, but is there, so that each change is made into the record it is kept as
            recorder.attach(StateJournal { })
            // each state is declared with as high a limit as the benchmark makes changes, so that what is measured is a change
            // that is recorded
            repeat(stateCount) { index ->
                val definition = StateDefinition(StateCaptureFixture.STATE_NAMES[index], changeLimit = Int.MAX_VALUE)
                check(recorder.declare(definition, StateCaptureFixture.INITIAL_STATE_VALUE, false, clock.now()))
            }
            // the part is open before any state has a new value, so that each state's first change is recorded and
            // replayed, as every change a benchmark makes after it is
            startPart()
            repeat(stateCount, ::transition)
            awaitEvents()
        }
    }

    /**
     * Starts the next session part, as the SDK does when the app changes between foreground and background. The one before
     * it ends, and the limit on the changes of one part starts again.
     */
    fun startPart() {
        checkNotNull(recorder).openPart("%032x".format(++partNumber), clock.now())
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
     * Emits an ordinary log through the real logging pipeline, which stamps it for each reader: the packed attribute added
     * to Embrace's payload, and the plain attributes on what an app's exporter is handed where it has one.
     */
    fun recordLog() {
        harness.destination.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = LogSeverity.INFO,
            message = LOG_MESSAGE,
            timestampMs = clock.now(),
        )
    }

    /**
     * Lets go of the logs stored so far, which Embrace's payload would otherwise hold until they are sent.
     */
    fun clearStoredLogs() {
        harness.logSink.flushBatch()
    }

    /**
     * What a log record is stamped with for an app's own processors and exporters: one attribute for each state, with another
     * that says the value is one the SDK generated, and the number of the last change as a whole number. This is what
     * `StateTelemetry.writeLogExportForm` writes, built as a map as a record can only be written to by the log pipeline. It is
     * built only where an app has configured something that receives logs.
     */
    fun stampLog(): Map<String, Any> = buildMap {
        val snapshot = checkNotNull(recorder).current()
        if (snapshot.size > 0) {
            for (i in 0 until snapshot.size) {
                val definition = snapshot.definition(i)
                put(definition.valueKey, snapshot.value(i))
                if (snapshot.isSystem(i)) {
                    put(definition.valueTypeKey, AppStateKeys.VALUE_TYPE_SYSTEM)
                }
            }
            put(AppStateKeys.SEQ, snapshot.seq)
        }
    }

    /**
     * The same as one string that holds every value, which is what Embrace's own payload is stamped with.
     */
    fun stampLogPacked(): List<Attribute> {
        val snapshot = checkNotNull(recorder).current()
        return if (snapshot.size > 0) checkNotNull(harness.stateTelemetry).packedStamp(snapshot) else emptyList()
    }

    /**
     * Changes the value of the state at [stateIndex], as its data source would report it.
     */
    fun transition(stateIndex: Int) {
        val count = ++transitionCounts[stateIndex]
        checkNotNull(recorder).update(
            state = StateCaptureFixture.STATE_NAMES[stateIndex],
            value = StateCaptureFixture.STATE_VALUES[count % StateCaptureFixture.STATE_VALUES.size],
            system = false,
            timeMs = clock.now(),
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
     * What the app's exporters have been handed so far, to tell that a mode does what it is named for.
     */
    fun exported(): Exported = Exported(
        spans = spanExporter.spans,
        spansLinked = spanExporter.linked,
        spansWithValues = spanExporter.withValues,
        logs = logExporter.logs,
        logsStamped = logExporter.stamped,
        stateEvents = logExporter.stateEvents,
    )

    /**
     * The attributes of the logs Embrace's own payload holds so far.
     */
    fun storedLogAttributes(): List<Map<String, String?>> =
        harness.logSink.logsForNextBatch().map { log -> log.attributes.orEmpty().associate { checkNotNull(it.key) to it.data } }

    /**
     * What an app's exporters have been handed, as [exported] counts it.
     */
    class Exported(
        val spans: Int,
        val spansLinked: Int,
        val spansWithValues: Int,
        val logs: Int,
        val logsStamped: Int,
        val stateEvents: Int,
    )

    /**
     * An app's span exporter that reads every attribute of what it is handed, which is when the values are put on a span
     * that carries them, and keeps only a count of what it saw.
     */
    private class ReadingSpanExporter : SpanExporter {
        @Volatile
        var attributesRead = 0

        @Volatile
        var spans = 0

        @Volatile
        var linked = 0

        @Volatile
        var withValues = 0

        override suspend fun export(telemetry: List<SpanData>): OperationResultCode {
            telemetry.forEach { span ->
                attributesRead += span.attributes.size
                spans++
                if (span.attributes.containsKey(AppStateKeys.SEQ)) {
                    linked++
                }
                if (span.attributes.keys.any { it.startsWith(AppStateKeys.VALUE_PREFIX) }) {
                    withValues++
                }
            }
            return OperationResultCode.Success
        }

        override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
        override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
    }

    private class ReadingLogRecordExporter : LogRecordExporter {
        @Volatile
        var attributesRead = 0

        @Volatile
        var logs = 0

        @Volatile
        var stamped = 0

        @Volatile
        var stateEvents = 0

        override suspend fun export(telemetry: List<LogRecordData>): OperationResultCode {
            telemetry.forEach { record ->
                attributesRead += record.attributes.size
                if (record.eventName?.startsWith(AppStateKeys.STEM) == true) {
                    stateEvents++
                } else {
                    logs++
                    if (record.attributes.containsKey(AppStateKeys.SEQ)) {
                        stamped++
                    }
                }
            }
            return OperationResultCode.Success
        }

        override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
        override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
    }

    private companion object {
        const val SPAN_NAME = "benchmark-span"
        const val LOG_MESSAGE = "benchmark-log"
        const val EVENT_TIMEOUT_SECONDS = 30L
    }
}
