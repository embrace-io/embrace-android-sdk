package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.AppExitInfoConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AeiConfigTest {

    @Test
    fun `defaults match resolved default config`() {
        val defaults = EmbraceConfig().aei
        val resolved = resolveConfig(InstrumentedConfigImpl, null, unreadBucket).aei
        with(defaults) {
            assertTrue(captureEnabled)
            assertEquals(10485760, traceMaxLimit)
            assertEquals(0, maxNum)
        }
        assertEquals(defaults.captureEnabled, resolved.captureEnabled)
        assertEquals(defaults.traceMaxLimit, resolved.traceMaxLimit)
        assertEquals(defaults.maxNum, resolved.maxNum)
    }

    @Test
    fun `remote values override defaults`() {
        val remote = AppExitInfoConfig(appExitInfoTracesLimit = 55209, aeiMaxNum = 3)
        with(resolve(local = true, remote = remote)) {
            assertEquals(55209, traceMaxLimit)
            assertEquals(3, maxNum)
        }
    }

    @Test
    fun `local flag used when remote pct absent`() {
        assertFalse(resolve(local = false, remote = null).captureEnabled)
        assertTrue(resolve(local = true, remote = AppExitInfoConfig(pctAeiCaptureEnabled = null)).captureEnabled)
    }

    @Test
    fun `remote pct overrides local flag`() {
        assertTrue(resolve(local = false, remote = AppExitInfoConfig(pctAeiCaptureEnabled = 100f)).captureEnabled)
        assertFalse(resolve(local = true, remote = AppExitInfoConfig(pctAeiCaptureEnabled = 0f)).captureEnabled)
    }

    @Test
    fun `partial rollout reads bucket`() {
        val remote = RemoteConfig(appExitInfoConfig = AppExitInfoConfig(pctAeiCaptureEnabled = 50f))
        assertTrue(resolveAei(InstrumentedConfigImpl, remote, lazy { 49f }).captureEnabled)
        assertFalse(resolveAei(InstrumentedConfigImpl, remote, lazy { 51f }).captureEnabled)
    }

    private fun resolve(local: Boolean, remote: AppExitInfoConfig?) = resolveAei(
        FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(aeiCapture = local)),
        RemoteConfig(appExitInfoConfig = remote),
        unreadBucket,
    )
}
