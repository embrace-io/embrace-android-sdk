package io.embrace.android.embracesdk.config.local

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
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
        val json = ResourceReader.readResourceAsText("app_config.json")
        val obj = Gson().fromJson(json, AppLocalConfig::class.java)
        assertFalse(checkNotNull(obj.reportDiskUsage))
    }

    @Test
    fun testEmptyObject() {
        val obj = Gson().fromJson("{}", AppLocalConfig::class.java)
        assertNull(obj.reportDiskUsage)
    }
}
