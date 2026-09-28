package io.embrace.android.gradle.plugin.util.serialization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class JsonSerializerTest {
    private val serializer = JsonSerializer()

    @Test
    fun `toJson throws IllegalArgumentException when serialization fails`() {
        assertThrows(IllegalArgumentException::class.java) {
            serializer.toJson(Double.NaN, Double.serializer())
        }
    }

    @Test
    fun `toJson returns JSON string representation of object`() {
        val testObject = TestObject("Francisco", "Independiente")
        val json = serializer.toJson(testObject, TestObject.serializer())
        val expectedJson = """{"name":"Francisco","team":"Independiente"}"""
        assertEquals(expectedJson, json)
    }

    @Test
    fun `toJson writes to and closes the stream`() {
        var closed = false
        val stream = object : ByteArrayOutputStream() {
            override fun close() {
                closed = true
            }
        }
        serializer.toJson(TestObject("Francisco", "Independiente"), TestObject.serializer(), stream)
        assertEquals("""{"name":"Francisco","team":"Independiente"}""", stream.toString(Charsets.UTF_8.name()))
        assertEquals(true, closed)
    }

    @Test
    fun `fromJson throws IllegalArgumentException when deserialization fails`() {
        assertThrows(IllegalArgumentException::class.java) {
            serializer.fromJson("This is not a JSON string", TestObject.serializer())
        }
    }

    @Test
    fun `fromJson throws IllegalArgumentException when json is empty`() {
        assertThrows(IllegalArgumentException::class.java) {
            serializer.fromJson("", TestObject.serializer())
        }
    }

    @Test
    fun `fromJson returns object of specified type`() {
        val json = """{"name":"Francisco","team":"Independiente"}"""
        val testObject = serializer.fromJson(json, TestObject.serializer())
        assertEquals("Francisco", testObject.name)
        assertEquals("Independiente", testObject.team)
    }

    @Test
    fun `fromJson reads from a stream`() {
        val json = """{"name":"Francisco","team":"Independiente"}"""
        val testObject = serializer.fromJson(ByteArrayInputStream(json.toByteArray()), TestObject.serializer())
        assertEquals("Francisco", testObject.name)
    }
}

@Serializable
class TestObject(
    @SerialName("name")
    val name: String,
    @SerialName("team")
    val team: String,
)
