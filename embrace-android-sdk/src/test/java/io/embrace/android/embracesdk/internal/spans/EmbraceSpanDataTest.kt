package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.fixtures.testSpan
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EmbraceSpanDataTest {

    private val serializer = EmbraceSerializer()
    private val span = testSpan

    @Test
    fun testDeserialization() {
        val deserializedSpan = deserializeJsonFromResource<EmbraceSpanData>("span_expected.json")
        assertEquals(span.name, deserializedSpan.name)
        assertEquals(span.spanId, deserializedSpan.spanId)
        assertEquals(span.parentSpanId, deserializedSpan.parentSpanId)
        assertEquals(span.traceId, deserializedSpan.traceId)
        assertEquals(span.startTimeNanos, deserializedSpan.startTimeNanos)
        assertEquals(span.endTimeNanos, deserializedSpan.endTimeNanos)
        assertEquals(span.status, deserializedSpan.status)
        span.events.forEachIndexed { index, event ->
            assertEquals("Failed for event ${event.name}", event, deserializedSpan.events[index])
            event.attributes.forEach {
                assertEquals("Failed for attribute ${it.key}", it.value, event.attributes[it.key])
            }
        }
        span.attributes.forEach {
            assertEquals("Failed for attribute ${it.key}", it.value, deserializedSpan.attributes[it.key])
        }
    }

    @Test
    fun testSerialization() {
        // Take a span, serialize it to JSON, then deserialize it back and compare. This avoids having to deal with the exactly
        // serialized form details like whitespace that won't matter when we deserialize it back to the object form.
        val serializedSpanJson = serializer.toJson(span)
        val deserializedSpan = serializer.fromJson(serializedSpanJson, EmbraceSpanData::class.java)
        assertEquals(span, deserializedSpan)
    }
}
