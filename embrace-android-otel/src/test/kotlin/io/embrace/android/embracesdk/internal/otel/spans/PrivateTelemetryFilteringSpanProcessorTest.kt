package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeReadWriteSpan
import io.embrace.android.embracesdk.fakes.FakeSpan
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.opentelemetry.kotlin.NoopOpenTelemetry
import org.junit.Assert.assertEquals
import org.junit.Test

internal class PrivateTelemetryFilteringSpanProcessorTest {

    @Test
    fun `only non-private spans are forwarded`() {
        val delegate = FakeSpanProcessor()
        val processor = PrivateTelemetryFilteringSpanProcessor(delegate)
        listOf(
            FakeReadWriteSpan(FakeSpan(name = "public")),
            FakeReadWriteSpan(FakeSpan(name = "private").apply { attrs[PrivateSpan.key] = PrivateSpan.value }),
        ).forEach { span ->
            processor.onStart(span, NoopOpenTelemetry.context.implicit())
            processor.onEnding(span)
            processor.onEnd(span)
        }
        assertEquals(listOf("public"), delegate.startedSpanNames)
        assertEquals(listOf("public"), delegate.endingSpanNames)
        assertEquals(listOf("public"), delegate.endedSpanNames)
    }
}
