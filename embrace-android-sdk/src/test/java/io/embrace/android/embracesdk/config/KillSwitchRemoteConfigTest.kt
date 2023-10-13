package io.embrace.android.embracesdk.config

import io.embrace.android.embracesdk.config.remote.KillSwitchRemoteConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class KillSwitchRemoteConfigTest {

    @Test
    fun isSigHandlerDetectionEnabled() {
        assertFalse(checkNotNull(KillSwitchRemoteConfig(false).sigHandlerDetection))
    }

    @Test
    fun ofDefault() {
        assertNull(KillSwitchRemoteConfig().sigHandlerDetection)
    }
}
