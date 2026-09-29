package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeReadWriteSpan
import io.embrace.android.embracesdk.fakes.FakeSpan
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

internal class PrivateTelemetryFilteringSpanExporterTest {

    @Test
    fun `only non-private spans are forwarded`() {
        val delegate = FakeSpanExporter()
        val exporter = PrivateTelemetryFilteringSpanExporter(delegate)
        val spans = listOf(
            FakeReadWriteSpan(FakeSpan(name = "public")),
            FakeReadWriteSpan(FakeSpan(name = "private").apply { attrs[PrivateSpan.key] = PrivateSpan.value }),
        )
        runBlocking { exporter.export(spans) }
        assertEquals(listOf("public"), delegate.exportedSpans.map { it.name })
    }
}
