package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

internal class EmbraceLogRecordExporterTest {

    @Test
    fun `export() should store logs in LogSink`() {
        val logSink: LogSink = mockk(relaxed = true)
        val embraceLogRecordExporter = EmbraceLogRecordExporter(logSink)
        val logRecordData = FakeLogRecordData()

        embraceLogRecordExporter.export(listOf(logRecordData))

        verify {
            logSink.storeLogs(listOf(logRecordData))
        }
    }
}
