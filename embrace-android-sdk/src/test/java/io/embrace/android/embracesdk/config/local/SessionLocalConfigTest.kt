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

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<SessionLocalConfig>("session_config.json")
        assertTrue(checkNotNull(obj.sessionEnableErrorLogStrictMode))
        assertEquals(setOf("breadcrumbs"), obj.sessionComponents)
        assertEquals(setOf("crash"), obj.fullSessionEvents)
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<SessionLocalConfig>()
        verifyDefaults(obj)
    }

    private fun verifyDefaults(cfg: SessionLocalConfig) {
        assertNull(cfg.sessionEnableErrorLogStrictMode)
        assertNull(cfg.sessionComponents)
        assertNull(cfg.fullSessionEvents)
    }
}
