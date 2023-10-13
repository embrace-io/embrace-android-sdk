package io.embrace.android.embracesdk.config.local

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class CrashHandlerLocalConfigTest {

    @Test
    fun testDefaults() {
        val cfg = CrashHandlerLocalConfig()
        assertNull(cfg.enabled)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("crash_handler_config.json")
        val obj = Gson().fromJson(json, CrashHandlerLocalConfig::class.java)
        assertFalse(checkNotNull(obj.enabled))
    }

    @Test
    fun testEmptyObject() {
        val obj = Gson().fromJson("{}", CrashHandlerLocalConfig::class.java)
        assertNull(obj.enabled)
    }
}
