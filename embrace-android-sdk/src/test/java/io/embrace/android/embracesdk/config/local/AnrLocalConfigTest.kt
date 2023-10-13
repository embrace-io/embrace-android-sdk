package io.embrace.android.embracesdk.config.local

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AnrLocalConfigTest {

    @Test
    fun testDefaults() {
        val cfg = AnrLocalConfig()
        assertNull(cfg.captureGoogle)
        assertNull(cfg.captureUnityThread)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("anr_config.json")
        val obj = Gson().fromJson(json, AnrLocalConfig::class.java)
        assertTrue(checkNotNull(obj.captureGoogle))
        assertTrue(checkNotNull(obj.captureUnityThread))
    }

    @Test
    fun testEmptyObject() {
        val obj = Gson().fromJson("{}", AnrLocalConfig::class.java)
        assertNull(obj.captureGoogle)
        assertNull(obj.captureUnityThread)
    }
}
