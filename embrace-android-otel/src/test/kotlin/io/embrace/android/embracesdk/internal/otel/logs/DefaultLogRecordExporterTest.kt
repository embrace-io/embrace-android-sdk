package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.internal.otel.export.ExternalExportDispatcher
import io.embrace.android.embracesdk.internal.otel.export.immediateExportDispatcher
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.data.LogRecordData
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

internal class DefaultLogRecordExporterTest {

    private fun exporter(
        logSink: LogSink,
        externalExporters: List<LogRecordExporter> = emptyList(),
        dispatcher: ExternalExportDispatcher = immediateExportDispatcher(),
    ) = DefaultLogRecordExporter(
        logSink = logSink,
        externalExporters = externalExporters,
        exportCheck = { true },
        externalExportDispatcher = dispatcher,
    )

    @Test
    fun `export() should store logs in LogSink`() {
        val logSink: LogSink = LogSinkImpl()
        val data = FakeReadWriteLogRecord()

        runBlocking { exporter(logSink).export(listOf(FakeReadWriteLogRecord())) }

        assertFalse(logSink.logsForNextBatch().isEmpty())
        assertEquals(data.toEmbracePayload(), logSink.logsForNextBatch()[0])
    }

    @Test
    fun `logs reach the sink before exportInline() returns`() {
        val logSink: LogSink = LogSinkImpl()
        val exporter = exporter(logSink, listOf(FakeLogRecordExporter()), ExternalExportDispatcher())

        exporter.exportInline(listOf(FakeReadWriteLogRecord()))

        assertEquals(1, logSink.logsForNextBatch().size)
    }

    @Test
    fun `external export runs off the calling thread and is awaited by forceFlush()`() {
        val exportThreads = mutableListOf<String>()
        val threadRecordingExporter = object : LogRecordExporter {
            override suspend fun export(telemetry: List<LogRecordData>): OperationResultCode {
                exportThreads += Thread.currentThread().name
                return OperationResultCode.Success
            }

            override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
            override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
        }
        val exporter = exporter(LogSinkImpl(), listOf(threadRecordingExporter), ExternalExportDispatcher())

        exporter.exportInline(listOf(FakeReadWriteLogRecord()))
        runBlocking { exporter.forceFlush() }

        assertTrue(exportThreads.single().startsWith("emb-otel-export"))
    }

    @Test
    fun `external exporters receive the record as it was when exported`() {
        val pendingExports = ArrayDeque<Runnable>()
        val dispatcher = ExternalExportDispatcher { Executor(pendingExports::add).asCoroutineDispatcher() }
        val externalExporter = FakeLogRecordExporter()
        val log = FakeReadWriteLogRecord(body = "original")

        exporter(LogSinkImpl(), listOf(externalExporter), dispatcher).exportInline(listOf(log))
        log.body = "mutated"
        log.setStringAttribute("late", "value")
        while (pendingExports.isNotEmpty()) {
            pendingExports.removeFirst().run()
        }

        val exported = externalExporter.exportedLogs.single()
        assertEquals("original", exported.body)
        assertFalse(exported.attributes.containsKey("late"))
    }

    @Test
    fun `a throwing external exporter neither stops the others nor fails the internal store`() {
        val logSink: LogSink = LogSinkImpl()
        val throwingExporter = object : LogRecordExporter {
            override suspend fun export(telemetry: List<LogRecordData>): OperationResultCode =
                throw RuntimeException("boom")

            override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
            override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
        }
        val workingExporter = FakeLogRecordExporter()

        val result = exporter(logSink, listOf(throwingExporter, workingExporter))
            .exportInline(listOf(FakeReadWriteLogRecord()))

        assertEquals(OperationResultCode.Success, result)
        assertEquals(1, logSink.logsForNextBatch().size)
        assertEquals(1, workingExporter.exportedLogs.size)
    }

    @Test
    fun `nothing is exported when the export check fails`() {
        val logSink: LogSink = LogSinkImpl()
        val externalExporter = FakeLogRecordExporter()
        val exporter = DefaultLogRecordExporter(
            logSink = logSink,
            externalExporters = listOf(externalExporter),
            exportCheck = { false },
            externalExportDispatcher = immediateExportDispatcher(),
        )

        assertEquals(OperationResultCode.Success, exporter.exportInline(listOf(FakeReadWriteLogRecord())))

        assertTrue(logSink.logsForNextBatch().isEmpty())
        assertTrue(externalExporter.exportedLogs.isEmpty())
    }
}
