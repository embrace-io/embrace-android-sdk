package io.embrace.android.embracesdk.config

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.config.remote.LogRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class LogRemoteConfigTest {

    @Test
    fun testDefaults() {
        val cfg = LogRemoteConfig(null, null, null, null)
        verifyDefaults(cfg)
    }

    @Test
    fun testOverride() {
        val cfg = LogRemoteConfig(
            768,
            50,
            200,
            500,
        )
        assertEquals(768, cfg.logMessageMaximumAllowedLength)
        assertEquals(50, cfg.logInfoLimit)
        assertEquals(200, cfg.logWarnLimit)
        assertEquals(500, cfg.logErrorLimit)
    }

    @Test
    fun testDeserialization() {
        val data = ResourceReader.readResourceAsText("log_config.json")
        val cfg = Gson().fromJson(data, LogRemoteConfig::class.java)
        assertEquals(768, cfg.logMessageMaximumAllowedLength)
        assertEquals(50, cfg.logInfoLimit)
        assertEquals(200, cfg.logWarnLimit)
        assertEquals(500, cfg.logErrorLimit)
    }

    @Test
    fun testDeserializationEmptyObj() {
        val cfg = Gson().fromJson("{}", LogRemoteConfig::class.java)
        verifyDefaults(cfg)
    }

    private fun verifyDefaults(cfg: LogRemoteConfig) {
        assertNull(cfg.logMessageMaximumAllowedLength)
        assertNull(cfg.logInfoLimit)
        assertNull(cfg.logWarnLimit)
        assertNull(cfg.logErrorLimit)
    }
}
