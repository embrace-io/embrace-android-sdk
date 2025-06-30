package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.fakes.FakeOtelJavaLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeReadWriteOtelJavaLogRecord
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EmbraceLogRecordProcessorTest {

    @Test
    fun `onEmit() should call export() on the LogRecordExporter`() {
        val logRecordExporter = FakeOtelJavaLogRecordExporter()
        val logRecordProcessor = EmbraceOtelJavaLogRecordProcessor(logRecordExporter)
        val readWriteLogRecord = FakeReadWriteOtelJavaLogRecord()

        logRecordProcessor.onEmit(mockk(), readWriteLogRecord)

        val logRecordData = logRecordExporter.exportedLogs?.first()
        assertEquals(readWriteLogRecord.toLogRecordData(), logRecordData)
    }
}
