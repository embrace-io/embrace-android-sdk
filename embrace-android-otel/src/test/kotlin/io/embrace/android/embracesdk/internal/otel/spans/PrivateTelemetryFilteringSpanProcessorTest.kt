package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeReadWriteSpan
import io.embrace.android.embracesdk.fakes.FakeSpan
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.opentelemetry.kotlin.NoopOpenTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PrivateTelemetryFilteringSpanProcessorTest {

    @Test
    fun `only non-private spans are forwarded`() {
        val delegate = FakeSpanProcessor()
        val processor = PrivateTelemetryFilteringSpanProcessor(delegate) { true }
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

    @Test
    fun `nothing is forwarded when the export check fails`() {
        val delegate = FakeSpanProcessor()
        val processor = PrivateTelemetryFilteringSpanProcessor(delegate) { false }
        val span = FakeReadWriteSpan(FakeSpan(name = "public"))
        processor.onStart(span, NoopOpenTelemetry.context.implicit())
        processor.onEnding(span)
        processor.onEnd(span)
        assertTrue(delegate.startedSpanNames.isEmpty())
        assertTrue(delegate.endingSpanNames.isEmpty())
        assertTrue(delegate.endedSpanNames.isEmpty())
    }
}
