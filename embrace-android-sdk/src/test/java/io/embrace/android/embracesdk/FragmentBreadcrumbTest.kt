package io.embrace.android.embracesdk

import com.google.gson.Gson
import io.embrace.android.embracesdk.payload.FragmentBreadcrumb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class FragmentBreadcrumbTest {

    private val info = FragmentBreadcrumb(
        "test",
        1600000000,
        1600001000,
    )

    @Test
    fun testSerialization() {
        val expectedInfo = ResourceReader.readResourceAsText("fragment_breadcrumb_expected.json")
            .filter { !it.isWhitespace() }
        val observed = Gson().toJson(info)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("fragment_breadcrumb_expected.json")
        val obj = Gson().fromJson(json, FragmentBreadcrumb::class.java)
        assertEquals("test", obj.name)
        assertEquals(1600000000, obj.getStartTime())
        assertEquals(1600001000, obj.endTime)
    }

    @Test
    fun testEmptyObject() {
        val info = Gson().fromJson("{}", FragmentBreadcrumb::class.java)
        assertNotNull(info)
    }
}
