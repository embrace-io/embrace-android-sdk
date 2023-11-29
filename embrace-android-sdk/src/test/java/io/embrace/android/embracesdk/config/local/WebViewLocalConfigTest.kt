package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class WebViewLocalConfigTest {

    private val serializer = EmbraceSerializer()

    @Test
    fun testDefaults() {
        val cfg = WebViewLocalConfig()
        assertNull(cfg.captureWebViews)
        assertNull(cfg.captureQueryParams)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("web_view_config.json")
        val obj = serializer.fromJson(json, WebViewLocalConfig::class.java)
        assertFalse(checkNotNull(obj.captureWebViews))
        assertFalse(checkNotNull(obj.captureQueryParams))
    }

    @Test
    fun testEmptyObject() {
        val obj = serializer.fromJson("{}", WebViewLocalConfig::class.java)
        assertNull(obj.captureWebViews)
        assertNull(obj.captureQueryParams)
    }
}
