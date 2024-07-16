package io.embrace.android.embracesdk.config

import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class BgActivityConfigTest {

    @Test
    fun testDefaults() {
        val cfg = BackgroundActivityRemoteConfig(null)
        assertNull(cfg.threshold)
    }

    @Test
    fun testOverride() {
        val cfg = BackgroundActivityRemoteConfig(
            5f,
        )
        assertEquals(5f, cfg.threshold)
    }

    @Test
    fun testDeserialization() {
        val cfg = deserializeJsonFromResource<BackgroundActivityRemoteConfig>("bg_activity_config.json")
        assertEquals(0.5f, cfg.threshold)
    }

    @Test
    fun testDeserializationEmptyObj() {
        val cfg = deserializeEmptyJsonString<BackgroundActivityRemoteConfig>()
        assertNull(cfg.threshold)
    }
}
