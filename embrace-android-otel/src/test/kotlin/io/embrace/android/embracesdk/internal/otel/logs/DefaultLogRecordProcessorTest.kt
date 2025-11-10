package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.createNoopOpenTelemetry
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class DefaultLogRecordProcessorTest {

    @Test
    fun `onEmit() should call export() on the LogRecordExporter`() {
        val logRecordExporter = FakeLogRecordExporter()
        val logRecordProcessor = DefaultLogRecordProcessor(logRecordExporter)
        val readWriteLogRecord = FakeReadWriteLogRecord()
        logRecordProcessor.onEmit(readWriteLogRecord, createNoopOpenTelemetry().contextFactory.implicitContext())

        val logRecordData = logRecordExporter.exportedLogs.first()
        assertEquals(readWriteLogRecord, logRecordData)
    }
}
