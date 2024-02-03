package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.FakeWorkerThreadModule
import io.embrace.android.embracesdk.capture.crumbs.EmbraceBreadcrumbService
import io.embrace.android.embracesdk.capture.memory.EmbraceMemoryService
import io.embrace.android.embracesdk.capture.memory.NoOpMemoryService
import io.embrace.android.embracesdk.capture.powersave.EmbracePowerSaveModeService
import io.embrace.android.embracesdk.capture.powersave.NoOpPowerSaveModeService
import io.embrace.android.embracesdk.capture.thermalstate.EmbraceThermalStatusService
import io.embrace.android.embracesdk.capture.thermalstate.NoOpThermalStatusService
import io.embrace.android.embracesdk.capture.webview.EmbraceWebViewService
import io.embrace.android.embracesdk.config.local.AutomaticDataCaptureLocalConfig
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeVersionChecker
import io.embrace.android.embracesdk.fakes.fakeAnrBehavior
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.fakeSdkModeBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DataCaptureServiceModuleImplTest {

    private val coreModule = FakeCoreModule()
    private val systemServiceModule = FakeSystemServiceModule()

    @Test
    fun testDefaultImplementations() {
        val module = DataCaptureServiceModuleImpl(
            InitModuleImpl(),
            coreModule,
            systemServiceModule,
            createEnabledBehavior(),
            FakeWorkerThreadModule(),
            FakeVersionChecker(true)
        )

        assertTrue(module.memoryService is EmbraceMemoryService)
        assertTrue(module.powerSaveModeService is EmbracePowerSaveModeService)
        assertTrue(module.webviewService is EmbraceWebViewService)
        assertTrue(module.breadcrumbService is EmbraceBreadcrumbService)
        assertTrue(module.thermalStatusService is EmbraceThermalStatusService)
        assertNotNull(module.pushNotificationService)
        assertNotNull(module.componentCallbackService)
        assertNotNull(module.startupService)
    }

    @Test
    fun testOldVersionChecks() {
        val module = DataCaptureServiceModuleImpl(
            InitModuleImpl(),
            coreModule,
            systemServiceModule,
            FakeEssentialServiceModule(),
            FakeWorkerThreadModule(),
            FakeVersionChecker(false)
        )

        assertTrue(module.thermalStatusService is NoOpThermalStatusService)
    }

    @Test
    fun testDisabledImplementations() {
        val module = DataCaptureServiceModuleImpl(
            InitModuleImpl(),
            coreModule,
            systemServiceModule,
            createDisabledBehavior(),
            FakeWorkerThreadModule(),
            FakeVersionChecker(true)
        )

        assertTrue(module.memoryService is NoOpMemoryService)
        assertTrue(module.powerSaveModeService is NoOpPowerSaveModeService)
        assertTrue(module.thermalStatusService is EmbraceThermalStatusService)
    }

    private fun createEnabledBehavior(): FakeEssentialServiceModule {
        return FakeEssentialServiceModule(
            configService = FakeConfigService(
                anrBehavior = fakeAnrBehavior { AnrRemoteConfig(pctStrictModeListenerEnabled = 100f) },
                sdkModeBehavior = fakeSdkModeBehavior(
                    isDebug = true
                )
            )
        )
    }

    private fun createDisabledBehavior(): FakeEssentialServiceModule {
        val cfg = AutomaticDataCaptureLocalConfig(
            memoryServiceEnabled = false,
            powerSaveModeServiceEnabled = false,
            networkConnectivityServiceEnabled = false,
            anrServiceEnabled = false
        )
        val behavior = fakeAutoDataCaptureBehavior(localCfg = {
            LocalConfig(
                "",
                true,
                SdkLocalConfig(
                    automaticDataCaptureConfig = cfg
                )
            )
        })
        return FakeEssentialServiceModule(
            configService = FakeConfigService(
                autoDataCaptureBehavior = behavior,
                sdkModeBehavior = fakeSdkModeBehavior(
                    remoteCfg = { RemoteConfig(pctBetaFeaturesEnabled = 0.0f) }
                ),
            )
        )
    }
}
