package io.embrace.android.embracesdk.internal.otel.state

import io.embrace.android.embracesdk.fakes.FakeAttributesMutator
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.state.StateTelemetry.LogKind
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.embrace.android.embracesdk.internal.state.StateDefinition
import io.embrace.android.embracesdk.internal.state.StateRecorder
import io.embrace.android.embracesdk.internal.state.StateSnapshot
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.logging.data.LogRecordData
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class StateTelemetryTest {

    private lateinit var clock: FakeClock
    private lateinit var recorder: StateRecorder
    private lateinit var association: StateTelemetry
    private lateinit var verbose: StateTelemetry

    @Before
    fun setUp() {
        clock = FakeClock(START_MS)
        recorder = StateRecorder(clock)
        association = StateTelemetry(recorder, exportMode = { StateExportMode.ASSOCIATION })
        verbose = StateTelemetry(recorder, exportMode = { StateExportMode.VERBOSE })
    }

    @Test
    fun `the rendering is the one the export mode names`() {
        assertEquals(false, association.isVerbose())
        assertEquals(true, verbose.isVerbose())
    }

    @Test
    fun `with the second step off no values go on a span, and a replayed change still follows the rendering`() {
        StateExportMode.entries.forEach { mode ->
            val telemetry = StateTelemetry(recorder, exportMode = { mode }, associationEnabled = false)
            assertEquals(false, telemetry.isVerbose())
            assertEquals(mode == StateExportMode.VERBOSE, telemetry.isVerboseReplay())
        }
    }

    @Test
    fun `values go on a span only in the verbose rendering, with the second step on and something to receive spans`() {
        fun telemetry(mode: StateExportMode, receivesSpans: Boolean) =
            StateTelemetry(recorder, exportMode = { mode }, receivesSpans = { receivesSpans })

        assertEquals(true, telemetry(StateExportMode.VERBOSE, receivesSpans = true).isVerbose())
        assertEquals(false, telemetry(StateExportMode.VERBOSE, receivesSpans = false).isVerbose())
        assertEquals(false, telemetry(StateExportMode.ASSOCIATION, receivesSpans = true).isVerbose())
        // what a replayed change carries does not depend on spans
        assertEquals(true, telemetry(StateExportMode.VERBOSE, receivesSpans = false).isVerboseReplay())
    }

    @Test
    fun `a stamp that throws as it is written is reported as skipped and does not reach the caller`() {
        var skipped = 0
        val telemetry = StateTelemetry(
            recorder,
            exportMode = { StateExportMode.ASSOCIATION },
            onPlainStampSkipped = { skipped++ },
        )
        val record = FakeReadWriteLogRecord()
        val throwing = object : LogRecordData by record, AttributesMutator by record {
            override val attributes: Map<String, Any> = mapOf(AppStateKeys.SEQ to "12")
            override fun setStringAttribute(key: String, value: String) = error("cannot be written")
            override fun setLongAttribute(key: String, value: Long) = error("cannot be written")
        }

        telemetry.writeLogExportForm(throwing as LogRecordData, twoStates())
        telemetry.writeDeadProcessSeq(throwing, throwing.attributes)

        assertEquals(2, skipped)
    }

    @Test
    fun `only the log of a process that has died is of that kind, and an event name changes nothing`() {
        assertEquals(LogKind.DEAD_PROCESS, association.kindOf(log(EmbType.System.NativeCrash.value).attributes))
        assertEquals(LogKind.DEAD_PROCESS, association.kindOf(log(EmbType.System.Exit.value).attributes))
        assertEquals(LogKind.LIVE, association.kindOf(log(EmbType.State.value, AppStateKeys.CHANGE_EVENT_NAME).attributes))
        assertEquals(LogKind.LIVE, association.kindOf(log(EmbType.State.value, AppStateKeys.SNAPSHOT_EVENT_NAME).attributes))
        assertEquals(LogKind.LIVE, association.kindOf(log(EmbType.State.value, "another.event").attributes))
        assertEquals(LogKind.LIVE, association.kindOf(log(EmbType.State.value, null).attributes))
        assertEquals(LogKind.LIVE, association.kindOf(log(EmbType.System.Log.value).attributes))
        assertEquals(LogKind.LIVE, association.kindOf(log(null, AppStateKeys.CHANGE_EVENT_NAME).attributes))
        assertEquals(LogKind.LIVE, association.kindOf(log(null).attributes))
    }

    @Test
    fun `whether the app receives logs is what the supplier says, and true by default`() {
        assertEquals(true, association.appReceivesLogs())
        val mode = StateExportMode.ASSOCIATION
        val noLogs = StateTelemetry(recorder, exportMode = { mode }, receivesLogs = { false })
        assertEquals(false, noLogs.appReceivesLogs())
    }

    @Test
    fun `the kind of a log is read from its type and nothing else`() {
        listOf(
            EmbType.System.NativeCrash.value to LogKind.DEAD_PROCESS,
            EmbType.System.Exit.value to LogKind.DEAD_PROCESS,
            EmbType.State.value to LogKind.LIVE,
            EmbType.System.Log.value to LogKind.LIVE,
        ).forEach { (type, kind) ->
            assertEquals(kind, association.kindOf(mapOf<String, Any>(EmbType.State.key to type)))
        }
        assertEquals(LogKind.LIVE, association.kindOf(emptyMap()))
        assertEquals(LogKind.LIVE, association.kindOf(mapOf<String, Any>("other" to EmbType.System.NativeCrash.value)))
    }

    @Test
    fun `a log's own attributes are followed by the packed stamp`() {
        val attributes = linkedMapOf<String, Any>("first" to "x", "count" to 3L)

        assertEquals(
            listOf(
                Attribute("first", "x"),
                Attribute("count", "3"),
                Attribute(AppStateKeys.PACKED, "network=wifi;screen-automatic~Initializing"),
                Attribute(AppStateKeys.SEQ, "2"),
            ),
            association.withPackedStamp(attributes, twoStates()),
        )
    }

    @Test
    fun `an attribute a log already has under one of the stamp's keys is replaced by the stamp, not sent beside it`() {
        val attributes = linkedMapOf<String, Any>(
            AppStateKeys.PACKED to "network=made-up",
            "first" to "x",
            AppStateKeys.SEQ to 99L,
        )

        assertEquals(
            listOf(
                Attribute("first", "x"),
                Attribute(AppStateKeys.PACKED, "network=wifi;screen-automatic~Initializing"),
                Attribute(AppStateKeys.SEQ, "2"),
            ),
            association.withPackedStamp(attributes, twoStates()),
        )
    }

    @Test
    fun `the number of a dead process given with its attributes becomes a long`() {
        val record = FakeReadWriteLogRecord()
        record.setStringAttribute(AppStateKeys.SEQ, "12")

        association.writeDeadProcessSeq(record, mapOf(AppStateKeys.SEQ to "12"))
        assertEquals(12L, record.attributes[AppStateKeys.SEQ])

        val other = FakeReadWriteLogRecord()
        other.setStringAttribute(AppStateKeys.SEQ, "12")
        association.writeDeadProcessSeq(other, emptyMap())
        assertEquals("12", other.attributes[AppStateKeys.SEQ])
    }

    @Test
    fun `the stamp for Embrace is the packed values and the number as text`() {
        assertEquals(
            listOf(
                Attribute(AppStateKeys.PACKED, "network=wifi;screen-automatic~Initializing"),
                Attribute(AppStateKeys.SEQ, "2"),
            ),
            association.packedStamp(twoStates()),
        )
    }

    @Test
    fun `the stamp for the app's own exporters is a plain value for each state and the number as a long`() {
        val record = FakeAttributesMutator()
        association.writeLogExportForm(record, twoStates())

        assertEquals(
            mapOf<String, Any>(
                "${AppStateKeys.VALUE_PREFIX}$NETWORK" to "wifi",
                "${AppStateKeys.VALUE_PREFIX}$SCREEN" to "Initializing",
                "${AppStateKeys.VALUE_TYPE_PREFIX}$SCREEN" to AppStateKeys.VALUE_TYPE_SYSTEM,
                AppStateKeys.SEQ to 2L,
            ),
            record.attributes,
        )
    }

    @Test
    fun `the plain stamp of a record that can be written to is written, with no skip reported`() {
        var skipped = 0
        val telemetry = StateTelemetry(
            recorder,
            exportMode = { StateExportMode.ASSOCIATION },
            onPlainStampSkipped = { skipped++ },
        )
        val record = FakeReadWriteLogRecord()
        val data: LogRecordData = record

        telemetry.writeLogExportForm(data, twoStates())

        assertEquals(
            mapOf<String, Any>(
                "${AppStateKeys.VALUE_PREFIX}$NETWORK" to "wifi",
                "${AppStateKeys.VALUE_PREFIX}$SCREEN" to "Initializing",
                "${AppStateKeys.VALUE_TYPE_PREFIX}$SCREEN" to AppStateKeys.VALUE_TYPE_SYSTEM,
                AppStateKeys.SEQ to 2L,
            ),
            record.attributes,
        )
        assertEquals(0, skipped)
    }

    @Test
    fun `a record that cannot be written to is left as it is and the skip is reported once`() {
        var skipped = 0
        val telemetry = StateTelemetry(
            recorder,
            exportMode = { StateExportMode.ASSOCIATION },
            onPlainStampSkipped = { skipped++ },
        )
        val readOnly = object : LogRecordData by FakeReadWriteLogRecord() {}

        telemetry.writeLogExportForm(readOnly, twoStates())

        assertEquals(1, skipped)
        assertEquals(emptyMap<String, Any>(), readOnly.attributes)
    }

    @Test
    fun `plain values of a dead process are packed and the other attributes pass through in order`() {
        val attributes = linkedMapOf<String, Any>(
            "first" to "x",
            "${AppStateKeys.VALUE_PREFIX}$NETWORK" to "wifi",
            "${AppStateKeys.VALUE_TYPE_PREFIX}$SCREEN" to AppStateKeys.VALUE_TYPE_SYSTEM,
            AppStateKeys.SEQ to "7",
            "${AppStateKeys.VALUE_PREFIX}$SCREEN" to "Initializing",
            "${AppStateKeys.VALUE_TYPE_PREFIX}orphan" to AppStateKeys.VALUE_TYPE_SYSTEM,
            "count" to 3L,
        )

        assertEquals(
            listOf(
                Attribute("first", "x"),
                Attribute(AppStateKeys.SEQ, "7"),
                Attribute("count", "3"),
                Attribute(AppStateKeys.PACKED, "network=wifi;screen-automatic~Initializing"),
            ),
            association.packPlainStamp(attributes),
        )
    }

    @Test
    fun `attributes with no plain values are not given a packed attribute`() {
        assertEquals(
            listOf(Attribute("first", "x")),
            association.packPlainStamp(mapOf("first" to "x", "${AppStateKeys.VALUE_TYPE_PREFIX}$SCREEN" to "system")),
        )
    }

    @Test
    fun `the number on a log of a dead process becomes a long`() {
        val record = FakeReadWriteLogRecord()
        record.setStringAttribute(AppStateKeys.SEQ, "12")
        association.writeDeadProcessSeq(record, record.attributes)
        assertEquals(12L, record.attributes[AppStateKeys.SEQ])
    }

    @Test
    fun `a number on a log of a dead process that is absent or not a number is left alone`() {
        val absent = FakeReadWriteLogRecord()
        association.writeDeadProcessSeq(absent, absent.attributes)
        assertEquals(emptyMap<String, Any>(), absent.attributes)

        val notANumber = FakeReadWriteLogRecord()
        notANumber.setStringAttribute(AppStateKeys.SEQ, "twelve")
        association.writeDeadProcessSeq(notANumber, notANumber.attributes)
        assertEquals("twelve", notANumber.attributes[AppStateKeys.SEQ])

        val alreadyLong = FakeReadWriteLogRecord()
        alreadyLong.setLongAttribute(AppStateKeys.SEQ, 5L)
        association.writeDeadProcessSeq(alreadyLong, alreadyLong.attributes)
        assertEquals(5L, alreadyLong.attributes[AppStateKeys.SEQ])
    }

    @Test
    fun `association writes only the numbers on an exported span`() {
        val atStart = twoStates()
        recorder.update(NETWORK, "cell", false, clock.now())
        val atEnd = recorder.current()

        val span = FakeAttributesMutator()
        association.writeSpanExportForm(span, atStart, atEnd)

        assertEquals(mapOf<String, Any>(AppStateKeys.SEQ to 2L, AppStateKeys.END_SEQ to 3L), span.attributes)
    }

    @Test
    fun `a span that no change touched has only its start number`() {
        val atStart = twoStates()

        val unchanged = FakeAttributesMutator()
        association.writeSpanExportForm(unchanged, atStart, recorder.current())
        assertEquals(mapOf<String, Any>(AppStateKeys.SEQ to 2L), unchanged.attributes)

        val noEnd = FakeAttributesMutator()
        association.writeSpanExportForm(noEnd, atStart, null)
        assertEquals(mapOf<String, Any>(AppStateKeys.SEQ to 2L), noEnd.attributes)
    }

    @Test
    fun `a span that started before the first change has no start number`() {
        val declared = twoStates()

        val span = FakeAttributesMutator()
        association.writeSpanExportForm(span, StateSnapshot.EMPTY, declared)
        assertEquals(mapOf<String, Any>(AppStateKeys.END_SEQ to 2L), span.attributes)

        val neither = FakeAttributesMutator()
        association.writeSpanExportForm(neither, StateSnapshot.EMPTY, StateSnapshot.EMPTY)
        assertEquals(emptyMap<String, Any>(), neither.attributes)
    }

    @Test
    fun `verbose writes the values at the start and only the values that differ at the end`() {
        val atStart = twoStates()
        recorder.update(NETWORK, "cell", false, clock.now())
        val atEnd = recorder.current()

        val span = FakeAttributesMutator()
        verbose.writeSpanExportForm(span, atStart, atEnd)

        assertEquals(
            mapOf<String, Any>(
                AppStateKeys.SEQ to 2L,
                AppStateKeys.END_SEQ to 3L,
                "${AppStateKeys.VALUE_PREFIX}$NETWORK" to "wifi",
                "${AppStateKeys.VALUE_PREFIX}$SCREEN" to "Initializing",
                "${AppStateKeys.VALUE_TYPE_PREFIX}$SCREEN" to AppStateKeys.VALUE_TYPE_SYSTEM,
                "${AppStateKeys.END_VALUE_PREFIX}$NETWORK" to "cell",
            ),
            span.attributes,
        )
    }

    @Test
    fun `verbose with no end writes only the start values`() {
        val span = FakeAttributesMutator()
        verbose.writeSpanExportForm(span, twoStates(), null)

        assertEquals(
            mapOf<String, Any>(
                AppStateKeys.SEQ to 2L,
                "${AppStateKeys.VALUE_PREFIX}$NETWORK" to "wifi",
                "${AppStateKeys.VALUE_PREFIX}$SCREEN" to "Initializing",
                "${AppStateKeys.VALUE_TYPE_PREFIX}$SCREEN" to AppStateKeys.VALUE_TYPE_SYSTEM,
            ),
            span.attributes,
        )
    }

    @Test
    fun `verbose gives the end value of a state that exists only in the end snapshot`() {
        recorder.declare(StateDefinition(NETWORK), "wifi", false, clock.now())
        val atStart = recorder.current()
        recorder.declare(StateDefinition(SCREEN), "Initializing", true, clock.now())
        val atEnd = recorder.current()

        val span = FakeAttributesMutator()
        verbose.writeSpanExportForm(span, atStart, atEnd)

        assertEquals(
            mapOf<String, Any>(
                AppStateKeys.SEQ to 1L,
                AppStateKeys.END_SEQ to 2L,
                "${AppStateKeys.VALUE_PREFIX}$NETWORK" to "wifi",
                "${AppStateKeys.END_VALUE_PREFIX}$SCREEN" to "Initializing",
                "${AppStateKeys.END_VALUE_TYPE_PREFIX}$SCREEN" to AppStateKeys.VALUE_TYPE_SYSTEM,
            ),
            span.attributes,
        )
    }

    @Test
    fun `verbose with no state at the start gives every state's end value`() {
        val atEnd = twoStates()

        val span = FakeAttributesMutator()
        verbose.writeSpanExportForm(span, StateSnapshot.EMPTY, atEnd)

        assertEquals(
            mapOf<String, Any>(
                AppStateKeys.END_SEQ to 2L,
                "${AppStateKeys.END_VALUE_PREFIX}$NETWORK" to "wifi",
                "${AppStateKeys.END_VALUE_PREFIX}$SCREEN" to "Initializing",
                "${AppStateKeys.END_VALUE_TYPE_PREFIX}$SCREEN" to AppStateKeys.VALUE_TYPE_SYSTEM,
            ),
            span.attributes,
        )
    }

    @Test
    fun `verbose notices a value that stays the same text and becomes generated`() {
        val atStart = twoStates()
        recorder.update(NETWORK, "wifi", true, clock.now())
        recorder.update(SCREEN, "Done", true, clock.now())
        val atEnd = recorder.current()

        val span = FakeAttributesMutator()
        verbose.writeSpanExportForm(span, atStart, atEnd)

        assertEquals(
            mapOf<String, Any>(
                AppStateKeys.SEQ to 2L,
                AppStateKeys.END_SEQ to 4L,
                "${AppStateKeys.VALUE_PREFIX}$NETWORK" to "wifi",
                "${AppStateKeys.VALUE_PREFIX}$SCREEN" to "Initializing",
                "${AppStateKeys.VALUE_TYPE_PREFIX}$SCREEN" to AppStateKeys.VALUE_TYPE_SYSTEM,
                "${AppStateKeys.END_VALUE_PREFIX}$NETWORK" to "wifi",
                "${AppStateKeys.END_VALUE_TYPE_PREFIX}$NETWORK" to AppStateKeys.VALUE_TYPE_SYSTEM,
                "${AppStateKeys.END_VALUE_PREFIX}$SCREEN" to "Done",
                "${AppStateKeys.END_VALUE_TYPE_PREFIX}$SCREEN" to AppStateKeys.VALUE_TYPE_SYSTEM,
            ),
            span.attributes,
        )
    }

    @Test
    fun `the snapshot at a time is the recorder's`() {
        val declared = twoStates()
        assertEquals(declared.seq, association.snapshotAt(clock.now())?.seq)
    }

    private fun twoStates(): StateSnapshot {
        recorder.declare(StateDefinition(NETWORK), "wifi", false, clock.now())
        recorder.declare(StateDefinition(SCREEN), "Initializing", true, clock.now())
        return recorder.current()
    }

    private fun log(type: String?, eventName: String? = null): FakeReadWriteLogRecord {
        val record = FakeReadWriteLogRecord(eventName = eventName)
        if (type != null) {
            record.setStringAttribute(EmbType.State.key, type)
        }
        return record
    }

    private companion object {
        const val START_MS = 10_000L
        const val NETWORK = "network"
        const val SCREEN = "screen-automatic"
    }
}
