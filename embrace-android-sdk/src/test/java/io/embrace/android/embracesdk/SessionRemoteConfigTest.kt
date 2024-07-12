package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class SessionRemoteConfigTest {

    @Suppress("DEPRECATION")
    @Test
    fun testDefaults() {
        val cfg = SessionRemoteConfig(false, null, null)
        assertFalse(checkNotNull(cfg.isEnabled))
        assertNull(cfg.sessionComponents)
        assertNull(cfg.fullSessionEvents)
    }
}
