package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
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
        val obj = deserializeJsonFromResource<AnrLocalConfig>("anr_config.json")
        assertTrue(checkNotNull(obj.captureGoogle))
        assertTrue(checkNotNull(obj.captureUnityThread))
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<AnrLocalConfig>()
        assertNull(obj.captureGoogle)
        assertNull(obj.captureUnityThread)
    }
}
