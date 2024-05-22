package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.FakeBreadcrumbService
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.capture.crumbs.PushNotificationCaptureService
import io.embrace.android.embracesdk.capture.memory.ComponentCallbackService
import io.embrace.android.embracesdk.capture.memory.MemoryService
import io.embrace.android.embracesdk.capture.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.capture.startup.StartupService
import io.embrace.android.embracesdk.capture.startup.StartupTracker
import io.embrace.android.embracesdk.capture.webview.EmbraceWebViewService
import io.embrace.android.embracesdk.capture.webview.WebViewService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeMemoryService
import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.injection.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.mockk.mockk

internal class FakeDataCaptureServiceModule(
    override val memoryService: MemoryService = FakeMemoryService(),
    override val breadcrumbService: BreadcrumbService = FakeBreadcrumbService(),
    override val webviewService: WebViewService = EmbraceWebViewService(
        FakeConfigService(),
        EmbraceSerializer(),
        EmbLoggerImpl(),
    ) {
        fakeDataSourceModule()
    }
) : DataCaptureServiceModule {

    override val pushNotificationService: PushNotificationCaptureService = mockk(relaxed = true)

    override val componentCallbackService: ComponentCallbackService = mockk(relaxed = true)

    override val startupService: StartupService = FakeStartupService()

    override val startupTracker: StartupTracker = mockk(relaxed = true)

    override val appStartupDataCollector: AppStartupDataCollector = mockk(relaxed = true)
}
