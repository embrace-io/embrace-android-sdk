package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class EmbraceLogRecordProcessorTest {

    @Test
    fun `onEmit() should call export() on the LogRecordExporter`() {
        val logRecordExporter = FakeLogRecordExporter()
        val logRecordProcessor = EmbraceLogRecordProcessor(logRecordExporter)
        val readWriteLogRecord = FakeReadWriteLogRecord()

        logRecordProcessor.onEmit(mockk(), readWriteLogRecord)

        val logRecordData = logRecordExporter.exportedLogs?.first()
        assertEquals(readWriteLogRecord.toLogRecordData(), logRecordData)
        assertNotNull(readWriteLogRecord.attributes["emb.event_id"])
    }
}
