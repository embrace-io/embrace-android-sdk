package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.semconv.LogAttributes
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
    fun `metadata not added to a log record that declares its own session part`() {
        val log = FakeReadWriteLogRecord().apply {
            setStringAttribute(EmbSessionAttributes.EMB_SESSION_PART_ID, "crashed-session-part")
        }
        processor.onEmit(log)

        with(log.attributes) {
            assertEquals("crashed-session-part", get(EmbSessionAttributes.EMB_SESSION_PART_ID))
            assertFalse(containsKey(SESSION_ATTRIBUTE_NAME))
            assertNotNull(get(LogAttributes.LOG_RECORD_UID))
        }
    }

    @Test
    fun `a blank session part is ownership, so it is not replaced by the metadata`() {
        val log = FakeReadWriteLogRecord().apply {
            setStringAttribute(EmbSessionAttributes.EMB_SESSION_PART_ID, "")
        }
        processor.onEmit(log)

        with(log.attributes) {
            assertEquals("", get(EmbSessionAttributes.EMB_SESSION_PART_ID))
            assertFalse(containsKey(SESSION_ATTRIBUTE_NAME))
            assertNotNull(get(LogAttributes.LOG_RECORD_UID))
        }
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
