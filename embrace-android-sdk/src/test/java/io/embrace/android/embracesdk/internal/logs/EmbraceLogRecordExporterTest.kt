package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.payload.toNewPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

internal class EmbraceLogRecordExporterTest {

    @Test
    fun `export() should store logs in LogSink`() {
        val logSink: LogSink = LogSinkImpl()
        val embraceLogRecordExporter = EmbraceLogRecordExporter(logSink)
        val logRecordData = FakeLogRecordData()

        embraceLogRecordExporter.export(listOf(logRecordData))

        assertFalse(logSink.completedLogs().isEmpty())
        assertEquals(logRecordData.toNewPayload(), logSink.completedLogs()[0])
    }
}
