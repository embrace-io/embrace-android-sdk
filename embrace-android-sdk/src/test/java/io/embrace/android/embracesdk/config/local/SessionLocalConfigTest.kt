package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SessionLocalConfigTest {

    private val serializer = EmbraceSerializer()

    @Test
    fun testDefaults() {
        val cfg = SessionLocalConfig()
        verifyDefaults(cfg)
    }

    @Suppress("DEPRECATION")
    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("session_config.json")
        val obj = serializer.fromJson(json, SessionLocalConfig::class.java)
        assertEquals(120, obj.maxSessionSeconds)
        assertTrue(checkNotNull(obj.asyncEnd))
        assertTrue(checkNotNull(obj.sessionEnableErrorLogStrictMode))
        assertEquals(setOf("breadcrumbs"), obj.sessionComponents)
        assertEquals(setOf("crash"), obj.fullSessionEvents)
    }

    @Test
    fun testEmptyObject() {
        val obj = serializer.fromJson("{}", SessionLocalConfig::class.java)
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
