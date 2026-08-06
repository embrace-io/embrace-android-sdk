package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeReadWriteSpan
import io.embrace.android.embracesdk.fakes.FakeSpan
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.export.ExternalExportDispatcher
import io.embrace.android.embracesdk.internal.otel.export.immediateExportDispatcher
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DefaultSpanExporterTest {

    private fun span(name: String, vararg attrs: Pair<String, String>): SpanData =
        FakeReadWriteSpan(FakeSpan(name = name).apply { this.attrs.putAll(attrs) })

    private fun exporter(
        spanRepository: SpanRepository,
        externalExporters: List<SpanExporter> = emptyList(),
        exportCheck: () -> Boolean = { true },
        dispatcher: ExternalExportDispatcher = immediateExportDispatcher(),
    ) = DefaultSpanExporter(
        spanRepository = spanRepository,
        externalExporters = externalExporters,
        exportCheck = exportCheck,
        externalExportDispatcher = dispatcher,
    )

    private fun threadRecordingExporter(threads: MutableList<String>) = object : SpanExporter {
        override suspend fun export(telemetry: List<SpanData>): OperationResultCode {
            threads += Thread.currentThread().name
            return OperationResultCode.Success
        }

        override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
        override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
    }

    private fun throwingExporter() = object : SpanExporter {
        override suspend fun export(telemetry: List<SpanData>): OperationResultCode =
            throw RuntimeException("boom")

        override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
        override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
    }

    @Test
    fun `export() should store spans in SpanRepository`() {
        val spanRepository = SpanRepository()

        runBlocking { exporter(spanRepository).export(listOf(span("public-span"))) }

        assertFalse(spanRepository.completedOtelSpans().isEmpty())
    }

    @Test
    fun `private spans should be filtered out from external exporters but still stored internally`() {
        val spanRepository = SpanRepository()
        val externalExporter = FakeSpanExporter()

        val publicSpan = span("public-span")
        val privateSpan = span("private-span", PrivateSpan.key to PrivateSpan.value)

        exporter(spanRepository, listOf(externalExporter)).exportInline(listOf(publicSpan, privateSpan))

        assertEquals(2, spanRepository.completedOtelSpans().size)
        assertEquals(1, externalExporter.exportedSpans.size)
        assertEquals("public-span", externalExporter.exportedSpans.first().name)
    }

    @Test
    fun `spans reach the repository before exportInline() returns`() {
        val spanRepository = SpanRepository()
        // a real dispatcher, so only a genuinely inline store is visible without draining first
        val exporter = exporter(
            spanRepository = spanRepository,
            externalExporters = listOf(FakeSpanExporter()),
            dispatcher = ExternalExportDispatcher(),
        )

        exporter.exportInline(listOf(span("public-span")))

        assertEquals(1, spanRepository.completedOtelSpans().size)
    }

    @Test
    fun `external export runs off the calling thread and is awaited by forceFlush()`() {
        val exportThreads = mutableListOf<String>()
        val exporter = exporter(
            spanRepository = SpanRepository(),
            externalExporters = listOf(threadRecordingExporter(exportThreads)),
            dispatcher = ExternalExportDispatcher(),
        )

        exporter.exportInline(listOf(span("public-span")))
        runBlocking { exporter.forceFlush() }

        assertTrue(exportThreads.single().startsWith("emb-otel-export"))
    }

    @Test
    fun `a throwing external exporter neither stops the others nor fails the internal store`() {
        val spanRepository = SpanRepository()
        val workingExporter = FakeSpanExporter()

        val result = exporter(spanRepository, listOf(throwingExporter(), workingExporter))
            .exportInline(listOf(span("public-span")))

        assertEquals(OperationResultCode.Success, result)
        assertEquals(1, spanRepository.completedOtelSpans().size)
        assertEquals("public-span", workingExporter.exportedSpans.single().name)
    }

    @Test
    fun `nothing is exported when the export check fails`() {
        val spanRepository = SpanRepository()
        val externalExporter = FakeSpanExporter()
        val exporter = exporter(spanRepository, listOf(externalExporter), exportCheck = { false })

        assertEquals(OperationResultCode.Success, exporter.exportInline(listOf(span("public-span"))))

        assertTrue(spanRepository.completedOtelSpans().isEmpty())
        assertTrue(externalExporter.exportedSpans.isEmpty())
    }
}
