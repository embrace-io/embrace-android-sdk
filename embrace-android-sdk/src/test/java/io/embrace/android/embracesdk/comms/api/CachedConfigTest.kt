package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.config.remote.RemoteConfig
import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class CachedConfigTest {

    @Test
    fun isValid() {
        assertFalse(CachedConfig(null, null).isValid())
        assertFalse(CachedConfig(RemoteConfig(), null).isValid())
        assertFalse(CachedConfig(null, "ba09cc").isValid())
        assertTrue(CachedConfig(RemoteConfig(), "ba09cc").isValid())
    }
}
