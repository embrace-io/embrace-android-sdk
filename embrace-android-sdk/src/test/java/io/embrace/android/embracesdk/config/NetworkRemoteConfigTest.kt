package io.embrace.android.embracesdk.config

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.config.remote.NetworkRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class NetworkRemoteConfigTest {

    @Test
    fun testDefaults() {
        val cfg = NetworkRemoteConfig(null, null)
        verifyDefaults(cfg)
    }

    @Test
    fun testOverride() {
        val cfg = NetworkRemoteConfig(
            2000,
            mapOf("google.com" to 500)
        )
        assertEquals(2000, cfg.defaultCaptureLimit)
        assertEquals(mapOf("google.com" to 500), cfg.domainLimits)
    }

    @Test
    fun testDeserialization() {
        val data = ResourceReader.readResourceAsText("network_config.json")
        val cfg = Gson().fromJson(data, NetworkRemoteConfig::class.java)

        assertEquals(2000, cfg.defaultCaptureLimit)
        assertEquals(mapOf("google.com" to 500), cfg.domainLimits)
    }

    @Test
    fun testDeserializationEmptyObj() {
        val cfg = Gson().fromJson("{}", NetworkRemoteConfig::class.java)
        verifyDefaults(cfg)
    }

    private fun verifyDefaults(cfg: NetworkRemoteConfig) {
        assertNull(cfg.defaultCaptureLimit)
        assertNull(cfg.domainLimits)
    }
}
