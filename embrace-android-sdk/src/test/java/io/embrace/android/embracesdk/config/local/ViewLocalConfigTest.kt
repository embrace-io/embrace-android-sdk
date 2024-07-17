package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.internal.config.local.ViewLocalConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class ViewLocalConfigTest {

    @Test
    fun testDefaults() {
        val cfg = ViewLocalConfig()
        assertNull(cfg.enableAutomaticActivityCapture)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<ViewLocalConfig>("view_config.json")
        assertFalse(checkNotNull(obj.enableAutomaticActivityCapture))
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<ViewLocalConfig>()
        assertNull(obj.enableAutomaticActivityCapture)
    }
}
