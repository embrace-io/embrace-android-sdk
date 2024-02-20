package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.mockk.mockk
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EmbraceLogRecordProcessorTest {

    @Test
    fun `onEmit() should call export() on the LogRecordExporter`() {
        val logRecordExporter = FakeLogRecordExporter()
        val logRecordProcessor = EmbraceLogRecordProcessor(logRecordExporter)
        val readWriteLogRecord: ReadWriteLogRecord = FakeReadWriteLogRecord()

        logRecordProcessor.onEmit(mockk(), readWriteLogRecord)

        assertEquals(readWriteLogRecord.toLogRecordData(), logRecordExporter.exportedLogs?.first())
    }
}
