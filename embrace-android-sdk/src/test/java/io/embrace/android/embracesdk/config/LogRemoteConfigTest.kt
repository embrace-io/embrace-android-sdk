package io.embrace.android.embracesdk.config

import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.internal.config.remote.LogRemoteConfig
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
        val cfg = deserializeJsonFromResource<LogRemoteConfig>("log_config.json")
        assertEquals(768, cfg.logMessageMaximumAllowedLength)
        assertEquals(50, cfg.logInfoLimit)
        assertEquals(200, cfg.logWarnLimit)
        assertEquals(500, cfg.logErrorLimit)
    }

    @Test
    fun testDeserializationEmptyObj() {
        val cfg = deserializeEmptyJsonString<LogRemoteConfig>()
        verifyDefaults(cfg)
    }

    private fun verifyDefaults(cfg: LogRemoteConfig) {
        assertNull(cfg.logMessageMaximumAllowedLength)
        assertNull(cfg.logInfoLimit)
        assertNull(cfg.logWarnLimit)
        assertNull(cfg.logErrorLimit)
    }
}
