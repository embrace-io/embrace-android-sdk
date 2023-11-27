package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class SessionRemoteConfigTest {

    @Suppress("DEPRECATION")
    @Test
    fun testDefaults() {
        val cfg = SessionRemoteConfig(false, 100f, false, null, null)
        assertFalse(checkNotNull(cfg.isEnabled))
        assertEquals(100f, checkNotNull(cfg.pctStartMessageEnabled))
        assertFalse(checkNotNull(cfg.endAsync))
        assertNull(cfg.sessionComponents)
        assertNull(cfg.fullSessionEvents)
    }
}
