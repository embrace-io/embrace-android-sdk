package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeReadWriteSpan
import io.embrace.android.embracesdk.fakes.FakeSpan
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

internal class DefaultSpanExporterTest {

    private fun span(name: String, vararg attrs: Pair<String, String>): SpanData =
        FakeReadWriteSpan(FakeSpan(name = name).apply { this.attrs.putAll(attrs) })

    @Test
    fun `export() should store spans in SpanSink`() {
        val spanSink: SpanSink = SpanSinkImpl()
        val exporter = DefaultSpanExporter(spanSink, emptyList()) { true }

        runBlocking { exporter.export(listOf(span("public-span"))) }

        assertFalse(spanSink.completedSpans().isEmpty())
    }

    @Test
    fun `private spans should be filtered out from external exporters but still stored internally`() {
        val spanSink: SpanSink = SpanSinkImpl()
        val externalExporter = FakeSpanExporter()
        val exporter = DefaultSpanExporter(spanSink, listOf(externalExporter)) { true }

        val publicSpan = span("public-span")
        val privateSpan = span("private-span", PrivateSpan.key to PrivateSpan.value)

        runBlocking { exporter.export(listOf(publicSpan, privateSpan)) }

        assertEquals(2, spanSink.completedSpans().size)
        assertEquals(1, externalExporter.exportedSpans.size)
        assertEquals("public-span", externalExporter.exportedSpans.first().name)
    }

    @Test
    fun `export() returns failure when an external exporter throws`() {
        val spanSink: SpanSink = SpanSinkImpl()
        val throwingExporter = object : SpanExporter {
            override suspend fun export(telemetry: List<SpanData>): OperationResultCode =
                throw RuntimeException("boom")

            override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
            override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
        }
        val exporter = DefaultSpanExporter(spanSink, listOf(throwingExporter)) { true }

        val result = runBlocking { exporter.export(listOf(span("public-span"))) }

        assertEquals(OperationResultCode.Failure, result)
    }
}
