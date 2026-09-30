package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SdkModeConfigTest {

    @Test
    fun `defaults match resolved default config`() {
        val defaults = EmbraceConfig().sdkMode
        val resolved = resolveConfig(InstrumentedConfigImpl, null, unreadBucket).sdkMode
        assertFalse(defaults.sdkDisabled)
        assertEquals(defaults.sdkDisabled, resolved.sdkDisabled)
        assertFalse(resolveSdkMode(RemoteConfig(), unreadBucket).sdkDisabled)
    }

    @Test
    fun `full thresholds do not read bucket`() {
        assertFalse(resolveSdkMode(RemoteConfig(threshold = 100), unreadBucket).sdkDisabled)
        assertTrue(resolveSdkMode(RemoteConfig(threshold = 0), unreadBucket).sdkDisabled)
    }

    @Test
    fun `partial threshold reads bucket`() {
        assertTrue(resolveSdkMode(RemoteConfig(threshold = 99), lazy { 100f }).sdkDisabled)
        assertTrue(resolveSdkMode(RemoteConfig(threshold = 30), lazy { 50f }).sdkDisabled)
        assertFalse(resolveSdkMode(RemoteConfig(threshold = 51), lazy { 50f }).sdkDisabled)
    }
}
