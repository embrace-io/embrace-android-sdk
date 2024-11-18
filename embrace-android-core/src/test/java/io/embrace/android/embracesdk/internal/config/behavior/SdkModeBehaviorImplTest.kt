package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createSdkModeBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SdkModeBehaviorImplTest {

    // 100% enabled
    private val enabled = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFC000000" }

    // ~50% enabled
    private val halfEnabled = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFC888888" }

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
        // SDK disabled
        var behavior = createSdkModeBehavior(
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

        // SDK 30% enabled with default offset
        behavior = createSdkModeBehavior(
            thresholdCheck = halfEnabled,
            remoteCfg = RemoteConfig(threshold = 30)
        )
        assertTrue(behavior.isSdkDisabled())

        // SDK 30% enabled with non-default offset
        behavior = createSdkModeBehavior(
            thresholdCheck = halfEnabled,
            remoteCfg = RemoteConfig(threshold = 30, offset = 25)
        )
        assertFalse(behavior.isSdkDisabled())
    }
}
