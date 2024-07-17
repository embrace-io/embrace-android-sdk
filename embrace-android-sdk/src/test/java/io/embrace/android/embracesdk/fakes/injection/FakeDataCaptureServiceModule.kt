package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.FakeBreadcrumbService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.internal.capture.crumbs.PushNotificationCaptureService
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.internal.capture.startup.StartupService
import io.embrace.android.embracesdk.internal.capture.startup.StartupTracker
import io.embrace.android.embracesdk.internal.capture.webview.EmbraceWebViewService
import io.embrace.android.embracesdk.internal.capture.webview.WebViewService
import io.embrace.android.embracesdk.internal.injection.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.mockk.mockk

internal class FakeDataCaptureServiceModule(
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

    override val startupService: StartupService = FakeStartupService()

    override val startupTracker: StartupTracker = mockk(relaxed = true)

    override val appStartupDataCollector: AppStartupDataCollector = mockk(relaxed = true)
}
