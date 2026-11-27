@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.export.LogInlineExporter
import io.embrace.android.embracesdk.internal.otel.state.StateAttributes
import io.embrace.android.embracesdk.internal.otel.state.StateStamp
import io.embrace.android.embracesdk.internal.otel.state.isStateEvent
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.semconv.EmbCommonAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.data.LogRecordData
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord
import io.opentelemetry.kotlin.semconv.LogAttributes

/**
 * A [LogRecordProcessor] that adds the attributes Embrace requires on every log record, then exports it. An attribute
 * [metadataProvider] gives as a [Long] is added as a whole number, and any other as text.
 *
 * Where [stateProvider] is given, state is stamped by who reads the log. Embrace's own payload carries every state's
 * value as one attribute, [StateAttributes.STATES], and the sequence number [StateAttributes.SEQUENCE], added to the
 * payload as it is stored. The record, which is what an app's own processors and exporters are handed, is stamped after
 * that with one attribute for each state and the sequence number as a whole number, and only where
 * [stampsExternalState] says there is something of the app's to hand it to: otherwise they are not built. Where
 * [stateProvider] is not given, [metadataProvider] stamps whatever form of state it supplies on the record, and both
 * readers see it.
 *
 * A record that is an event describing state is handed to [stateEventExporter] as it is, if there is one: nothing is
 * added to it, and it is not exported with the logs.
 */
internal class EmbraceLogRecordProcessor(
    private val uuidSource: UuidSource,
    private val metadataProvider: Provider<Map<String, Any>>,
    private val logRecordExporter: LogInlineExporter,
    private val stateEventExporter: ((List<LogRecordData>) -> Unit)? = null,
    private val stateProvider: (() -> StateStamp?)? = null,
    private val stampsExternalState: Boolean = false,
) : LogRecordProcessor {

    @Volatile
    private var lastPayloadAttributes: Pair<StateStamp, List<Attribute>>? = null

    override suspend fun forceFlush(): OperationResultCode = logRecordExporter.forceFlush()

    override fun onEmit(log: ReadWriteLogRecord, context: Context) {
        if (stateEventExporter != null && context.isStateEvent()) {
            stateEventExporter.invoke(listOf(log))
            return
        }

        val attributes = log.attributes

        if (!attributes.containsKey(LogAttributes.LOG_RECORD_UID)) {
            log.setStringAttribute(LogAttributes.LOG_RECORD_UID, uuidSource.createUuid())
        }

        val ignoreMetadata = attributes.ignoreCurrentProcessMetadata()
        if (!ignoreMetadata) {
            stampMetadata(log, attributes, withState = !attributes.describesAnotherProcess())
        }

        // exported on the calling thread: a crash log has to reach the sink before crash teardown
        // persists the payload and the process dies. See [InlineExporter].
        val states = if (attributes.describesAnotherProcess()) null else stateProvider?.invoke()?.takeUnless { it.isEmpty() }
        if (states == null || attributes.containsKey(StateAttributes.STATES)) {
            logRecordExporter.exportInline(listOf(log))
            return
        }
        // Embrace's payload is stored first, from a record with none of the plain attributes on it, and has the packed
        // attribute added. The plain ones go on the record after that, for what an app's own processors and exporters see.
        var stamped = false
        logRecordExporter.exportInline(
            log,
            payloadAttributes(states),
        ) {
            stamped = true
            stampPlainState(log, states)
        }
        if (!stamped) {
            stampPlainState(log, states)
        }
    }

    /**
     * Stamps [log] with the metadata. The packed stamp of state that [metadataProvider] may supply is left out unless
     * [withState], whichever path would have put it there, as it describes this process.
     */
    private fun stampMetadata(log: ReadWriteLogRecord, attributes: Map<String, Any>, withState: Boolean) {
        // the experiment records attribute is reserved for the SDK, so any value found here should be erased.
        if (attributes.containsKey(EmbCommonAttributes.EMB_EXPERIMENTS)) {
            log.setStringAttribute(EmbCommonAttributes.EMB_EXPERIMENTS, "")
        }

        metadataProvider().forEach { (key, value) ->
            when {
                !withState && (key == StateAttributes.STATES || key == StateAttributes.SEQUENCE) -> Unit
                // the reserved experiments value always wins, but only non-private logs carry it
                key == EmbCommonAttributes.EMB_EXPERIMENTS -> {
                    if (!attributes.isPrivate()) {
                        log.setStringAttribute(key, value.toString())
                    }
                }
                // never override what the instrumentation has already set. Telemetry that knows which
                // session it belongs to - such as an app exit reported by a later process - sets the
                // session attributes itself, and leaves them blank if the session it describes is unknown.
                // A whole number stays one, and everything else is text.
                !attributes.containsKey(key) -> when (value) {
                    is Long -> log.setLongAttribute(key, value)
                    else -> log.setStringAttribute(key, value.toString())
                }
            }
        }
    }

    /**
     * Stamps the record with what an app's own processors and exporters get of state, if there is something of the app's
     * to get it. Otherwise nothing is built.
     */
    private fun stampPlainState(log: ReadWriteLogRecord, states: StateStamp) {
        if (!stampsExternalState) {
            return
        }
        // read once, as each read builds a map. The stamp's keys are distinct, and the number goes first so that it is not
        // what the limit on a record's attributes drops.
        val present = log.attributes
        if (!present.containsKey(StateAttributes.SEQUENCE)) {
            log.setLongAttribute(StateAttributes.SEQUENCE, states.sequence)
        }
        states.attributes.forEach { (key, value) ->
            if (!present.containsKey(key)) {
                log.setStringAttribute(key, value)
            }
        }
    }

    /**
     * What Embrace's payload carries of [states] beyond the record's attributes. Every log between two changes has the
     * same, so the last is kept.
     */
    private fun payloadAttributes(states: StateStamp): List<Attribute> {
        val cached = lastPayloadAttributes
        if (cached != null && cached.first === states) {
            return cached.second
        }
        val built = listOf(
            Attribute(StateAttributes.STATES, states.encoded),
            Attribute(StateAttributes.SEQUENCE, states.sequence.toString()),
        )
        lastPayloadAttributes = states to built
        return built
    }

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success

    /**
     * Whether the record describes a process that has already died, in which case none of the current
     * metadata applies to it. A native crash is resurrected by a later process and carries the whole state of
     * the session that crashed - its session IDs, app state and session properties - so enriching it would
     * describe the wrong session entirely.
     */
    private fun Map<String, Any>.ignoreCurrentProcessMetadata(): Boolean =
        get(EmbType.System.NativeCrash.key) == EmbType.System.NativeCrash.value

    /**
     * Whether the record is about a process other than the one emitting it, such as the exit record or the native crash
     * that a launch writes about the last process. Whatever state this process has describes none of what such a record
     * is about, so it is given no state stamp.
     *
     * Such a record is recognized by what it says of itself and not by its type, so a record of a type added later is
     * covered too: one that names the session part it belongs to has been filed under another process's session already
     * (the exit record names it blank, as the process it is about is not this one), and a native crash is always about
     * a process that has died.
     */
    private fun Map<String, Any>.describesAnotherProcess(): Boolean =
        containsKey(EmbSessionAttributes.EMB_SESSION_PART_ID) || ignoreCurrentProcessMetadata()

    private fun Map<String, Any>.isPrivate(): Boolean =
        get(PrivateSpan.key) == PrivateSpan.value
}
