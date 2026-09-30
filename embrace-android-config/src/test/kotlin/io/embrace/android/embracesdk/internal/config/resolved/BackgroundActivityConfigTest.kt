package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class BackgroundActivityConfigTest {

    @Test
    fun `defaults match resolved default config`() {
        val defaults = EmbraceConfig().backgroundActivity
        val resolved = resolveConfig(InstrumentedConfigImpl, null, unreadBucket).backgroundActivity
        assertFalse(defaults.captureEnabled)
        assertEquals(defaults.captureEnabled, resolved.captureEnabled)
    }

    @Test
    fun `local flag used when remote threshold absent`() {
        assertTrue(resolve(local = true, remote = null).captureEnabled)
        assertFalse(resolve(local = false, remote = BackgroundActivityRemoteConfig(threshold = null)).captureEnabled)
    }

    @Test
    fun `remote threshold overrides local flag`() {
        assertTrue(resolve(local = false, remote = BackgroundActivityRemoteConfig(threshold = 100f)).captureEnabled)
        assertFalse(resolve(local = true, remote = BackgroundActivityRemoteConfig(threshold = 0f)).captureEnabled)
    }

    @Test
    fun `partial rollout reads bucket`() {
        val remote = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(threshold = 50f))
        assertTrue(resolveBackgroundActivity(InstrumentedConfigImpl, remote, lazy { 49f }).captureEnabled)
        assertFalse(resolveBackgroundActivity(InstrumentedConfigImpl, remote, lazy { 51f }).captureEnabled)
    }

    private fun resolve(local: Boolean, remote: BackgroundActivityRemoteConfig?) = resolveBackgroundActivity(
        FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(bgActivityCapture = local)),
        RemoteConfig(backgroundActivityConfig = remote),
        unreadBucket,
    )
}
