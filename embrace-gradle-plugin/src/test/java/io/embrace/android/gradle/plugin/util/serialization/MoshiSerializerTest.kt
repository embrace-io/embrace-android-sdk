package io.embrace.android.gradle.plugin.util.serialization

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.junit.Assert.assertEquals
import org.junit.Test

class MoshiSerializerTest {
    private val moshiSerializer = MoshiSerializer()

    @Test
    fun `toJson throws exception when serialization fails`() {
        val data = Any()
        try {
            moshiSerializer.toJson(data)
        } catch (e: IllegalArgumentException) {
            assertEquals(IllegalArgumentException::class.java, e::class.java)
        }
    }

    @Test
    fun `toJson throws exception when data is null`() {
        try {
            moshiSerializer.toJson(null)
        } catch (e: IllegalArgumentException) {
            assertEquals(IllegalArgumentException::class.java, e::class.java)
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
    fun `fromJson throws exception when deserialization fails`() {
        val nonJsonString = "This is not a JSON string"
        try {
            moshiSerializer.fromJson(nonJsonString, TestObject::class.java)
        } catch (e: IllegalArgumentException) {
            assertEquals(IllegalArgumentException::class.java, e::class.java)
        }
    }

    @Test
    fun `fromJson throws exception when json is empty`() {
        val emptyJsonString = ""
        try {
            moshiSerializer.fromJson(emptyJsonString, TestObject::class.java)
        } catch (e: IllegalArgumentException) {
            assertEquals(IllegalArgumentException::class.java, e::class.java)
        }
    }

    @Test
    fun `fromJson returns object of specified type`() {
        val json = """{"name":"Francisco","team":"Independiente"}"""
        val testObject = moshiSerializer.fromJson(json, TestObject::class.java)
        assertEquals("Francisco", testObject.name)
        assertEquals("Independiente", testObject.team)
    }
}

@JsonClass(generateAdapter = true)
class TestObject(
    @Json(name = "name") val name: String,
    @Json(name = "team") val team: String
)
