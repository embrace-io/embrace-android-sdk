package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeVersionChecker
import io.embrace.android.embracesdk.fakes.fakeAnrBehavior
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.fakeSdkModeBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.fakeDataSourceModule
import io.embrace.android.embracesdk.internal.capture.crumbs.EmbraceBreadcrumbService
import io.embrace.android.embracesdk.internal.capture.memory.EmbraceMemoryService
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupTraceEmitter
import io.embrace.android.embracesdk.internal.capture.webview.EmbraceWebViewService
import io.embrace.android.embracesdk.internal.config.local.AutomaticDataCaptureLocalConfig
import io.embrace.android.embracesdk.internal.config.local.LocalConfig
import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.worker.WorkerThreadModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DataCaptureServiceModuleImplTest {

    private val initModule = FakeInitModule()
    private val openTelemetryModule = initModule.openTelemetryModule
    private val coreModule = FakeCoreModule()

    @Test
    fun testDefaultImplementations() {
        val module = DataCaptureServiceModuleImpl(
            initModule,
            openTelemetryModule,
            coreModule,
            createEnabledBehavior(),
            WorkerThreadModuleImpl(initModule),
            FakeVersionChecker(true),
            fakeDataSourceModule()
        )

        assertTrue(module.memoryService is EmbraceMemoryService)
        assertTrue(module.webviewService is EmbraceWebViewService)
        assertTrue(module.breadcrumbService is EmbraceBreadcrumbService)
        assertTrue(module.appStartupDataCollector is AppStartupTraceEmitter)
        assertNotNull(module.pushNotificationService)
        assertNotNull(module.componentCallbackService)
        assertNotNull(module.startupService)
    }

    @Test
    fun testDisabledImplementations() {
        val module = DataCaptureServiceModuleImpl(
            initModule,
            openTelemetryModule,
            coreModule,
            createDisabledBehavior(),
            WorkerThreadModuleImpl(initModule),
            FakeVersionChecker(true),
            fakeDataSourceModule()
        )

        assertNull(module.memoryService)
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
