package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createSdkModeBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.mockk.core.ValueClassSupport.boxedValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SdkModeBehaviorImplTest {

    // 100% enabled
    private val enabled = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFC000000" }

    // ~50% enabled
    private val halfEnabled = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFC800000" }

    // 0% enabled
    private val disabled = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFCFFFFFF" }

    @Test
    fun testDefaults() {
        with(
            createSdkModeBehavior(
                thresholdCheck = disabled
            )
        ) {
            assertFalse(isSdkDisabled())
        }
    }

    @Test
    fun testSdkEnabled() {
        // Device disabled
        assertEquals(100.0f, disabled.getNormalizedDeviceId())
        var behavior = createSdkModeBehavior(
            thresholdCheck = disabled,
            remoteCfg = RemoteConfig(threshold = 99)
        )
        assertTrue(behavior.isSdkDisabled())

        // SDK disabled
        assertEquals(0.0f, enabled.getNormalizedDeviceId())
        behavior = createSdkModeBehavior(
            thresholdCheck = enabled,
            remoteCfg = RemoteConfig(threshold = 0)
        )
        assertTrue(behavior.isSdkDisabled())

        // SDK enabled
        behavior = createSdkModeBehavior(
            thresholdCheck = enabled,
            remoteCfg = RemoteConfig(threshold = 100)
        )
        assertFalse(behavior.isSdkDisabled())

        // SDK 30% enabled
        assertEquals(50.000008f, halfEnabled.getNormalizedDeviceId())
        behavior = createSdkModeBehavior(
            thresholdCheck = halfEnabled,
            remoteCfg = RemoteConfig(threshold = 30)
        )
        assertTrue(behavior.isSdkDisabled())

        // SDK 51% enabled
        behavior = createSdkModeBehavior(
            thresholdCheck = halfEnabled,
            remoteCfg = RemoteConfig(threshold = 51)
        )
        assertFalse(behavior.isSdkDisabled())
    }
}
