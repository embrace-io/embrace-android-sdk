package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeFeatureModule
import io.embrace.android.embracesdk.fakes.FakeVersionChecker
import io.embrace.android.embracesdk.fakes.fakeAnrBehavior
import io.embrace.android.embracesdk.fakes.fakeSdkModeBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupTraceEmitter
import io.embrace.android.embracesdk.internal.capture.webview.EmbraceWebViewService
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DataCaptureServiceModuleImplTest {

    private val initModule = FakeInitModule()
    private val openTelemetryModule = initModule.openTelemetryModule

    @Test
    fun testDefaultImplementations() {
        val module = DataCaptureServiceModuleImpl(
            initModule,
            openTelemetryModule,
            createEnabledBehavior(),
            FakeWorkerThreadModule(),
            FakeVersionChecker(false),
            FakeFeatureModule()
        )

        assertTrue(module.webviewService is EmbraceWebViewService)
        assertNotNull(module.activityBreadcrumbTracker)
        assertTrue(module.appStartupDataCollector is AppStartupTraceEmitter)
        assertNotNull(module.pushNotificationService)
        assertNotNull(module.startupService)
    }

    private fun createEnabledBehavior(): FakeConfigService {
        return FakeConfigService(
            anrBehavior = fakeAnrBehavior { AnrRemoteConfig(pctStrictModeListenerEnabled = 100f) },
            sdkModeBehavior = fakeSdkModeBehavior(
                isDebug = true
            )
        )
    }
}
