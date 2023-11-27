package io.embrace.android.embracesdk.payload

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class NativeCrashTest {

    private val serializer = EmbraceSerializer()

    private val info = NativeCrash(
        "id",
        "crashMessage",
        mapOf("key" to "value"),
        listOf(
            NativeCrashDataError(
                5,
                2
            )
        ),
        2,
        "map"
    )

    @Test
    fun testSerialization() {
        val expectedInfo = ResourceReader.readResourceAsText("native_crash_expected.json")
            .filter { !it.isWhitespace() }
        val observed = serializer.toJson(info)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("native_crash_expected.json")
        val obj = serializer.fromJson(json, NativeCrash::class.java)
        assertEquals("id", obj.id)
        assertEquals("crashMessage", obj.crashMessage)
        assertEquals(mapOf("key" to "value"), obj.symbols)
        assertEquals(2, obj.unwindError)
        assertEquals("map", obj.map)

        val err = obj.errors?.single()
        assertEquals(5, err?.number)
        assertEquals(2, err?.context)
    }

    @Test
    fun testEmptyObject() {
        val info = serializer.fromJson("{}", NativeCrash::class.java)
        assertNotNull(info)
    }
}
