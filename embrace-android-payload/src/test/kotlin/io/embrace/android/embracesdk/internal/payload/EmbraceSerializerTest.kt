package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.fromJson
import io.embrace.android.embracesdk.internal.serialization.toJson
import io.embrace.android.embracesdk.internal.serialization.truncatedStacktrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

internal class EmbraceSerializerTest {

    private val serializer = EmbraceSerializer()
    private val payload = Attribute("key", "value")
    private val payload2 = Attribute("key_2", "value_2")

    @Test
    fun testWriteToFile() {
        val stream = ByteArrayOutputStream()
        serializer.toJson(payload, stream)
        assertTrue(stream.toByteArray().isNotEmpty())
    }

    @Test
    fun testLoadObject() {
        val stream = serializer.toJson(payload).byteInputStream()
        val result: Attribute = serializer.fromJson(stream)
        assertEquals(payload, result)
    }

    @Test
    fun testLoadListOfObjects() {
        val listOfObjects = listOf(payload, payload2)
        val stream = serializer.toJson(listOfObjects).byteInputStream()
        val result: List<Attribute> = serializer.fromJson(stream)
        assertEquals(2, result.size)
        assertEquals(payload, result[0])
        assertEquals(payload2, result[1])
    }

    @Test
    fun `verify truncation of stacktrace serialized JSON`() {
        val elements = Array(201) { StackTraceElement("A", "B", "C", 1) }
        val serializedString = serializer.truncatedStacktrace(elements)
        assertEquals(200, serializedString.count { it == 'A' })
        assertEquals(200, serializedString.count { it == 'B' })
        assertEquals(200, serializedString.count { it == 'C' })
        assertEquals(200, serializedString.count { it == '1' })
    }
}
