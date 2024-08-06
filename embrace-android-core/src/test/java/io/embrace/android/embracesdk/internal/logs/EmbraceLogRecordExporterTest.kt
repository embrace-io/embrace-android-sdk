package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fixtures.testLog
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import org.junit.Assert
import org.junit.Test

internal class EmbraceLogRecordExporterTest {

    @Test
    fun `export() should store logs in LogSink`() {
        val logSink: LogSink = LogSinkImpl()
        val embraceLogRecordExporter =
            EmbraceLogRecordExporter(logSink, LogRecordExporter.composite(emptyList()))
        val logRecordData = FakeLogRecordData()

        embraceLogRecordExporter.export(listOf(logRecordData))

        Assert.assertFalse(logSink.completedLogs().isEmpty())
        Assert.assertEquals(logRecordData.toNewPayload(), logSink.completedLogs()[0])
    }

    @Test
    fun `private logs should be filtered out from external exporters`() {
        val logSink: LogSink = LogSinkImpl()
        val externalExporter = FakeLogRecordExporter()
        val embraceLogRecordExporter =
            EmbraceLogRecordExporter(logSink, LogRecordExporter.composite(externalExporter))
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

        Assert.assertEquals(2, logSink.completedLogs().size)
        Assert.assertEquals(logRecordData.toNewPayload(), logSink.completedLogs()[0])
        Assert.assertEquals(privateLogRecordData.toNewPayload(), logSink.completedLogs()[1])

        Assert.assertEquals(1, externalExporter.exportedLogs?.size)
        Assert.assertEquals(logRecordData, externalExporter.exportedLogs?.first())
    }
}
