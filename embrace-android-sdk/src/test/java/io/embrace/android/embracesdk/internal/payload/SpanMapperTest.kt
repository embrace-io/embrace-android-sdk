package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.fakes.FakeSpanData
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SpanMapperTest {

    @Test
    fun toSpan() {
        val input: SpanData = FakeSpanData()
        val output = input.toNewPayload()

        assertEquals(input.traceId, output.traceId)
        assertEquals(input.spanId, output.spanId)
        assertEquals(input.parentSpanId, output.parentSpanId)
        assertEquals(input.name, output.name)
        assertEquals(input.startEpochNanos, output.startTimeUnixNano)
        assertEquals(input.endEpochNanos, output.endTimeUnixNano)
        assertEquals(input.status.statusCode.name, checkNotNull(output.status).name)

        // validate event copied
        val inputEvent = input.events.single()
        val outputEvent = checkNotNull(output.events).single()
        assertEquals(inputEvent.name, outputEvent.name)
        assertEquals(inputEvent.epochNanos, outputEvent.timeUnixNano)

        // test event attributes
        val inputEventAttrs = checkNotNull(inputEvent.attributes?.asMap())
        val outputEventAttrs = checkNotNull(outputEvent.attributes?.single())
        assertEquals(inputEventAttrs.keys.single().key, outputEventAttrs.key)
        assertEquals(inputEventAttrs.values.single(), outputEventAttrs.data)

        // test attributes
        val inputAttrs = checkNotNull(input.attributes?.asMap())
        val outputAttrs = checkNotNull(output.attributes?.single())
        assertEquals(inputAttrs.keys.single().key, outputAttrs.key)
        assertEquals(inputAttrs.values.single(), outputAttrs.data)
    }
}
