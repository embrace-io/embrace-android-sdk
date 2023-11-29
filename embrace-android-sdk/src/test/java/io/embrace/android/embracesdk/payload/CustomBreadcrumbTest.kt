package io.embrace.android.embracesdk.payload

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class CustomBreadcrumbTest {

    private val serializer = EmbraceSerializer()

    private val info = CustomBreadcrumb(
        "test",
        1600000000
    )

    @Test
    fun testSerialization() {
        val expectedInfo = ResourceReader.readResourceAsText("custom_breadcrumb_expected.json")
            .filter { !it.isWhitespace() }
        val observed = serializer.toJson(info)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("custom_breadcrumb_expected.json")
        val obj = serializer.fromJson(json, CustomBreadcrumb::class.java)
        assertEquals("test", obj.message)
        assertEquals(1600000000, obj.getStartTime())
    }

    @Test
    fun testEmptyObject() {
        val info = serializer.fromJson("{}", CustomBreadcrumb::class.java)
        assertNotNull(info)
    }
}
