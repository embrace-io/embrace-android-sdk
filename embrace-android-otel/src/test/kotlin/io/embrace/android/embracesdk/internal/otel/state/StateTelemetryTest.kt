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
    fun `the kind of a log decides how it is stamped and where it goes`() {
        assertEquals(LogKind.DEAD_PROCESS, association.kindOf(log(EmbType.System.NativeCrash.value)))
        assertEquals(LogKind.DEAD_PROCESS, association.kindOf(log(EmbType.System.Exit.value)))
        assertEquals(LogKind.REPLAY, association.kindOf(log(EmbType.State.value, AppStateKeys.CHANGE_EVENT_NAME)))
        assertEquals(LogKind.REPLAY, association.kindOf(log(EmbType.State.value, AppStateKeys.SNAPSHOT_EVENT_NAME)))
        assertEquals(LogKind.LIVE, association.kindOf(log(EmbType.State.value, "another.event")))
        assertEquals(LogKind.LIVE, association.kindOf(log(EmbType.State.value, null)))
        assertEquals(LogKind.LIVE, association.kindOf(log(EmbType.System.Log.value)))
        assertEquals(LogKind.LIVE, association.kindOf(log(null, AppStateKeys.CHANGE_EVENT_NAME)))
        assertEquals(LogKind.LIVE, association.kindOf(log(null)))
    }

    @Test
    fun `whether the app receives logs is what the supplier says, and true by default`() {
        assertEquals(true, association.appReceivesLogs())
        val mode = StateExportMode.ASSOCIATION
        val noLogs = StateTelemetry(recorder, exportMode = { mode }, receivesLogs = { false })
        assertEquals(false, noLogs.appReceivesLogs())
    }

    @Test
    fun `the kind of a log is the same read from the event name and attributes`() {
        assertEquals(LogKind.REPLAY, association.kindOf(AppStateKeys.CHANGE_EVENT_NAME, mapOf(EmbType.State.key to EmbType.State.value)))
        assertEquals(LogKind.REPLAY, association.kindOf(AppStateKeys.SNAPSHOT_EVENT_NAME, mapOf(EmbType.State.key to EmbType.State.value)))
        assertEquals(LogKind.LIVE, association.kindOf("another.event", mapOf(EmbType.State.key to EmbType.State.value)))
        assertEquals(LogKind.LIVE, association.kindOf(AppStateKeys.CHANGE_EVENT_NAME, emptyMap()))
        assertEquals(LogKind.DEAD_PROCESS, association.kindOf(null, mapOf(EmbType.State.key to EmbType.System.NativeCrash.value)))
        assertEquals(LogKind.DEAD_PROCESS, association.kindOf(null, mapOf(EmbType.State.key to EmbType.System.Exit.value)))
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
        association.writeDeadProcessSeq(record)
        assertEquals(12L, record.attributes[AppStateKeys.SEQ])
    }

    @Test
    fun `a number on a log of a dead process that is absent or not a number is left alone`() {
        val absent = FakeReadWriteLogRecord()
        association.writeDeadProcessSeq(absent)
        assertEquals(emptyMap<String, Any>(), absent.attributes)

        val notANumber = FakeReadWriteLogRecord()
        notANumber.setStringAttribute(AppStateKeys.SEQ, "twelve")
        association.writeDeadProcessSeq(notANumber)
        assertEquals("twelve", notANumber.attributes[AppStateKeys.SEQ])

        val alreadyLong = FakeReadWriteLogRecord()
        alreadyLong.setLongAttribute(AppStateKeys.SEQ, 5L)
        association.writeDeadProcessSeq(alreadyLong)
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
