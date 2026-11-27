package io.embrace.android.embracesdk.internal.otel.state

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.payload.toPayloadString
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.embrace.android.embracesdk.internal.state.PackedStates
import io.embrace.android.embracesdk.internal.state.StateRecorder
import io.embrace.android.embracesdk.internal.state.StateSnapshot
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.logging.data.LogRecordData

/**
 * Ties telemetry to state as it is made: the link on a span and the stamp on a log, each in the form its reader takes.
 *
 * Embrace's own payload gets the link as text and the stamp as one packed attribute. The OpenTelemetry span or log record, which
 * is what the app's own processors and exporters see, gets the numbers as whole numbers and a plain attribute for each state.
 */
class StateTelemetry(
    val recorder: StateRecorder,
    exportMode: () -> StateExportMode,
    receivesLogs: () -> Boolean = { true },

    /**
     * Whether the model's second step is on: spans are linked to state, and changes are replayed for the app's own exporters.
     * With it off a log is still stamped, and nothing else here runs.
     */
    val associationEnabled: Boolean = true,

    /**
     * Called when a log record could not be given the stamp in the form the app's own exporters take, because it could not be
     * written to. Embrace's copy is stamped all the same.
     */
    private val onPlainStampSkipped: () -> Unit = {},
) {

    private val verbose: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
        associationEnabled && exportMode() == StateExportMode.VERBOSE
    }

    private val logsReceived: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION, receivesLogs)

    /**
     * Whether the app registered anything that is handed log records. When it did not, nothing reads the plain form of the stamp,
     * so it is not written, and stamping a log costs the same however many states there are.
     */
    fun appReceivesLogs(): Boolean = logsReceived

    /**
     * Whether the values go on what the app's own exporters get of a span and of a change, beside the numbers.
     */
    fun isVerbose(): Boolean = verbose

    /**
     * The snapshot that held at [timeMs], or null if that is older than what the recorder still knows.
     */
    fun snapshotAt(timeMs: Long): StateSnapshot? = recorder.snapshotAt(timeMs)

    /**
     * Writes the link on the OpenTelemetry [span] that is about to end: the two numbers as whole numbers and, in the verbose
     * rendering, every state's value at its start and each value that differed at its end.
     */
    fun writeSpanExportForm(span: AttributesMutator, atStart: StateSnapshot, atEnd: StateSnapshot?) {
        if (atStart.seq > 0) {
            span.setLongAttribute(AppStateKeys.SEQ, atStart.seq)
        }
        val changedBy = atEnd?.takeIf { it.seq > atStart.seq }
        if (changedBy != null) {
            span.setLongAttribute(AppStateKeys.END_SEQ, changedBy.seq)
        }
        if (!verbose) {
            return
        }
        span.setValues(atStart)
        if (changedBy != null) {
            // states are only ever added, so one at an index in the earlier snapshot is the same state in the later one
            for (i in 0 until changedBy.size) {
                if (i >= atStart.size || changedBy.value(i) != atStart.value(i) || changedBy.isSystem(i) != atStart.isSystem(i)) {
                    val definition = changedBy.definition(i)
                    span.setStringAttribute(definition.endValueKey, changedBy.value(i))
                    if (changedBy.isSystem(i)) {
                        span.setStringAttribute(definition.endValueTypeKey, AppStateKeys.VALUE_TYPE_SYSTEM)
                    }
                }
            }
        }
    }

    /**
     * What kind of log [record] is, which decides the state it is stamped with and where it is sent.
     */
    fun kindOf(record: LogRecordData): LogKind = kindOf(record.attributes)

    /**
     * What kind of log one with [attributes] is, for a caller that has already read them from the record.
     */
    fun kindOf(attributes: Map<String, Any>): LogKind = when (attributes[EMB_TYPE]) {
        NATIVE_CRASH_TYPE, EXIT_TYPE -> LogKind.DEAD_PROCESS
        else -> LogKind.LIVE
    }

    /**
     * The stamp a log sent to Embrace carries for [snapshot]: every state's value packed into one attribute, and the number.
     */
    fun packedStamp(snapshot: StateSnapshot): List<Attribute> = listOf(
        Attribute(AppStateKeys.PACKED, snapshot.packed()),
        Attribute(AppStateKeys.SEQ, snapshot.seqText()),
    )

    /**
     * The attributes a log sent to Embrace carries: its own [attributes], then the stamp for [snapshot].
     */
    fun withPackedStamp(attributes: Map<String, Any>, snapshot: StateSnapshot): List<Attribute> {
        val out = ArrayList<Attribute>(attributes.size + 2)
        for ((key, value) in attributes) {
            out.add(Attribute(key, value.toPayloadString()))
        }
        out.add(Attribute(AppStateKeys.PACKED, snapshot.packed()))
        out.add(Attribute(AppStateKeys.SEQ, snapshot.seqText()))
        return out
    }

    /**
     * Writes the stamp for [snapshot] on the OpenTelemetry log [record]: a plain attribute for each state, and the number.
     */
    fun writeLogExportForm(record: AttributesMutator, snapshot: StateSnapshot) {
        record.setValues(snapshot)
        record.setLongAttribute(AppStateKeys.SEQ, snapshot.seq)
    }

    /**
     * As above, for a record whose type does not say it can be written to. One that cannot is left as it is, and reported, so
     * that an app's exporters going without the stamp is never silent.
     */
    fun writeLogExportForm(record: LogRecordData, snapshot: StateSnapshot) {
        val mutator = record as? AttributesMutator
        if (mutator == null) {
            onPlainStampSkipped()
        } else {
            writeLogExportForm(mutator, snapshot)
        }
    }

    /**
     * Turns the attributes of a log about a process that has died into what Embrace's payload carries. Such a log is given that
     * process's state as plain attributes, which is the form the app's own exporters get, so they are packed here.
     */
    fun packPlainStamp(attributes: Map<String, Any>): List<Attribute> {
        val entries = ArrayList<PackedStates.Entry>()
        val out = ArrayList<Attribute>(attributes.size)
        for ((key, value) in attributes) {
            when {
                key.startsWith(AppStateKeys.VALUE_PREFIX) -> {
                    val name = key.substring(AppStateKeys.VALUE_PREFIX.length)
                    val system = attributes[AppStateKeys.VALUE_TYPE_PREFIX + name] == AppStateKeys.VALUE_TYPE_SYSTEM
                    entries.add(PackedStates.Entry(name, value.toPayloadString().orEmpty(), system))
                }

                key.startsWith(AppStateKeys.VALUE_TYPE_PREFIX) -> Unit
                else -> out.add(Attribute(key, value.toPayloadString()))
            }
        }
        if (entries.isNotEmpty()) {
            out.add(Attribute(AppStateKeys.PACKED, PackedStates.pack(entries)))
        }
        return out
    }

    /**
     * Rewrites the number on a log about a process that has died as a whole number, which is the type the app's own exporters get it
     * as. Such a log is given its attributes as text.
     */
    fun writeDeadProcessSeq(record: LogRecordData) {
        writeDeadProcessSeq(record, record.attributes)
    }

    /**
     * As above, for a caller that has already read the record's [attributes].
     */
    fun writeDeadProcessSeq(record: LogRecordData, attributes: Map<String, Any>) {
        val seq = (attributes[AppStateKeys.SEQ] as? String)?.toLongOrNull() ?: return
        (record as? AttributesMutator)?.setLongAttribute(AppStateKeys.SEQ, seq)
    }

    private fun AttributesMutator.setValues(snapshot: StateSnapshot) {
        for (i in 0 until snapshot.size) {
            val definition = snapshot.definition(i)
            setStringAttribute(definition.valueKey, snapshot.value(i))
            if (snapshot.isSystem(i)) {
                setStringAttribute(definition.valueTypeKey, AppStateKeys.VALUE_TYPE_SYSTEM)
            }
        }
    }

    enum class LogKind {

        /** A log about what is happening now. It is stamped with the state now. */
        LIVE,

        /** A log about a process that has died. It carries that process's state, or none, and never this one's. */
        DEAD_PROCESS,
    }

    private companion object {
        val EMB_TYPE: String = EmbType.State.key
        val NATIVE_CRASH_TYPE: String = EmbType.System.NativeCrash.value
        val EXIT_TYPE: String = EmbType.System.Exit.value
    }
}
