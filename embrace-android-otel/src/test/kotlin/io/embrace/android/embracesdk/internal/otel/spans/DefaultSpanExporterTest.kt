package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeReadWriteSpan
import io.embrace.android.embracesdk.fakes.FakeSpan
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.internal.otel.export.ExternalExportDispatcher
import io.embrace.android.embracesdk.internal.otel.export.immediateExportDispatcher
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DefaultSpanExporterTest {

    private fun span(name: String): SpanData = FakeReadWriteSpan(FakeSpan(name = name))

    private fun exporter(
        spanRepository: SpanRepository,
        externalExporters: List<SpanExporter> = emptyList(),
        exportCheck: () -> Boolean = { true },
        dispatcher: ExternalExportDispatcher = immediateExportDispatcher(),
        stripStateValues: () -> Boolean = { false },
    ) = DefaultSpanExporter(
        spanRepository = spanRepository,
        externalExporters = externalExporters,
        exportCheck = exportCheck,
        externalExportDispatcher = dispatcher,
        stripStateValues = stripStateValues,
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
        val spanRepository = SpanRepository(FakeErrorHandler())

        runBlocking { exporter(spanRepository).export(listOf(span("public-span"))) }

        assertFalse(spanRepository.completedOtelSpans().isEmpty())
    }

    @Test
    fun `spans reach the repository before exportInline() returns`() {
        val spanRepository = SpanRepository(FakeErrorHandler())
        // a real dispatcher, so only a genuinely inline store is visible without draining first
        val exporter = exporter(
            spanRepository = spanRepository,
            externalExporters = listOf(FakeSpanExporter()),
            dispatcher = ExternalExportDispatcher(FakeErrorHandler()),
        )

        exporter.exportInline(listOf(span("public-span")))

        assertEquals(1, spanRepository.completedOtelSpans().size)
    }

    @Test
    fun `external export runs off the calling thread and is awaited by forceFlush()`() {
        val exportThreads = mutableListOf<String>()
        val exporter = exporter(
            spanRepository = SpanRepository(FakeErrorHandler()),
            externalExporters = listOf(threadRecordingExporter(exportThreads)),
            dispatcher = ExternalExportDispatcher(FakeErrorHandler()),
        )

        exporter.exportInline(listOf(span("public-span")))
        runBlocking { exporter.forceFlush() }

        assertTrue(exportThreads.single().startsWith("emb-otel-export"))
    }

    @Test
    fun `a throwing external exporter neither stops the others nor fails the internal store`() {
        val spanRepository = SpanRepository(FakeErrorHandler())
        val workingExporter = FakeSpanExporter()

        val result = exporter(spanRepository, listOf(throwingExporter(), workingExporter))
            .exportInline(listOf(span("public-span")))

        assertEquals(OperationResultCode.Success, result)
        assertEquals(1, spanRepository.completedOtelSpans().size)
        assertEquals("public-span", workingExporter.exportedSpans.single().name)
    }

    @Test
    fun `a throwing completed spans listener neither fails the export nor stops external exporters`() {
        val spanRepository = SpanRepository(FakeErrorHandler(throwOnSdkError = false))
        spanRepository.addCompletedOtelSpansListener { error("listener failed") }
        val externalExporter = FakeSpanExporter()

        val result = exporter(spanRepository, listOf(externalExporter))
            .exportInline(listOf(span("public-span")))

        assertEquals(OperationResultCode.Success, result)
        assertEquals(1, spanRepository.completedOtelSpans().size)
        assertEquals("public-span", externalExporter.exportedSpans.single().name)
    }

    @Test
    fun `nothing is exported when the export check fails`() {
        val spanRepository = SpanRepository(FakeErrorHandler())
        val externalExporter = FakeSpanExporter()
        val exporter = exporter(spanRepository, listOf(externalExporter), exportCheck = { false })

        assertEquals(OperationResultCode.Success, exporter.exportInline(listOf(span("public-span"))))

        assertTrue(spanRepository.completedOtelSpans().isEmpty())
        assertTrue(externalExporter.exportedSpans.isEmpty())
    }

    @Test
    fun `state values are left out of Embrace's payload and kept for external exporters when asked`() {
        val spanRepository = SpanRepository(FakeErrorHandler())
        val externalExporter = FakeSpanExporter()
        // the numbers are long on the span an exporter gets
        val attributes = mapOf<String, Any>(
            "custom" to "x",
            AppStateKeys.SEQ to 2L,
            AppStateKeys.END_SEQ to 3L,
            "${AppStateKeys.VALUE_PREFIX}network" to "wifi",
            "${AppStateKeys.VALUE_TYPE_PREFIX}screen-automatic" to AppStateKeys.VALUE_TYPE_SYSTEM,
            "${AppStateKeys.END_VALUE_PREFIX}network" to "cell",
            "${AppStateKeys.END_VALUE_TYPE_PREFIX}screen-automatic" to AppStateKeys.VALUE_TYPE_SYSTEM,
        )
        val span = SpanWithAttributes(FakeReadWriteSpan(FakeSpan(name = "public-span")), attributes)

        exporter(spanRepository, listOf(externalExporter), stripStateValues = { true }).exportInline(listOf(span))

        assertEquals(
            mapOf("custom" to "x", AppStateKeys.SEQ to "2", AppStateKeys.END_SEQ to "3"),
            spanRepository.completedOtelSpans().single().attributes?.associate { it.key to it.data },
        )
        assertEquals(attributes, externalExporter.exportedSpans.single().attributes)
    }

    @Test
    fun `state values stay in Embrace's payload when not asked to strip them`() {
        val spanRepository = SpanRepository(FakeErrorHandler())
        val fake = FakeSpan(name = "public-span").apply {
            setStringAttribute("${AppStateKeys.VALUE_PREFIX}network", "wifi")
        }

        exporter(spanRepository).exportInline(listOf(FakeReadWriteSpan(fake)))

        assertEquals(
            mapOf("${AppStateKeys.VALUE_PREFIX}network" to "wifi"),
            spanRepository.completedOtelSpans().single().attributes?.associate { it.key to it.data },
        )
    }

    private class SpanWithAttributes(
        delegate: FakeReadWriteSpan,
        override val attributes: Map<String, Any>,
    ) : SpanData by delegate
}
