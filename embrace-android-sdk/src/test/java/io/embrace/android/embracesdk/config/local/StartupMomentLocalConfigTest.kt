package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class StartupMomentLocalConfigTest {

    @Test
    fun testDefaults() {
        val cfg = StartupMomentLocalConfig()
        assertNull(cfg.automaticallyEnd)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<StartupMomentLocalConfig>("startup_moment_config.json")
        assertFalse(checkNotNull(obj.automaticallyEnd))
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<StartupMomentLocalConfig>()
        assertNull(obj.automaticallyEnd)
    }
}
