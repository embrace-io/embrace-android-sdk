package io.embrace.android.embracesdk.config.local

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class TapsLocalConfigTest {

    @Test
    fun testDefaults() {
        val cfg = TapsLocalConfig()
        assertNull(cfg.captureCoordinates)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("taps_config.json")
        val obj = Gson().fromJson(json, TapsLocalConfig::class.java)
        assertFalse(checkNotNull(obj.captureCoordinates))
    }

    @Test
    fun testEmptyObject() {
        val obj = Gson().fromJson("{}", TapsLocalConfig::class.java)
        assertNull(obj.captureCoordinates)
    }
}
