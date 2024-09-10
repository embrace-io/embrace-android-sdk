package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createAutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.config.local.AppLocalConfig
import io.embrace.android.embracesdk.internal.config.local.AutomaticDataCaptureLocalConfig
import io.embrace.android.embracesdk.internal.config.local.ComposeLocalConfig
import io.embrace.android.embracesdk.internal.config.local.CrashHandlerLocalConfig
import io.embrace.android.embracesdk.internal.config.local.LocalConfig
import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.internal.config.remote.DataRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.KillSwitchRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AutoDataCaptureBehaviorImplTest {

    private val local = LocalConfig(
        appId = "",
        ndkEnabled = true,
        sdkConfig = SdkLocalConfig(
            automaticDataCaptureConfig = AutomaticDataCaptureLocalConfig(
                memoryServiceEnabled = false,
                powerSaveModeServiceEnabled = false,
                networkConnectivityServiceEnabled = false,
                anrServiceEnabled = false
            ),
            crashHandler = CrashHandlerLocalConfig(false),
            composeConfig = ComposeLocalConfig(true),
            app = AppLocalConfig(reportDiskUsage = false)
        ),
    )

    private val remote = RemoteConfig(
        killSwitchConfig = KillSwitchRemoteConfig(
            sigHandlerDetection = false,
            jetpackCompose = false
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
        }
    }

    @Test
    fun testLocalOnly() {
        with(createAutoDataCaptureBehavior(localCfg = { local })) {
            assertFalse(isMemoryWarningCaptureEnabled())
            assertFalse(isPowerSaveModeCaptureEnabled())
            assertFalse(isNetworkConnectivityCaptureEnabled())
            assertFalse(isAnrCaptureEnabled())
            assertFalse(isJvmCrashCaptureEnabled())
            assertTrue(isComposeClickCaptureEnabled())
            assertTrue(is3rdPartySigHandlerDetectionEnabled())
            assertTrue(isNativeCrashCaptureEnabled())
            assertFalse(isDiskUsageCaptureEnabled())
        }
    }

    @Test
    fun testLocalAndRemote() {
        with(createAutoDataCaptureBehavior(localCfg = { local }, remoteCfg = { remote })) {
            assertFalse(is3rdPartySigHandlerDetectionEnabled())
            assertFalse(isComposeClickCaptureEnabled())
            assertFalse(isThermalStatusCaptureEnabled())
        }
    }

    @Test
    fun testJetpackCompose() {
        // Jetpack Compose is disabled by default
        with(createAutoDataCaptureBehavior()) {
            assertFalse(isComposeClickCaptureEnabled())
        }

        // Jetpack Compose is enabled locally, no remote config
        with(createAutoDataCaptureBehavior(localCfg = { local })) {
            assertTrue(isComposeClickCaptureEnabled())
        }

        // Jetpack Compose disabled remotely, overrides local: killswitch
        with(createAutoDataCaptureBehavior(localCfg = { local }, remoteCfg = { remote })) {
            assertFalse(isComposeClickCaptureEnabled())
        }

        val localComposeOff = LocalConfig(
            "abcde",
            false,
            SdkLocalConfig(
                composeConfig = ComposeLocalConfig(
                    false
                )
            )
        )

        val remoteComposeKillSwitchOff = RemoteConfig(
            killSwitchConfig = KillSwitchRemoteConfig(
                sigHandlerDetection = false,
                jetpackCompose = true
            )
        )

        // Jetpack Compose enabled remotely, but explicit disabled locally, remote ignored
        with(
            createAutoDataCaptureBehavior(
                localCfg = { localComposeOff },
                remoteCfg = { remoteComposeKillSwitchOff }
            )
        ) {
            assertFalse(isComposeClickCaptureEnabled())
        }

        // Jetpack Compose enabled remotely, and explicit enabled locally
        with(
            createAutoDataCaptureBehavior(
                localCfg = { local },
                remoteCfg = { remoteComposeKillSwitchOff }
            )
        ) {
            assertTrue(isComposeClickCaptureEnabled())
        }
    }
}
