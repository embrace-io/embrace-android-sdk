package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.FAKE_DEVICE_ID
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.createPersistenceBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PersistenceBehaviorImplTest {

    @Test
    fun `multi file persistence disabled by default`() {
        assertFalse(createPersistenceBehavior(remoteCfg = null).isMultiFilePersistenceEnabled())
    }

    @Test
    fun `multi file persistence uses local flag when remote absent`() {
        assertTrue(
            createBehavior(localMultiFilePersistenceEnabled = true, remote = null)
                .isMultiFilePersistenceEnabled(),
        )
    }

    @Test
    fun `multi file persistence falls back to local flag when remote pct null`() {
        assertTrue(
            createBehavior(
                localMultiFilePersistenceEnabled = true,
                remote = RemoteConfig(pctMultiFilePersistenceEnabled = null),
            ).isMultiFilePersistenceEnabled(),
        )
    }

    @Test
    fun `multi file persistence enabled when pct is 100`() {
        assertTrue(
            createBehavior(
                remote = RemoteConfig(pctMultiFilePersistenceEnabled = 100.0f),
            ).isMultiFilePersistenceEnabled(),
        )
    }

    @Test
    fun `multi file persistence remote pct of 0 overrides enabled local flag`() {
        assertFalse(
            createBehavior(
                localMultiFilePersistenceEnabled = true,
                remote = RemoteConfig(pctMultiFilePersistenceEnabled = 0.0f),
            ).isMultiFilePersistenceEnabled(),
        )
    }

    private fun createBehavior(
        localMultiFilePersistenceEnabled: Boolean = false,
        remote: RemoteConfig?,
    ) = PersistenceBehaviorImpl(
        thresholdCheck = BehaviorThresholdCheck { FAKE_DEVICE_ID },
        local = FakeInstrumentedConfig(
            enabledFeatures = FakeEnabledFeatureConfig(
                multiFilePersistence = localMultiFilePersistenceEnabled,
            ),
        ),
        remote = remote,
    )
}
