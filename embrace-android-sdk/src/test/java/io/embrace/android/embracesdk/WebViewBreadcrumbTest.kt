package io.embrace.android.embracesdk

import com.squareup.moshi.JsonDataException
import io.embrace.android.embracesdk.payload.WebViewBreadcrumb
import org.junit.Assert.assertEquals
import org.junit.Test

internal class WebViewBreadcrumbTest {

    private val info = WebViewBreadcrumb(
        "url",
        1600000000
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("webview_breadcrumb_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<WebViewBreadcrumb>("webview_breadcrumb_expected.json")
        assertEquals("url", obj.url)
        assertEquals(1600000000, obj.getStartTime())
    }

    @Test(expected = JsonDataException::class)
    fun testEmptyObject() {
        deserializeEmptyJsonString<WebViewBreadcrumb>()
    }
}
