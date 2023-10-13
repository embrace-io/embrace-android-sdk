package io.embrace.android.embracesdk.config

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.config.remote.BackgroundActivityRemoteConfig
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
        val data = ResourceReader.readResourceAsText("bg_activity_config.json")
        val cfg = Gson().fromJson(data, BackgroundActivityRemoteConfig::class.java)
        assertEquals(0.5f, cfg.threshold)
    }

    @Test
    fun testDeserializationEmptyObj() {
        val cfg = Gson().fromJson("{}", BackgroundActivityRemoteConfig::class.java)
        assertNull(cfg.threshold)
    }
}
