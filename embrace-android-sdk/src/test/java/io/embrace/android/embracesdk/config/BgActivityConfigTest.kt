package io.embrace.android.embracesdk.config

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class BgActivityConfigTest {

    private val serializer = EmbraceSerializer()

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
        val cfg = serializer.fromJson(data, BackgroundActivityRemoteConfig::class.java)
        assertEquals(0.5f, cfg.threshold)
    }

    @Test
    fun testDeserializationEmptyObj() {
        val cfg = serializer.fromJson("{}", BackgroundActivityRemoteConfig::class.java)
        assertNull(cfg.threshold)
    }
}
