package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeVersionChecker
import io.embrace.android.embracesdk.fakes.fakeAnrBehavior
import io.embrace.android.embracesdk.fakes.fakeSdkModeBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.fakes.injection.fakeDataSourceModule
import io.embrace.android.embracesdk.internal.capture.crumbs.ActivityBreadcrumbTracker
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupTraceEmitter
import io.embrace.android.embracesdk.internal.capture.webview.EmbraceWebViewService
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.internal.injection.DataCaptureServiceModuleImpl
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
            fakeDataSourceModule()
        )

        assertTrue(module.webviewService is EmbraceWebViewService)
        assertTrue(module.activityBreadcrumbTracker is ActivityBreadcrumbTracker)
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
