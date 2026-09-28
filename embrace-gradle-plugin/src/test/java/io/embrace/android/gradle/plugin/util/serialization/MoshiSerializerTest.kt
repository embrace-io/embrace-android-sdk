package io.embrace.android.gradle.plugin.util.serialization

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MoshiSerializerTest {
    private val moshiSerializer = MoshiSerializer()

    @Test
    fun `toJson throws IllegalArgumentException when serialization fails`() {
        assertThrows(IllegalArgumentException::class.java) {
            moshiSerializer.toJson(Double.NaN)
        }
    }

    @Test
    fun `toJson throws IllegalArgumentException when data is null`() {
        assertThrows(IllegalArgumentException::class.java) {
            moshiSerializer.toJson(null)
        }
    }

    @Test
    fun `toJson returns JSON string representation of object`() {
        val testObject = TestObject("Francisco", "Independiente")
        val json = moshiSerializer.toJson(testObject)
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
        moshiSerializer.toJson(TestObject("Francisco", "Independiente"), TestObject::class.java, stream)
        assertEquals("""{"name":"Francisco","team":"Independiente"}""", stream.toString(Charsets.UTF_8.name()))
        assertEquals(true, closed)
    }

    @Test
    fun `fromJson throws IllegalArgumentException when deserialization fails`() {
        assertThrows(IllegalArgumentException::class.java) {
            moshiSerializer.fromJson("This is not a JSON string", TestObject::class.java)
        }
    }

    @Test
    fun `fromJson throws IllegalArgumentException when json is empty`() {
        assertThrows(IllegalArgumentException::class.java) {
            moshiSerializer.fromJson("", TestObject::class.java)
        }
    }

    @Test
    fun `fromJson returns object of specified type`() {
        val json = """{"name":"Francisco","team":"Independiente"}"""
        val testObject = moshiSerializer.fromJson(json, TestObject::class.java)
        assertEquals("Francisco", testObject.name)
        assertEquals("Independiente", testObject.team)
    }

    @Test
    fun `fromJson reads from a stream`() {
        val json = """{"name":"Francisco","team":"Independiente"}"""
        val testObject = moshiSerializer.fromJson(ByteArrayInputStream(json.toByteArray()), TestObject::class.java)
        assertEquals("Francisco", testObject.name)
    }
}

@JsonClass(generateAdapter = true)
@Serializable
class TestObject(
    @Json(name = "name")
    @SerialName("name")
    val name: String,
    @Json(name = "team")
    @SerialName("team")
    val team: String,
)
