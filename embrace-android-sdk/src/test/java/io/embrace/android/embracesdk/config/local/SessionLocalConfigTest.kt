package io.embrace.android.embracesdk.config.local

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
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
        val json = ResourceReader.readResourceAsText("session_config.json")
        val obj = Gson().fromJson(json, SessionLocalConfig::class.java)
        assertEquals(120, obj.maxSessionSeconds)
        assertTrue(checkNotNull(obj.asyncEnd))
        assertTrue(checkNotNull(obj.sessionEnableErrorLogStrictMode))
        assertEquals(setOf("breadcrumbs"), obj.sessionComponents)
        assertEquals(setOf("crash"), obj.fullSessionEvents)
    }

    @Test
    fun testEmptyObject() {
        val obj = Gson().fromJson("{}", SessionLocalConfig::class.java)
        verifyDefaults(obj)
    }

    private fun verifyDefaults(cfg: SessionLocalConfig) {
        assertNull(cfg.maxSessionSeconds)
        assertNull(cfg.asyncEnd)
        assertNull(cfg.sessionEnableErrorLogStrictMode)
        assertNull(cfg.sessionComponents)
        assertNull(cfg.fullSessionEvents)
    }
}
