package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class SessionRemoteConfigTest {

    @Test
    fun testDefaults() {
        val cfg = SessionRemoteConfig(false, false, null, null)
        assertFalse(checkNotNull(cfg.isEnabled))
        assertFalse(checkNotNull(cfg.endAsync))
        assertNull(cfg.sessionComponents)
        assertNull(cfg.fullSessionEvents)
    }
}
