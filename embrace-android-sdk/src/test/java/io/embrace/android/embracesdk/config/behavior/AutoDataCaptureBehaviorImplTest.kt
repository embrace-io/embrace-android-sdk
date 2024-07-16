package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.EmbraceConfigServiceTest.Companion.createLocalConfig
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
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
        killSwitchConfig = KillSwitchRemoteConfig(sigHandlerDetection = false, jetpackCompose = false),
        dataConfig = DataRemoteConfig(pctThermalStatusEnabled = 0.0f)
    )

    @Test
    fun testDefaults() {
        with(fakeAutoDataCaptureBehavior()) {
            assertTrue(isMemoryServiceEnabled())
            assertTrue(isPowerSaveModeServiceEnabled())
            assertTrue(isNetworkConnectivityServiceEnabled())
            assertTrue(isAnrServiceEnabled())
            assertTrue(isUncaughtExceptionHandlerEnabled())
            assertFalse(isComposeOnClickEnabled())
            assertTrue(isSigHandlerDetectionEnabled())
            assertFalse(isNdkEnabled())
            assertTrue(isDiskUsageReportingEnabled())
            assertTrue(isThermalStatusCaptureEnabled())
        }
    }

    @Test
    fun testLocalOnly() {
        with(fakeAutoDataCaptureBehavior(localCfg = { local })) {
            assertFalse(isMemoryServiceEnabled())
            assertFalse(isPowerSaveModeServiceEnabled())
            assertFalse(isNetworkConnectivityServiceEnabled())
            assertFalse(isAnrServiceEnabled())
            assertFalse(isUncaughtExceptionHandlerEnabled())
            assertTrue(isComposeOnClickEnabled())
            assertTrue(isSigHandlerDetectionEnabled())
            assertTrue(isNdkEnabled())
            assertFalse(isDiskUsageReportingEnabled())
        }
    }

    @Test
    fun testLocalAndRemote() {
        with(fakeAutoDataCaptureBehavior(localCfg = { local }, remoteCfg = { remote })) {
            assertFalse(isSigHandlerDetectionEnabled())
            assertFalse(isComposeOnClickEnabled())
            assertFalse(isThermalStatusCaptureEnabled())
        }
    }

    @Test
    fun testJetpackCompose() {
        // Jetpack Compose is disabled by default
        with(fakeAutoDataCaptureBehavior()) {
            assertFalse(isComposeOnClickEnabled())
        }

        // Jetpack Compose is enabled locally, no remote config
        with(fakeAutoDataCaptureBehavior(localCfg = { local })) {
            assertTrue(isComposeOnClickEnabled())
        }

        // Jetpack Compose disabled remotely, overrides local: killswitch
        with(fakeAutoDataCaptureBehavior(localCfg = { local }, remoteCfg = { remote })) {
            assertFalse(isComposeOnClickEnabled())
        }

        val localComposeOff = createLocalConfig {
            SdkLocalConfig(
                composeConfig = ComposeLocalConfig(
                    false
                )
            )
        }

        val remoteComposeKillSwitchOff = RemoteConfig(
            killSwitchConfig = KillSwitchRemoteConfig(sigHandlerDetection = false, jetpackCompose = true)
        )

        // Jetpack Compose enabled remotely, but explicit disabled locally, remote ignored
        with(fakeAutoDataCaptureBehavior(localCfg = { localComposeOff }, remoteCfg = { remoteComposeKillSwitchOff })) {
            assertFalse(isComposeOnClickEnabled())
        }

        // Jetpack Compose enabled remotely, and explicit enabled locally
        with(fakeAutoDataCaptureBehavior(localCfg = { local }, remoteCfg = { remoteComposeKillSwitchOff })) {
            assertTrue(isComposeOnClickEnabled())
        }
    }
}
