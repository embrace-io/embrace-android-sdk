package io.embrace.android.embracesdk.payload

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class RnActionBreadcrumbTest {

    private val info = RnActionBreadcrumb(
        "my_action",
        1600000000,
        1600000100,
        mapOf("key" to "value"),
        104,
        "test"
    )

    @Test
    fun testSerialization() {
        val expectedInfo = ResourceReader.readResourceAsText("rn_action_breadcrumb_expected.json")
            .filter { !it.isWhitespace() }
        val observed = Gson().toJson(info)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("rn_action_breadcrumb_expected.json")
        val obj = Gson().fromJson(json, RnActionBreadcrumb::class.java)
        assertEquals("my_action", obj.name)
        assertEquals(1600000000, obj.getStartTime())
        assertEquals(1600000100, obj.endTime)
        assertEquals(mapOf("key" to "value"), obj.properties)
        assertEquals(104, obj.bytesSent)
        assertEquals("test", obj.output)
    }

    @Test
    fun testEmptyObject() {
        val info = Gson().fromJson("{}", RnActionBreadcrumb::class.java)
        assertNotNull(info)
    }
}
