package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
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
        val obj = deserializeJsonFromResource<CrashHandlerLocalConfig>("crash_handler_config.json")
        assertFalse(checkNotNull(obj.enabled))
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<CrashHandlerLocalConfig>()
        assertNull(obj.enabled)
    }
}
