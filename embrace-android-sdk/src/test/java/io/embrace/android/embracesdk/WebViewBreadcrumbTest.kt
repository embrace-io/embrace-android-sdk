package io.embrace.android.embracesdk

import com.google.gson.Gson
import io.embrace.android.embracesdk.payload.WebViewBreadcrumb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class WebViewBreadcrumbTest {

    private val info = WebViewBreadcrumb(
        "url",
        1600000000
    )

    @Test
    fun testSerialization() {
        val expectedInfo = ResourceReader.readResourceAsText("webview_breadcrumb_expected.json")
            .filter { !it.isWhitespace() }
        val observed = Gson().toJson(info)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("webview_breadcrumb_expected.json")
        val obj = Gson().fromJson(json, WebViewBreadcrumb::class.java)
        assertEquals("url", obj.url)
        assertEquals(1600000000, obj.getStartTime())
    }

    @Test
    fun testEmptyObject() {
        val info = Gson().fromJson("{}", WebViewBreadcrumb::class.java)
        assertNotNull(info)
    }
}
