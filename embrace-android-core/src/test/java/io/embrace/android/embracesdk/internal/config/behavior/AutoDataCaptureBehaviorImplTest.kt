package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createAutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.config.remote.DataRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.KillSwitchRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AutoDataCaptureBehaviorImplTest {

    private val remote = RemoteConfig(
        killSwitchConfig = KillSwitchRemoteConfig(
            sigHandlerDetection = false,
            jetpackCompose = false,
            v2StoragePct = 100f,
            useOkHttpPct = 100f
        ),
        dataConfig = DataRemoteConfig(pctThermalStatusEnabled = 0.0f)
    )

    @Test
    fun testDefaults() {
        with(createAutoDataCaptureBehavior()) {
            assertTrue(isMemoryWarningCaptureEnabled())
            assertTrue(isPowerSaveModeCaptureEnabled())
            assertTrue(isNetworkConnectivityCaptureEnabled())
            assertTrue(isAnrCaptureEnabled())
            assertTrue(isJvmCrashCaptureEnabled())
            assertFalse(isComposeClickCaptureEnabled())
            assertTrue(is3rdPartySigHandlerDetectionEnabled())
            assertFalse(isNativeCrashCaptureEnabled())
            assertTrue(isDiskUsageCaptureEnabled())
            assertTrue(isThermalStatusCaptureEnabled())
            assertTrue(isThermalStatusCaptureEnabled())
            assertTrue(isV2StorageEnabled())
        }
    }

    @Test
    fun testLocalAndRemote() {
        with(createAutoDataCaptureBehavior(remoteCfg = { remote })) {
            assertFalse(is3rdPartySigHandlerDetectionEnabled())
            assertFalse(isComposeClickCaptureEnabled())
            assertFalse(isThermalStatusCaptureEnabled())
            assertTrue(isV2StorageEnabled())
        }
    }

    @Test
    fun testJetpackCompose() {
        // Jetpack Compose is disabled by default
        with(createAutoDataCaptureBehavior()) {
            assertFalse(isComposeClickCaptureEnabled())
        }

        // Jetpack Compose disabled remotely
        with(createAutoDataCaptureBehavior(remoteCfg = { remote })) {
            assertFalse(isComposeClickCaptureEnabled())
        }
        val remoteComposeKillSwitchOff = RemoteConfig(
            killSwitchConfig = KillSwitchRemoteConfig(
                sigHandlerDetection = false,
                jetpackCompose = true
            )
        )

        // Jetpack Compose enabled remotely
        with(
            createAutoDataCaptureBehavior(
                remoteCfg = { remoteComposeKillSwitchOff }
            )
        ) {
            assertTrue(isComposeClickCaptureEnabled())
        }
    }
}
