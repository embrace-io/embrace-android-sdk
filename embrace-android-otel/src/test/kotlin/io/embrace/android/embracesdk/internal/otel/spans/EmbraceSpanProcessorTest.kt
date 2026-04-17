package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeReadWriteSpan
import io.embrace.android.embracesdk.fakes.FakeSessionIdProvider
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Test

class EmbraceSpanProcessorTest {

    @Test
    fun `test export`() {
        val spanExporter = FakeSpanExporter()
        val provider = FakeSessionIdProvider(userSessionId = "user-sid", sessionPartId = "part-sid")
        val processor = EmbraceSpanProcessor({ provider }, "pid", spanExporter)
        val span = FakeReadWriteSpan()
        processor.onStart(span, NoopOpenTelemetry.context.implicit())

        assertEquals(span.attributes[EmbSessionAttributes.EMB_PRIVATE_SEQUENCE_ID], "1")
        assertEquals(span.attributes[EmbSessionAttributes.EMB_PROCESS_IDENTIFIER], "pid")
        assertEquals(span.attributes[SessionAttributes.SESSION_ID], "user-sid")
        assertEquals(span.attributes[EmbSessionAttributes.EMB_USER_SESSION_ID], "user-sid")
        assertEquals(span.attributes[EmbSessionAttributes.EMB_SESSION_PART_ID], "part-sid")

        processor.onEnd(span)
        assertEquals(span, spanExporter.exportedSpans.single())
    }
}
