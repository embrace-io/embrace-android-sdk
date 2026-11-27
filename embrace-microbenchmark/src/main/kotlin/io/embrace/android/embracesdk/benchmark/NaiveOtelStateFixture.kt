package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.opentelemetry.kotlin.tracing.model.ReadableSpan
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * [stateCount] states recorded the way OpenTelemetry's Android SDK records them, which is as naive as recording can be: no
 * recorder, no order between states and nothing kept for a reader to rebuild a state from.
 *
 * - Each state has a live holder of its own, an [AtomicReference] that is read independently of the others.
 * - Every span is stamped as it starts by a [SpanProcessor] for each state, which sets the attribute `emb.state.<name>` to what
 *   that state's holder holds. Every log is stamped by the SDK's event metadata provider with the same attribute for each state.
 * - Every change of a state is a log record of its own, emitted once the holder has changed, saying which state changed and
 *   to what. What stamps every other log leaves a change event alone, so that [changeEventContent] decides what it
 *   carries: every state's value, as it would have if it were stamped like any log, or the change and nothing else,
 *   which is the least a record of a change can be.
 *
 * Session parts are written as multiple files as [StateCaptureFixture] writes them, with a worker of its own, so the same
 * measurements can be taken of what each way of recording delivers.
 */
internal class NaiveOtelStateFixture(
    val stateCount: Int,
    private val storageDir: File,
    private val clock: Clock = NormalizedIntervalClock(),
    private val changeEventContent: ChangeEventContent = ChangeEventContent.NEW_VALUE_ONLY,
    keepParts: Boolean = false,
) : AutoCloseable {

    /**
     * What a change event says about the change.
     */
    enum class ChangeEventContent {
        /**
         * The state that changed and its new value, and nothing else.
         */
        NEW_VALUE_ONLY,

        /**
         * The state that changed and its new value, and the value of every state as well, as an entity state event of
         * OpenTelemetry carries the complete description of the entity each time.
         */
        EVERY_VALUE,
    }

    private val holders = List(stateCount) { AtomicReference(StateCaptureFixture.INITIAL_STATE_VALUE) }
    private val transitionCounts = IntArray(stateCount)

    /**
     * Whether the calling thread is emitting a change event, which is the one log that is not stamped.
     */
    private val emittingChangeEvent = ThreadLocal.withInitial { false }

    private val externalOtelConfig = ExternalOtelConfig().also { config ->
        repeat(stateCount) { config.addSpanProcessor(StateStampProcessor(stateAttribute(it), holders[it])) }
    }
    private val harness = TelemetryDestinationHarness(
        useKotlinSdk = true,
        clock = clock,
        externalOtelConfig = externalOtelConfig,
        eventMetadataProvider = { if (emittingChangeEvent.get() == true) emptyMap() else stamp() },
        // the app state data model is off: states are recorded the way an app could record them without it
        config = EmbraceConfig(persistence = { PersistenceConfig(multiFileEnabled = { true }) }),
    )
    private val parts = SessionPartFiles(harness, harness.configService, storageDir, clock, keepParts, stateRecorder = null)
    private val ordinarySpanService: SpanService = harness.createUncappedSpanService()

    /**
     * Ends the current session part and starts the next, and returns the id of the part that ended.
     */
    fun changeSessionPart(): String = parts.changeSessionPart()

    /**
     * Changes the value of the state at [stateIndex], and emits the log record that says so.
     */
    fun transition(stateIndex: Int) {
        val count = ++transitionCounts[stateIndex]
        val newValue = StateCaptureFixture.STATE_VALUES[count % StateCaptureFixture.STATE_VALUES.size]
        holders[stateIndex].set(newValue)

        val values = if (changeEventContent == ChangeEventContent.EVERY_VALUE) stamp() else emptyMap()
        emittingChangeEvent.set(true)
        try {
            harness.destination.addLog(
                schemaType = StateChange(StateCaptureFixture.STATE_NAMES[stateIndex], newValue, values),
                severity = LogSeverity.INFO,
                message = CHANGE_EVENT_MESSAGE,
                timestampMs = clock.now(),
            )
        } finally {
            emittingChangeEvent.remove()
        }
    }

    /**
     * Changes the value of every state [transitionsPerState] times, one state after another.
     */
    fun transitionEveryState(transitionsPerState: Int) {
        repeat(transitionsPerState) {
            repeat(stateCount, ::transition)
        }
    }

    /**
     * Starts and stops one ordinary span, which is stamped as it starts.
     */
    fun recordSpan() {
        ordinarySpanService.createSpan(name = ORDINARY_SPAN_NAME).apply {
            start()
            stop()
        }
    }

    /**
     * Emits one ordinary log, which is stamped as it is emitted.
     */
    fun recordLog() {
        harness.destination.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = LogSeverity.INFO,
            message = ORDINARY_LOG_MESSAGE,
            timestampMs = clock.now(),
        )
    }

    /**
     * What every state holds now, as the attributes a log is stamped with, one for each state.
     */
    fun stamp(): Map<String, String> = buildMap {
        holders.forEachIndexed { index, holder -> put(stateAttribute(index), holder.get()) }
    }

    /**
     * The logs captured so far in the order the log sink holds them, which is the order they were emitted in.
     */
    fun logs(): List<Log> = harness.logSink.logsForNextBatch()

    /**
     * The envelope the SDK would deliver the logs captured so far in.
     */
    fun logEnvelope(): Envelope<LogPayload> = logEnvelope(logs())

    /**
     * Empties the log sink, as delivering a batch of logs does, and returns how many logs it held.
     */
    fun drainLogs(): Int = harness.logSink.flushBatch().size

    /**
     * Waits for everything queued for the worker so far to be written.
     */
    fun awaitWrites() = parts.awaitWrites()

    /**
     * Waits for everything queued for the worker so far to be written, then keeps the worker from running anything more
     * until [releaseWrites].
     */
    fun holdWrites() = parts.holdWrites()

    /**
     * Lets the worker run again after [holdWrites], and waits for it to write everything queued meanwhile.
     */
    fun releaseWrites() = parts.releaseWrites()

    /**
     * Where the session part with [sessionPartId] is on disk, once everything queued for the worker has been written.
     */
    fun partDirectory(sessionPartId: String): SessionPartDirectory = parts.partDirectory(sessionPartId)

    /**
     * Reads the session part in [directory] back from disk as the envelope that would be delivered for it. The part must
     * have ended, and the fixture must keep its parts.
     */
    fun readPart(directory: SessionPartDirectory): Envelope<SessionPartPayload> = parts.readPart(directory)

    /**
     * The directory holding the files of the session part with [sessionPartId].
     */
    fun partDir(sessionPartId: String): File = parts.partDir(sessionPartId)

    override fun close() = parts.close()

    /**
     * Sets the attribute of one state on every span as it starts, to what the state's holder holds then.
     */
    private class StateStampProcessor(
        private val attribute: String,
        private val holder: AtomicReference<String>,
    ) : SpanProcessor {

        override fun onStart(span: ReadWriteSpan, parentContext: Context) {
            span.setStringAttribute(attribute, holder.get())
        }

        override fun onEnding(span: ReadWriteSpan) = Unit
        override fun onEnd(span: ReadableSpan) = Unit
        override fun isStartRequired(): Boolean = true
        override fun isEndRequired(): Boolean = false
        override fun isOnEndingRequired(): Boolean = false
        override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
        override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
    }

    /**
     * The schema of a change event: a log of the `state` type, which carries what [StateCaptureFixture]'s spans have as events
     * and so is the nearest the schema has to a record of a state change. It is told apart from the logs of the other states
     * by [STATE_NAME_ATTRIBUTE], and carries [values] as the attribute each state has when it stamps.
     */
    private class StateChange(
        stateName: String,
        newValue: String,
        values: Map<String, String>,
    ) : SchemaType.State<String>(newValue, stateName) {
        override val schemaAttributes: Map<String, String> = values + mapOf(
            STATE_NAME_ATTRIBUTE to stateName,
            NEW_VALUE_ATTRIBUTE to newValue,
        )
    }

    companion object {
        /**
         * The envelope the SDK would deliver [logs] in.
         */
        fun logEnvelope(logs: List<Log>): Envelope<LogPayload> =
            Envelope(version = ENVELOPE_VERSION, type = LOG_ENVELOPE_TYPE, data = LogPayload(logs = logs))

        /**
         * The attribute of a log or span that says what a state held, by the state's index.
         */
        fun stateAttribute(stateIndex: Int): String = "$STATE_ATTRIBUTE_PREFIX${StateCaptureFixture.STATE_NAMES[stateIndex]}"

        /**
         * The attribute of a change event that says which state changed.
         */
        const val STATE_NAME_ATTRIBUTE: String = "emb.state.name"

        /**
         * The attribute of a change event that says what the state changed to.
         */
        const val NEW_VALUE_ATTRIBUTE: String = "emb.state.new_value"

        /**
         * The prefix of the attribute every span and log is stamped with for a state.
         */
        const val STATE_ATTRIBUTE_PREFIX: String = "emb.state."

        /**
         * The name every ordinary span has.
         */
        const val ORDINARY_SPAN_NAME: String = "ordinary-work"

        /**
         * The body of every ordinary log.
         */
        const val ORDINARY_LOG_MESSAGE: String = "ordinary-log-message"

        private const val CHANGE_EVENT_MESSAGE = "state-change"
        private const val ENVELOPE_VERSION = "0.1.0"
        private const val LOG_ENVELOPE_TYPE = "logs"
    }
}
