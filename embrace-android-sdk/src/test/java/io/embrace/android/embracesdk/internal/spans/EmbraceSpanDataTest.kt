package io.embrace.android.embracesdk.internal.spans

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.fixtures.testSpan
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EmbraceSpanDataTest {
    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("span_expected.json")
        val deserializedSpan = Gson().fromJson(json, EmbraceSpanData::class.java)
        assertEquals(testSpan.name, deserializedSpan.name)
        assertEquals(testSpan.spanId, deserializedSpan.spanId)
        assertEquals(testSpan.parentSpanId, deserializedSpan.parentSpanId)
        assertEquals(testSpan.traceId, deserializedSpan.traceId)
        assertEquals(testSpan.startTimeNanos, deserializedSpan.startTimeNanos)
        assertEquals(testSpan.endTimeNanos, deserializedSpan.endTimeNanos)
        assertEquals(testSpan.status, deserializedSpan.status)
        testSpan.events.forEachIndexed { index, event ->
            assertEquals("Failed for event ${event.name}", event, deserializedSpan.events[index])
            event.attributes.forEach {
                assertEquals("Failed for attribute ${it.key}", it.value, event.attributes[it.key])
            }
        }
        testSpan.attributes.forEach {
            assertEquals("Failed for attribute ${it.key}", it.value, deserializedSpan.attributes[it.key])
        }
    }

    @Test
    fun testSerialization() {
        // Take a span, serialize it to JSON, then deserialize it back and compare. This avoids having to deal with the exactly
        // serialized form details like whitespace that won't matter when we deserialize it back to the object form.
        val serializedSpanJson = Gson().toJson(testSpan)
        val deserializedSpan = Gson().fromJson(serializedSpanJson, EmbraceSpanData::class.java)
        assertEquals(testSpan, deserializedSpan)
    }
}
