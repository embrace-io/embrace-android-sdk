package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.internal.otel.export.ExternalExportDispatcher
import io.embrace.android.embracesdk.internal.otel.export.immediateExportDispatcher
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.payload.Attribute
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
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val data = FakeReadWriteLogRecord()

        runBlocking { exporter(logSink).export(listOf(FakeReadWriteLogRecord())) }

        assertFalse(logSink.logsForNextBatch().isEmpty())
        assertEquals(data.toEmbracePayload(), logSink.logsForNextBatch()[0])
    }

    @Test
    fun `exportInline() with payload attributes stores them after the record, then calls back before exporters get it`() {
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val external = FakeLogRecordExporter()
        val log = FakeReadWriteLogRecord().apply { setStringAttribute("own", "1") }
        val extra = listOf(Attribute("emb.app.state.packed", "a=b"), Attribute("own", "duplicate"))

        exporter(logSink, listOf(external)).exportInline(log, extra) {
            // the sink has what was stored, and nothing is handed to the external exporters yet
            assertEquals(1, logSink.storedLogCount())
            assertTrue(external.exportedLogs.isEmpty())
            log.setStringAttribute("late", "2")
        }
        val attributesWhenHandedOver = external.exportedLogs.single().attributes

        // the record's own attributes first, then those it does not already have
        assertEquals(
            listOf("own" to "1", "emb.app.state.packed" to "a=b"),
            logSink.logsForNextBatch().single().attributes.orEmpty().map { it.key to it.data },
        )
        // what the callback put on the record reaches the app's exporters and not the sink
        assertEquals("2", attributesWhenHandedOver["late"])
    }

    @Test
    fun `exportInline() with payload attributes stores nothing and never calls back when export is disabled`() {
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val external = FakeLogRecordExporter()
        var called = false
        val exporter = DefaultLogRecordExporter(logSink, listOf(external), { false }, immediateExportDispatcher())

        val result = exporter.exportInline(FakeReadWriteLogRecord(), listOf(Attribute("emb.app.state.packed", "a=b"))) {
            called = true
        }

        assertEquals(OperationResultCode.Success, result)
        assertFalse(called)
        assertTrue(logSink.logsForNextBatch().isEmpty())
        assertTrue(external.exportedLogs.isEmpty())
    }

    @Test
    fun `exportExternally() hands records to the external exporters and never to the sink`() {
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val external = FakeLogRecordExporter()
        var exportEnabled = true
        val exporter =
            DefaultLogRecordExporter(logSink, listOf(external), { exportEnabled }, immediateExportDispatcher())

        exporter.exportExternally(listOf(FakeReadWriteLogRecord(eventName = "an-event")))
        exportEnabled = false
        exporter.exportExternally(listOf(FakeReadWriteLogRecord(eventName = "after-export-was-disabled")))

        assertEquals(listOf("an-event"), external.exportedLogs.map { it.eventName })
        assertTrue(logSink.logsForNextBatch().isEmpty())
    }

    @Test
    fun `logs reach the sink before exportInline() returns`() {
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val exporter =
            exporter(logSink, listOf(FakeLogRecordExporter()), ExternalExportDispatcher(FakeErrorHandler()))

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
        val exporter =
            exporter(LogSinkImpl(FakeErrorHandler()), listOf(threadRecordingExporter), ExternalExportDispatcher(FakeErrorHandler()))

        exporter.exportInline(listOf(FakeReadWriteLogRecord()))
        runBlocking { exporter.forceFlush() }

        assertTrue(exportThreads.single().startsWith("emb-otel-export"))
    }

    @Test
    fun `external exporters receive the record as it was when exported`() {
        val pendingExports = ArrayDeque<Runnable>()
        val dispatcher =
            ExternalExportDispatcher(FakeErrorHandler()) { Executor(pendingExports::add).asCoroutineDispatcher() }
        val externalExporter = FakeLogRecordExporter()
        val log = FakeReadWriteLogRecord(body = "original")

        exporter(LogSinkImpl(FakeErrorHandler()), listOf(externalExporter), dispatcher).exportInline(listOf(log))
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
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
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
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
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
