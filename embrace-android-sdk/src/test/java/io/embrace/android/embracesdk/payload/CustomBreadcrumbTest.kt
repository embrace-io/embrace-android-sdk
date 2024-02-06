package io.embrace.android.embracesdk.payload

import com.squareup.moshi.JsonDataException
import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import org.junit.Assert.assertEquals
import org.junit.Test

internal class CustomBreadcrumbTest {

    private val info = CustomBreadcrumb(
        "test",
        1600000000
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("custom_breadcrumb_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<CustomBreadcrumb>("custom_breadcrumb_expected.json")
        assertEquals("test", obj.message)
        assertEquals(1600000000, obj.getStartTime())
    }

    @Test(expected = JsonDataException::class)
    fun testEmptyObject() {
        deserializeEmptyJsonString<CustomBreadcrumb>()
    }
}
