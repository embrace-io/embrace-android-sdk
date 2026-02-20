package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeReadWriteSpan
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.internal.arch.attrs.embProcessIdentifier
import io.embrace.android.embracesdk.internal.arch.attrs.embSequenceId
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.semconv.IncubatingApi
import io.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalApi::class)
class EmbraceSpanProcessorTest {

    @OptIn(IncubatingApi::class)
    @Test
    fun `test export`() {
        val spanExporter = FakeSpanExporter()
        val processor = EmbraceSpanProcessor({ "sid" }, "pid", spanExporter)
        val span = FakeReadWriteSpan()
        processor.onStart(span, NoopOpenTelemetry.contextFactory.implicitContext())

        assertEquals(span.attributes[embSequenceId.name], "1")
        assertEquals(span.attributes[embProcessIdentifier.name], "pid")
        assertEquals(span.attributes[SessionAttributes.SESSION_ID], "sid")

        processor.onEnd(span)
        assertEquals(span, spanExporter.exportedSpans.single())
    }
}
