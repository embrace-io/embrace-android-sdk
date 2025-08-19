package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Types
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.truncatedStacktrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

internal class EmbraceSerializerTest {

    private val serializer = EmbraceSerializer()
    private val payload = Attribute("key", "value")
    private val payload2 = Attribute("key_2", "value_2")
    private val clz = Attribute::class.java

    @Test
    fun testWriteToFile() {
        val stream = ByteArrayOutputStream()
        serializer.toJson(payload, clz, stream)
        assertTrue(stream.toByteArray().isNotEmpty())
    }

    @Test
    fun testLoadObject() {
        val stream = serializer.toJson(payload, clz).byteInputStream()
        val result = serializer.fromJson(stream, clz)
        assertEquals(payload, result)
    }

    @Test
    fun testLoadListOfObjects() {
        val listOfObjects = listOf(payload, payload2)
        val type = Types.newParameterizedType(List::class.java, clz)
        val stream = serializer.toJson(listOfObjects, type).byteInputStream()
        val result = serializer.fromJson(stream, type) as List<Attribute>
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
