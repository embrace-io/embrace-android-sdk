package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.fakes.FakeAttributeContainer
import io.embrace.android.embracesdk.fakes.FakeOtelJavaLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.logging.export.toOtelKotlinLogRecordExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class DefaultLogRecordExporterTest {

    @Test
    fun `export() should store logs in LogSink`() {
        val logSink: LogSink = LogSinkImpl()
        val exporter = DefaultLogRecordExporter(logSink, null) { true }
        val data = FakeReadWriteLogRecord()

        exporter.export(listOf(FakeReadWriteLogRecord()))

        assertFalse(logSink.logsForNextBatch().isEmpty())
        assertEquals(data.toEmbracePayload(), logSink.logsForNextBatch()[0])
    }

    @Test
    fun `private logs should be filtered out from external exporters`() {
        val logSink: LogSink = LogSinkImpl()
        val externalExporter = FakeOtelJavaLogRecordExporter()
        val exporter = DefaultLogRecordExporter(logSink, externalExporter.toOtelKotlinLogRecordExporter()) { true }
        val logKey = "test_log"
        val data = FakeReadWriteLogRecord(body = logKey)

        val privateData = FakeReadWriteLogRecord(
            attributeContainer = FakeAttributeContainer().apply {
                setStringAttribute(PrivateSpan.key.name, PrivateSpan.value)
            }
        )

        exporter.export(listOf(data, privateData))

        assertEquals(2, logSink.logsForNextBatch().size)
        assertEquals(data.toEmbracePayload(), logSink.logsForNextBatch()[0])
        assertEquals(privateData.toEmbracePayload(), logSink.logsForNextBatch()[1])

        assertEquals(1, externalExporter.exportedLogs?.size)
        assertEquals(data.body, externalExporter.exportedLogs?.first()?.bodyValue?.asString())
    }
}
