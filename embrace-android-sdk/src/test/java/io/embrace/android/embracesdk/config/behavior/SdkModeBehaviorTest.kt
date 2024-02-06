package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.fakeSdkModeBehavior
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SdkModeBehaviorTest {

    private val local = LocalConfig(
        "",
        false,
        SdkLocalConfig(
            betaFeaturesEnabled = true
        )
    )

    // 100% enabled
    private val enabled = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFC000000" }

    // ~50% enabled
    private val halfEnabled = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFC888888" }

    // 0% enabled
    private val disabled = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFCFFFFFF" }

    @Test
    fun testDefaults() {
        with(
            fakeSdkModeBehavior(
                thresholdCheck = disabled
            )
        ) {
            assertFalse(isBetaFeaturesEnabled())
            assertFalse(isSdkDisabled())
        }
    }

    @Test
    fun testLocalOnly() {
        with(
            fakeSdkModeBehavior(
                thresholdCheck = enabled,
                localCfg = { local }
            )
        ) {
            assertTrue(isBetaFeaturesEnabled())
        }
    }

    @Test
    fun testBetaFeaturesEnabled() {
        var behavior = fakeSdkModeBehavior(
            thresholdCheck = enabled
        )
        assertTrue(behavior.isBetaFeaturesEnabled())

        behavior = fakeSdkModeBehavior(
            thresholdCheck = disabled
        )
        assertFalse(behavior.isBetaFeaturesEnabled())

        behavior = fakeSdkModeBehavior(
            thresholdCheck = enabled,
            localCfg = { LocalConfig("", false, SdkLocalConfig(betaFeaturesEnabled = false)) }
        )
        assertFalse(behavior.isBetaFeaturesEnabled())

        behavior =
            fakeSdkModeBehavior(
                thresholdCheck = enabled,
                localCfg = { local },
                remoteCfg = { RemoteConfig(pctBetaFeaturesEnabled = 100f) }
            )
        assertTrue(behavior.isBetaFeaturesEnabled())

        behavior =
            fakeSdkModeBehavior(
                thresholdCheck = disabled,
                localCfg = { local },
                remoteCfg = { RemoteConfig(pctBetaFeaturesEnabled = 0f) }
            )
        assertFalse(behavior.isBetaFeaturesEnabled())
    }

    @Test
    fun testMetadataDebug() {
        val behaviorNotDebug =
            fakeSdkModeBehavior(
                isDebug = false,
                thresholdCheck = disabled
            )
        assertFalse(behaviorNotDebug.isBetaFeaturesEnabled())

        val behaviorDebug =
            fakeSdkModeBehavior(
                isDebug = true,
                thresholdCheck = disabled
            )
        assertTrue(behaviorDebug.isBetaFeaturesEnabled())
    }

    @Test
    fun testSdkEnabled() {
        // SDK disabled
        var behavior = fakeSdkModeBehavior(
            thresholdCheck = enabled,
            remoteCfg = { RemoteConfig(threshold = 0) }
        )
        assertTrue(behavior.isSdkDisabled())

        // SDK enabled
        behavior = fakeSdkModeBehavior(
            thresholdCheck = enabled,
            remoteCfg = { RemoteConfig(threshold = 100) }
        )
        assertFalse(behavior.isSdkDisabled())

        // SDK 30% enabled with default offset
        behavior = fakeSdkModeBehavior(
            thresholdCheck = halfEnabled,
            remoteCfg = { RemoteConfig(threshold = 30) }
        )
        assertTrue(behavior.isSdkDisabled())

        // SDK 30% enabled with non-default offset
        behavior = fakeSdkModeBehavior(
            thresholdCheck = halfEnabled,
            remoteCfg = { RemoteConfig(threshold = 30, offset = 25) }
        )
        assertFalse(behavior.isSdkDisabled())
    }
}
