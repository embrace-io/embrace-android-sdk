package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class AppLocalConfigTest {

    @Test
    fun testDefaults() {
        val cfg = AppLocalConfig()
        assertNull(cfg.reportDiskUsage)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<AppLocalConfig>("app_config.json")
        assertFalse(checkNotNull(obj.reportDiskUsage))
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<AppLocalConfig>()
        assertNull(obj.reportDiskUsage)
    }
}
