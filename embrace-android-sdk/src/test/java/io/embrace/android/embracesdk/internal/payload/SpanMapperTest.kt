package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.fakes.FakeSpanData
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SpanMapperTest {

    @Test
    fun toSpan() {
        val input = EmbraceSpanData(FakeSpanData())
        val output = input.toNewPayload()

        assertEquals(input.traceId, output.traceId)
        assertEquals(input.spanId, output.spanId)
        assertEquals(input.parentSpanId, output.parentSpanId)
        assertEquals(input.name, output.name)
        assertEquals(input.startTimeNanos, output.startTimeUnixNano)
        assertEquals(input.endTimeNanos, output.endTimeUnixNano)
        assertEquals(input.status.name, checkNotNull(output.status).name)

        // validate event copied
        val inputEvent = input.events.single()
        val outputEvent = checkNotNull(output.events).single()
        assertEquals(inputEvent.name, outputEvent.name)
        assertEquals(inputEvent.timestampNanos, outputEvent.timeUnixNano)

        // test event attributes
        val inputEventAttrs = checkNotNull(inputEvent.attributes)
        val outputEventAttrs = checkNotNull(outputEvent.attributes?.single())
        assertEquals(inputEventAttrs.keys.single(), outputEventAttrs.key)
        assertEquals(inputEventAttrs.values.single(), outputEventAttrs.data)

        // test attributes
        val inputAttrs = checkNotNull(input.attributes)
        val outputAttrs = checkNotNull(output.attributes?.single())
        assertEquals(inputAttrs.keys.single(), outputAttrs.key)
        assertEquals(inputAttrs.values.single(), outputAttrs.data)
    }
}
