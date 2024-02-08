package io.embrace.android.embracesdk.internal.spans

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.junit.Assert.assertTrue
import org.junit.Test


internal class CompositeSpanExporterTest {

    @Test
    fun `Composite calls every exporter in the collection`() {
        //given
        val compositeSpanExporter = CompositeSpanExporter()
        val fakeSpanExporter = FakeSpanExporter()
        compositeSpanExporter.add(fakeSpanExporter)

        //when
        compositeSpanExporter.export(mutableListOf())
        compositeSpanExporter.flush()
        compositeSpanExporter.shutdown()

        //then
        assertTrue(fakeSpanExporter.exportCalled)
        assertTrue(fakeSpanExporter.flushCalled)
        assertTrue(fakeSpanExporter.shutdownCalled)
    }
}

internal class FakeSpanExporter : SpanExporter {
    var exportCalled = false
    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
        exportCalled = true
        return CompletableResultCode.ofSuccess()
    }
    var flushCalled = false
    override fun flush(): CompletableResultCode {
        flushCalled = true
        return CompletableResultCode.ofSuccess()
    }

    var shutdownCalled = false

    override fun shutdown(): CompletableResultCode {
        shutdownCalled = true
        return CompletableResultCode.ofSuccess()
    }

}
