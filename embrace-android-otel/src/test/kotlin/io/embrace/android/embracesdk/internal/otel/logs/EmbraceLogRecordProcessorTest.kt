@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.state.CountingStateStamp
import io.embrace.android.embracesdk.internal.otel.state.FakeStateStamp
import io.embrace.android.embracesdk.internal.otel.state.StateStamp
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.semconv.EmbCommonAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.semconv.LogAttributes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

internal class EmbraceLogRecordProcessorTest {

    private lateinit var logRecordExporter: FakeLogRecordExporter
    private lateinit var processor: EmbraceLogRecordProcessor

    @Before
    fun setup() {
        logRecordExporter = FakeLogRecordExporter()
        processor = EmbraceLogRecordProcessor(
            uuidSource = TestUuidSource(),
            metadataProvider = { METADATA },
            logRecordExporter = logRecordExporter,
        )
    }

    private fun stateProcessor(stamp: StateStamp, externalReceivers: Boolean) = EmbraceLogRecordProcessor(
        uuidSource = TestUuidSource(),
        metadataProvider = { emptyMap() },
        logRecordExporter = logRecordExporter,
        stateProvider = { stamp },
        stampsExternalState = externalReceivers,
    )

    @Test
    fun `onEmit() should call export() on the LogRecordExporter`() {
        val readWriteLogRecord = FakeReadWriteLogRecord()
        processor.onEmit(readWriteLogRecord)

        val logRecordData = logRecordExporter.exportedLogs.single()
        assertEquals(readWriteLogRecord, logRecordData)
    }

    @Test
    fun `expected attributes added to every non-private log record`() {
        val log = FakeReadWriteLogRecord().apply {
            setStringAttribute("custom", "attr")
        }
        processor.onEmit(log)

        with(log.attributes) {
            assertEquals("attr", get("custom"))
            assertNotNull(get(LogAttributes.LOG_RECORD_UID))
            assertEquals("foo", get(SESSION_ATTRIBUTE_NAME))
            assertEquals(EXPERIMENT_RECORDS, get(EmbCommonAttributes.EMB_EXPERIMENTS))
        }
    }

    @Test
    fun `a whole number in the metadata is stamped as one, and anything else as text`() {
        val log = FakeReadWriteLogRecord()
        EmbraceLogRecordProcessor(
            uuidSource = TestUuidSource(),
            metadataProvider = { mapOf("number" to 42L, "text" to "42") },
            logRecordExporter = logRecordExporter,
        ).onEmit(log)

        assertEquals(42L, log.attributes["number"])
        assertEquals("42", log.attributes["text"])
    }

    @Test
    fun `experiment records are not stamped on private logs`() {
        val log = FakeReadWriteLogRecord().apply {
            setStringAttribute(PrivateSpan.key, PrivateSpan.value)
        }
        processor.onEmit(log)

        assertFalse(log.attributes.containsKey(EmbCommonAttributes.EMB_EXPERIMENTS))
    }

    @Test
    fun `an experiment records attribute value on logs that are not native crashes is erased if set`() {
        val noExperimentsProcessor = EmbraceLogRecordProcessor(
            uuidSource = TestUuidSource(),
            metadataProvider = { METADATA - EmbCommonAttributes.EMB_EXPERIMENTS },
            logRecordExporter = logRecordExporter,
        )
        val log = FakeReadWriteLogRecord().apply {
            setStringAttribute(EmbCommonAttributes.EMB_EXPERIMENTS, "spoofed")
        }
        noExperimentsProcessor.onEmit(log)

        assertEquals("", log.attributes[EmbCommonAttributes.EMB_EXPERIMENTS])
    }

    @Test
    fun `a pre-set experiment records value is preserved native crash log`() {
        val log = FakeReadWriteLogRecord().apply {
            setStringAttribute(EmbType.System.NativeCrash.key, EmbType.System.NativeCrash.value)
            setStringAttribute(EmbCommonAttributes.EMB_EXPERIMENTS, DEAD_PROCESS_EXPERIMENT_RECORDS)
        }
        processor.onEmit(log)

        assertEquals(DEAD_PROCESS_EXPERIMENT_RECORDS, log.attributes[EmbCommonAttributes.EMB_EXPERIMENTS])
    }

    @Test
    fun `existing log id not overridden`() {
        val log = FakeReadWriteLogRecord().apply {
            setStringAttribute(LogAttributes.LOG_RECORD_UID, "foo")
        }
        processor.onEmit(log)

        assertEquals("foo", log.attributes[LogAttributes.LOG_RECORD_UID])
    }

    @Test
    fun `a session part set by the instrumentation is not replaced by the metadata`() {
        val log = FakeReadWriteLogRecord().apply {
            setStringAttribute(EmbSessionAttributes.EMB_SESSION_PART_ID, "crashed-session-part")
        }
        processor.onEmit(log)

        with(log.attributes) {
            assertEquals("crashed-session-part", get(EmbSessionAttributes.EMB_SESSION_PART_ID))
            // the rest of the metadata is still added
            assertEquals("foo", get(SESSION_ATTRIBUTE_NAME))
            assertNotNull(get(LogAttributes.LOG_RECORD_UID))
        }
    }

    @Test
    fun `a blank session part set by the instrumentation is not replaced by the metadata`() {
        val log = FakeReadWriteLogRecord().apply {
            setStringAttribute(EmbSessionAttributes.EMB_SESSION_PART_ID, "")
        }
        processor.onEmit(log)

        with(log.attributes) {
            assertEquals("", get(EmbSessionAttributes.EMB_SESSION_PART_ID))
            assertEquals("foo", get(SESSION_ATTRIBUTE_NAME))
            assertNotNull(get(LogAttributes.LOG_RECORD_UID))
        }
    }

    @Test
    fun `metadata is added to a log record that sets none of it`() {
        val log = FakeReadWriteLogRecord()
        processor.onEmit(log)

        with(log.attributes) {
            assertEquals("foo", get(SESSION_ATTRIBUTE_NAME))
            assertEquals("my-session-part", get(EmbSessionAttributes.EMB_SESSION_PART_ID))
        }
    }

    @Test
    fun `no metadata is added to a native crash, as it describes a process that has died`() {
        val log = FakeReadWriteLogRecord().apply {
            setStringAttribute(EmbType.System.NativeCrash.key, EmbType.System.NativeCrash.value)
            setStringAttribute(EmbSessionAttributes.EMB_SESSION_PART_ID, "crashed-session-part")
        }
        processor.onEmit(log)

        with(log.attributes) {
            assertEquals("crashed-session-part", get(EmbSessionAttributes.EMB_SESSION_PART_ID))
            assertFalse(containsKey(SESSION_ATTRIBUTE_NAME))
            assertFalse(containsKey(EmbCommonAttributes.EMB_EXPERIMENTS))
            // the log ID is still stamped on it
            assertNotNull(get(LogAttributes.LOG_RECORD_UID))
        }
    }

    @Test
    fun `metadata is added to a log record of another type`() {
        val log = FakeReadWriteLogRecord().apply {
            setStringAttribute(EmbType.System.Exit.key, EmbType.System.Exit.value)
        }
        processor.onEmit(log)

        assertEquals("foo", log.attributes[SESSION_ATTRIBUTE_NAME])
    }

    @Test
    fun `state is stamped on Embrace's payload as the packed attribute, and on the record as one attribute for each state`() {
        val stamp = CountingStateStamp()
        val log = FakeReadWriteLogRecord()
        stateProcessor(stamp, externalReceivers = true).onEmit(log)

        // an app's processors and exporters are handed the record: the plain attributes, a whole number, and no packed one
        assertEquals("wifi", log.attributes["emb.states.value.network"])
        assertEquals("Backgrounded", log.attributes["emb.states.value.screen"])
        assertEquals("system", log.attributes["emb.states.value_type.screen"])
        assertEquals(2L, log.attributes["emb.states.seq"])
        assertFalse(log.attributes.containsKey("emb.states.packed"))

        // Embrace's payload is handed the packed attribute and the sequence, beside the record's own and not on it
        assertEquals(
            listOf(Attribute("emb.states.packed", "network=wifi;screen~Backgrounded"), Attribute("emb.states.seq", "2")),
            logRecordExporter.payloadAttributes.getValue(log),
        )
        assertEquals(log, logRecordExporter.exportedLogs.single())

        // and the record had none of the plain attributes when Embrace's payload was stored
        assertEquals(
            emptyList<String>(),
            logRecordExporter.attributesAtStore.getValue(log).keys.filter { it.startsWith("emb.states") },
        )
    }

    @Test
    fun `the plain stamp is still put on the record when the exporter does not call back after storing`() {
        logRecordExporter.callsAfterStore = false
        val log = FakeReadWriteLogRecord()
        stateProcessor(CountingStateStamp(), externalReceivers = true).onEmit(log)

        assertEquals("wifi", log.attributes["emb.states.value.network"])
        assertEquals(2L, log.attributes["emb.states.seq"])
    }

    @Test
    fun `with nothing of the app's to receive a log the plain attributes are never built`() {
        val stamp = CountingStateStamp()
        val log = FakeReadWriteLogRecord()
        stateProcessor(stamp, externalReceivers = false).onEmit(log)

        assertEquals(0, stamp.attributesBuilt)
        assertEquals(emptyList<String>(), log.attributes.keys.filter { it.startsWith("emb.states") })
        assertEquals(
            listOf("emb.states.packed", "emb.states.seq"),
            logRecordExporter.payloadAttributes.getValue(log).map { it.key },
        )
    }

    @Test
    fun `no state is stamped when none has a value, or on a native crash or an exit record, or on a log with the packed attribute`() {
        val empty = FakeReadWriteLogRecord()
        stateProcessor(FakeStateStamp(0), externalReceivers = true).onEmit(empty)
        assertEquals(emptyList<String>(), empty.attributes.keys.filter { it.startsWith("emb.states") })
        assertEquals(emptyMap<Any, Any>(), logRecordExporter.payloadAttributes)

        val crash = FakeReadWriteLogRecord().apply {
            setStringAttribute(EmbType.System.NativeCrash.key, EmbType.System.NativeCrash.value)
        }
        stateProcessor(CountingStateStamp(), externalReceivers = true).onEmit(crash)
        assertEquals(emptyList<String>(), crash.attributes.keys.filter { it.startsWith("emb.states") })
        assertEquals(emptyMap<Any, Any>(), logRecordExporter.payloadAttributes)

        // a record that names the session part it belongs to is about another process, as an exit record is
        val exit = FakeReadWriteLogRecord().apply {
            setStringAttribute(EmbType.System.Exit.key, EmbType.System.Exit.value)
            setStringAttribute(EmbSessionAttributes.EMB_SESSION_PART_ID, "")
        }
        stateProcessor(CountingStateStamp(), externalReceivers = true).onEmit(exit)
        assertEquals(emptyList<String>(), exit.attributes.keys.filter { it.startsWith("emb.states") })
        assertEquals(emptyMap<Any, Any>(), logRecordExporter.payloadAttributes)
        assertEquals(exit, logRecordExporter.exportedLogs.last())

        val stamped = FakeReadWriteLogRecord().apply { setStringAttribute("emb.states.packed", "set=earlier") }
        stateProcessor(CountingStateStamp(), externalReceivers = true).onEmit(stamped)
        assertEquals("set=earlier", stamped.attributes["emb.states.packed"])
        assertEquals(emptyMap<Any, Any>(), logRecordExporter.payloadAttributes)
    }

    @Test
    fun `a packed stamp the metadata supplies is left off a log about another process, and kept on an ordinary one`() {
        val stamped = EmbraceLogRecordProcessor(
            uuidSource = TestUuidSource(),
            metadataProvider = {
                mapOf("emb.states.packed" to "network=wifi", "emb.states.seq" to "2", SESSION_ATTRIBUTE_NAME to "foo")
            },
            logRecordExporter = logRecordExporter,
        )
        val ordinary = FakeReadWriteLogRecord()
        val exit = FakeReadWriteLogRecord().apply {
            setStringAttribute(EmbType.System.Exit.key, EmbType.System.Exit.value)
            setStringAttribute(EmbSessionAttributes.EMB_SESSION_PART_ID, "")
        }
        val crash = FakeReadWriteLogRecord().apply {
            setStringAttribute(EmbType.System.NativeCrash.key, EmbType.System.NativeCrash.value)
        }
        listOf(ordinary, exit, crash).forEach { stamped.onEmit(it) }

        assertEquals("network=wifi", ordinary.attributes["emb.states.packed"])
        assertEquals("2", ordinary.attributes["emb.states.seq"])
        listOf(exit, crash).forEach { log ->
            assertFalse(log.attributes.containsKey("emb.states.packed"))
            assertFalse(log.attributes.containsKey("emb.states.seq"))
        }
        // the rest of the metadata is as it was: stamped on the exit record, and not on the native crash
        assertEquals("foo", exit.attributes[SESSION_ATTRIBUTE_NAME])
        assertFalse(crash.attributes.containsKey(SESSION_ATTRIBUTE_NAME))
    }

    @Test
    fun `onEmit() exports before returning, so a crash log reaches the sink before the process dies`() {
        val log = FakeReadWriteLogRecord()

        processor.onEmit(log)

        // no drain and no waiting: crash teardown persists the payload on the crashing thread
        // straight after the log is emitted, so anything not yet in the sink is lost
        assertEquals(log, logRecordExporter.exportedLogs.single())
    }

    @Test
    fun `forceFlush() flushes the exporter, which is what drains export dispatched off-thread`() {
        val result = runBlocking { processor.forceFlush() }

        assertEquals(OperationResultCode.Success, result)
        assertEquals(1, logRecordExporter.forceFlushCount)
    }

    private fun EmbraceLogRecordProcessor.onEmit(log: FakeReadWriteLogRecord) =
        onEmit(log, NoopOpenTelemetry.context.current())

    private companion object {
        const val SESSION_ATTRIBUTE_NAME = "session-attr"
        const val EXPERIMENT_RECORDS = "e:checkout-flow:variant-a:1000;f:dark-mode::2000"
        const val DEAD_PROCESS_EXPERIMENT_RECORDS = "e:dead-exp:variant-z:500"
        val METADATA = mapOf(
            SESSION_ATTRIBUTE_NAME to "foo",
            EmbSessionAttributes.EMB_SESSION_PART_ID to "my-session-part",
            EmbCommonAttributes.EMB_EXPERIMENTS to EXPERIMENT_RECORDS,
        )
    }
}
