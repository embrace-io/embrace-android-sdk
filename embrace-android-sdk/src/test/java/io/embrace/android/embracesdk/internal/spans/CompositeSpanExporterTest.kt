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
        val fakeSpanExporter1 = FakeSpanExporter()
        val fakeSpanExporter2 = FakeSpanExporter()
        compositeSpanExporter.addAll(fakeSpanExporter1, fakeSpanExporter2)

        //when
        compositeSpanExporter.export(mutableListOf())
        compositeSpanExporter.flush()
        compositeSpanExporter.shutdown()

        //then
        assertTrue(fakeSpanExporter1.exportCalled)
        assertTrue(fakeSpanExporter2.exportCalled)
        assertTrue(fakeSpanExporter1.flushCalled)
        assertTrue(fakeSpanExporter2.flushCalled)
        assertTrue(fakeSpanExporter1.shutdownCalled)
        assertTrue(fakeSpanExporter2.shutdownCalled)
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
