package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SessionLocalConfigTest {

    @Test
    fun testDefaults() {
        val cfg = SessionLocalConfig()
        verifyDefaults(cfg)
    }

    @Suppress("DEPRECATION")
    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<SessionLocalConfig>("session_config.json")
        assertEquals(120, obj.maxSessionSeconds)
        assertTrue(checkNotNull(obj.asyncEnd))
        assertTrue(checkNotNull(obj.sessionEnableErrorLogStrictMode))
        assertEquals(setOf("breadcrumbs"), obj.sessionComponents)
        assertEquals(setOf("crash"), obj.fullSessionEvents)
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<SessionLocalConfig>()
        verifyDefaults(obj)
    }

    @Suppress("DEPRECATION")
    private fun verifyDefaults(cfg: SessionLocalConfig) {
        assertNull(cfg.maxSessionSeconds)
        assertNull(cfg.asyncEnd)
        assertNull(cfg.sessionEnableErrorLogStrictMode)
        assertNull(cfg.sessionComponents)
        assertNull(cfg.fullSessionEvents)
    }
}
