package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeFeatureModule
import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.fakes.FakeWebViewService
import io.embrace.android.embracesdk.internal.capture.crumbs.ActivityBreadcrumbTracker
import io.embrace.android.embracesdk.internal.capture.crumbs.PushNotificationCaptureService
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.internal.capture.startup.StartupService
import io.embrace.android.embracesdk.internal.capture.startup.StartupTracker
import io.embrace.android.embracesdk.internal.capture.webview.WebViewService
import io.embrace.android.embracesdk.internal.injection.DataCaptureServiceModule
import io.mockk.mockk

internal class FakeDataCaptureServiceModule(
    override val webviewService: WebViewService = FakeWebViewService()
) : DataCaptureServiceModule {

    override val activityBreadcrumbTracker: ActivityBreadcrumbTracker =
        ActivityBreadcrumbTracker(FakeConfigService(), FakeFeatureModule().viewDataSource::dataSource)

    override val pushNotificationService: PushNotificationCaptureService = mockk(relaxed = true)

    override val startupService: StartupService = FakeStartupService()

    override val startupTracker: StartupTracker = mockk(relaxed = true)

    override val appStartupDataCollector: AppStartupDataCollector = mockk(relaxed = true)
}
