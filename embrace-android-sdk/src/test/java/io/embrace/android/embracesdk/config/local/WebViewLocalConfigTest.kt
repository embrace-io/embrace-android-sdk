package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.internal.config.local.WebViewLocalConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class WebViewLocalConfigTest {

    @Test
    fun testDefaults() {
        val cfg = WebViewLocalConfig()
        assertNull(cfg.captureWebViews)
        assertNull(cfg.captureQueryParams)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<WebViewLocalConfig>("web_view_config.json")
        assertFalse(checkNotNull(obj.captureWebViews))
        assertFalse(checkNotNull(obj.captureQueryParams))
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<WebViewLocalConfig>()
        assertNull(obj.captureWebViews)
        assertNull(obj.captureQueryParams)
    }
}
