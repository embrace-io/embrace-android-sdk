@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.fakes.fakeOpenTelemetry
import io.embrace.android.embracesdk.fixtures.fakeContextKey
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.state.markedAsStateReplay
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.embrace.android.embracesdk.semconv.EmbCommonAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.data.LogRecordData
import io.opentelemetry.kotlin.semconv.LogAttributes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    @Test
    fun `a record emitted with the replay context goes to the replay exporter once and nothing is done to it`() {
        val replayed = mutableListOf<LogRecordData>()
        val replayProcessor = replayProcessor(replayed::addAll)
        listOf(AppStateKeys.CHANGE_EVENT_NAME, AppStateKeys.SNAPSHOT_EVENT_NAME).forEach { eventName ->
            val log = FakeReadWriteLogRecord(eventName = eventName).apply {
                setStringAttribute(EmbType.State.key, EmbType.State.value)
            }

            replayProcessor.onEmit(log, replayContext())

            assertSame(log, replayed.last())
            assertEquals(mapOf<String, Any>(EmbType.State.key to EmbType.State.value), log.attributes)
        }
        assertEquals(2, replayed.size)
        assertTrue(logRecordExporter.exportedLogs.isEmpty())
    }

    @Test
    fun `a log that only has the name and type of a state change is an ordinary log when its context is not the replay one`() {
        val replayed = mutableListOf<LogRecordData>()
        val log = FakeReadWriteLogRecord(eventName = AppStateKeys.CHANGE_EVENT_NAME).apply {
            setStringAttribute(EmbType.State.key, EmbType.State.value)
        }

        replayProcessor(replayed::addAll).onEmit(log, NoopOpenTelemetry.context.current())

        assertNotNull(log.attributes[LogAttributes.LOG_RECORD_UID])
        assertEquals("foo", log.attributes[SESSION_ATTRIBUTE_NAME])
        assertSame(log, logRecordExporter.exportedLogs.single())
        assertTrue(replayed.isEmpty())
    }

    @Test
    fun `a record with the replay context and any other event name is an ordinary log`() {
        // a log that an app's own log record processor emits with the context it was handed has the mark and not the name
        val replayed = mutableListOf<LogRecordData>()
        listOf("another.event", null).forEach { eventName ->
            val log = FakeReadWriteLogRecord(eventName = eventName).apply {
                setStringAttribute(EmbType.State.key, EmbType.State.value)
            }

            replayProcessor(replayed::addAll).onEmit(log, replayContext())

            assertNotNull(log.attributes[LogAttributes.LOG_RECORD_UID])
            assertSame(log, logRecordExporter.exportedLogs.last())
        }
        assertTrue(replayed.isEmpty())
        assertEquals(2, logRecordExporter.exportedLogs.size)
    }

    @Test
    fun `a context carrying another key is not the replay context`() {
        val replayed = mutableListOf<LogRecordData>()
        val log = FakeReadWriteLogRecord()

        replayProcessor(replayed::addAll).onEmit(log, otelRoot().set(fakeContextKey, "value"))

        assertNotNull(log.attributes[LogAttributes.LOG_RECORD_UID])
        assertSame(log, logRecordExporter.exportedLogs.single())
        assertTrue(replayed.isEmpty())
    }

    @Test
    fun `a record with the replay context is an ordinary log when nothing is replayed`() {
        val log = FakeReadWriteLogRecord(eventName = AppStateKeys.CHANGE_EVENT_NAME).apply {
            setStringAttribute(EmbType.State.key, EmbType.State.value)
        }

        processor.onEmit(log, replayContext())

        assertNotNull(log.attributes[LogAttributes.LOG_RECORD_UID])
        assertEquals("my-session-part", log.attributes[EmbSessionAttributes.EMB_SESSION_PART_ID])
        assertSame(log, logRecordExporter.exportedLogs.single())
    }

    private fun otelRoot(): Context = fakeOpenTelemetry(OtelSdkMode.REGULAR).context.root()

    private fun replayContext(): Context = otelRoot().markedAsStateReplay()

    private fun replayProcessor(stateReplayExporter: (List<LogRecordData>) -> Unit) = EmbraceLogRecordProcessor(
        uuidSource = TestUuidSource(),
        metadataProvider = { METADATA },
        logRecordExporter = logRecordExporter,
        stateReplayExporter = stateReplayExporter,
    )

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
