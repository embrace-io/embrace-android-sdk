package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.FakeBreadcrumbService
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.capture.crumbs.PushNotificationCaptureService
import io.embrace.android.embracesdk.capture.memory.ComponentCallbackService
import io.embrace.android.embracesdk.capture.memory.MemoryService
import io.embrace.android.embracesdk.capture.startup.AppStartupTraceEmitter
import io.embrace.android.embracesdk.capture.startup.StartupService
import io.embrace.android.embracesdk.capture.startup.StartupTracker
import io.embrace.android.embracesdk.capture.thermalstate.NoOpThermalStatusService
import io.embrace.android.embracesdk.capture.thermalstate.ThermalStatusService
import io.embrace.android.embracesdk.capture.webview.EmbraceWebViewService
import io.embrace.android.embracesdk.capture.webview.WebViewService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeMemoryService
import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.injection.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.EmbLoggerImpl

internal class FakeDataCaptureServiceModule(
    override val thermalStatusService: ThermalStatusService = NoOpThermalStatusService(),
    override val memoryService: MemoryService = FakeMemoryService(),
    override val breadcrumbService: BreadcrumbService = FakeBreadcrumbService(),
    override val webviewService: WebViewService =
        EmbraceWebViewService(FakeConfigService(), EmbraceSerializer(), EmbLoggerImpl()),
) : DataCaptureServiceModule {

    override val pushNotificationService: PushNotificationCaptureService
        get() = TODO("Not yet implemented")

    override val componentCallbackService: ComponentCallbackService
        get() = TODO("Not yet implemented")

    override val startupService: StartupService = FakeStartupService()

    override val startupTracker: StartupTracker
        get() = TODO("Not yet implemented")

    override val appStartupTraceEmitter: AppStartupTraceEmitter
        get() = TODO("Not yet implemented")
}
