package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeReadWriteSpan
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.internal.otel.attrs.embProcessIdentifier
import io.embrace.android.embracesdk.internal.otel.attrs.embSequenceId
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalApi::class)
class DefaultSpanProcessorTest {

    @Test
    fun `test export`() {
        val spanExporter = FakeSpanExporter()
        val processor = DefaultSpanProcessor(spanExporter, "pid")
        val span = FakeReadWriteSpan()
        processor.onStart(span, mockk(relaxed = true))

        assertEquals(span.attributes[embSequenceId.name], "1")
        assertEquals(span.attributes[embProcessIdentifier.name], "pid")

        processor.onEnd(span)
        assertEquals(span, spanExporter.exportedSpans.single())
    }
}
