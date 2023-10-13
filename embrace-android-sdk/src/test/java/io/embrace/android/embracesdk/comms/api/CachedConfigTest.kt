package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.config.remote.RemoteConfig
import org.junit.Assert
import org.junit.Test

internal class CachedConfigTest {

    @Test
    fun isValid() {
        Assert.assertFalse(CachedConfig(null, null).isValid())
        Assert.assertFalse(CachedConfig(RemoteConfig(), null).isValid())
        Assert.assertFalse(CachedConfig(null, "ba09cc").isValid())
        Assert.assertTrue(CachedConfig(RemoteConfig(), "ba09cc").isValid())
    }
}
