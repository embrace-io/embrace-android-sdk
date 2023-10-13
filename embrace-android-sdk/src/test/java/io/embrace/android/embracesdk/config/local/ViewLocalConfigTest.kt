package io.embrace.android.embracesdk.config.local

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
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
        val json = ResourceReader.readResourceAsText("view_config.json")
        val obj = Gson().fromJson(json, ViewLocalConfig::class.java)
        assertFalse(checkNotNull(obj.enableAutomaticActivityCapture))
    }

    @Test
    fun testEmptyObject() {
        val obj = Gson().fromJson("{}", ViewLocalConfig::class.java)
        assertNull(obj.enableAutomaticActivityCapture)
    }
}
