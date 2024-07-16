package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.internal.config.local.SessionLocalConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(setOf("breadcrumbs"), obj.sessionComponents)
        assertEquals(setOf("crash"), obj.fullSessionEvents)
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<SessionLocalConfig>()
        verifyDefaults(obj)
    }

    private fun verifyDefaults(cfg: SessionLocalConfig) {
        assertNull(cfg.sessionComponents)
        assertNull(cfg.fullSessionEvents)
    }
}
