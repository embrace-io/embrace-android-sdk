package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
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

    @Test
    fun `onEmit() should call export() on the LogRecordExporter`() {
        val readWriteLogRecord = FakeReadWriteLogRecord()
        processor.onEmit(readWriteLogRecord)

        val logRecordData = logRecordExporter.exportedLogs.single()
        assertEquals(readWriteLogRecord, logRecordData)
    }

    @Test
    fun `expected attributes added to every log record`() {
        val log = FakeReadWriteLogRecord().apply {
            setStringAttribute("custom", "attr")
        }
        processor.onEmit(log)

        with(log.attributes) {
            assertEquals("attr", get("custom"))
            assertNotNull(get(LogAttributes.LOG_RECORD_UID))
            assertEquals("foo", get(SESSION_ATTRIBUTE_NAME))
        }
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

    private fun EmbraceLogRecordProcessor.onEmit(log: FakeReadWriteLogRecord) =
        onEmit(log, NoopOpenTelemetry.context.implicit())

    private companion object {
        const val SESSION_ATTRIBUTE_NAME = "session-attr"
        val METADATA = mapOf(
            SESSION_ATTRIBUTE_NAME to "foo",
            EmbSessionAttributes.EMB_SESSION_PART_ID to "my-session-part",
        )
    }
}
