package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PersistenceConfigTest {

    @Test
    fun `defaults match resolved default local config`() {
        val defaults = EmbraceConfig().persistence
        assertFalse(defaults.multiFileEnabled)
        assertEquals(
            defaults.multiFileEnabled,
            resolveConfig(InstrumentedConfigImpl, null, unreadBucket).persistence.multiFileEnabled,
        )
    }

    @Test
    fun `local flag used when remote pct absent`() {
        assertTrue(resolve(local = true, remote = null).multiFileEnabled)
        assertTrue(resolve(local = true, remote = RemoteConfig(pctMultiFilePersistenceEnabled = null)).multiFileEnabled)
    }

    @Test
    fun `remote pct overrides local flag`() {
        assertTrue(resolve(local = false, remote = RemoteConfig(pctMultiFilePersistenceEnabled = 100f)).multiFileEnabled)
        assertFalse(resolve(local = true, remote = RemoteConfig(pctMultiFilePersistenceEnabled = 0f)).multiFileEnabled)
    }

    @Test
    fun `partial rollout reads bucket`() {
        val remote = RemoteConfig(pctMultiFilePersistenceEnabled = 50f)
        assertTrue(resolvePersistence(InstrumentedConfigImpl, remote, lazy { 49f }).multiFileEnabled)
        assertFalse(resolvePersistence(InstrumentedConfigImpl, remote, lazy { 51f }).multiFileEnabled)
    }

    private fun resolve(local: Boolean, remote: RemoteConfig?) = resolvePersistence(
        FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(multiFilePersistence = local)),
        remote,
        unreadBucket,
    )
}
