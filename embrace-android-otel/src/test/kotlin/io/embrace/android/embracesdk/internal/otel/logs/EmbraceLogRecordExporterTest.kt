package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fixtures.testLog
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

internal class EmbraceLogRecordExporterTest {

    @Test
    fun `export() should store logs in LogSink`() {
        val logSink: LogSink = LogSinkImpl()
        val embraceLogRecordExporter =
            EmbraceLogRecordExporter(logSink, LogRecordExporter.composite(emptyList())) { true }
        val logRecordData = FakeLogRecordData()

        embraceLogRecordExporter.export(listOf(logRecordData))

        assertFalse(logSink.logsForNextBatch().isEmpty())
        assertEquals(logRecordData.toEmbracePayload(), logSink.logsForNextBatch()[0])
    }

    @Test
    fun `private logs should be filtered out from external exporters`() {
        val logSink: LogSink = LogSinkImpl()
        val externalExporter = FakeLogRecordExporter()
        val embraceLogRecordExporter =
            EmbraceLogRecordExporter(logSink, LogRecordExporter.composite(externalExporter)) { true }
        val logRecordData = FakeLogRecordData()
        val privateLogRecordData = FakeLogRecordData(
            log = testLog.copy(
                attributes = logRecordData.log.attributes?.plus(
                    Attribute(
                        PrivateSpan.key.name,
                        PrivateSpan.value
                    )
                )
            )
        )

        embraceLogRecordExporter.export(listOf(logRecordData, privateLogRecordData))

        assertEquals(2, logSink.logsForNextBatch().size)
        assertEquals(logRecordData.toEmbracePayload(), logSink.logsForNextBatch()[0])
        assertEquals(privateLogRecordData.toEmbracePayload(), logSink.logsForNextBatch()[1])

        assertEquals(1, externalExporter.exportedLogs?.size)
        assertEquals(logRecordData, externalExporter.exportedLogs?.first())
    }
}
