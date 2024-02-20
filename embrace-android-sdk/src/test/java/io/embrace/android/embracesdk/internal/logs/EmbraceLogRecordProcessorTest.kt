package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import org.junit.Test

internal class EmbraceLogRecordProcessorTest {

    @Test
    fun `onEmit() should call export() on the LogRecordExporter`() {
        val logRecordExporter = mockk<LogRecordExporter>(relaxed = true)
        val logRecordProcessor = EmbraceLogRecordProcessor(logRecordExporter)
        val readWriteLogRecord: ReadWriteLogRecord = FakeReadWriteLogRecord()

        logRecordProcessor.onEmit(mockk(), readWriteLogRecord)

        verify {
            logRecordExporter.export(mutableListOf(readWriteLogRecord.toLogRecordData()))
        }
    }
}
