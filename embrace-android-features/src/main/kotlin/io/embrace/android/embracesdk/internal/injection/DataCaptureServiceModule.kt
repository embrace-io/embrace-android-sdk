package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.crumbs.ActivityBreadcrumbTracker
import io.embrace.android.embracesdk.internal.capture.crumbs.PushNotificationCaptureService
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.internal.capture.startup.StartupService
import io.embrace.android.embracesdk.internal.capture.startup.StartupTracker
import io.embrace.android.embracesdk.internal.capture.webview.WebViewService

/**
 * This modules provides services that capture data from within an application. It could be argued
 * that a lot of classes could fit in this module, so to keep it small (<15 properties) it's best
 * to only include services whose main responsibility is just capturing data. It would be well
 * worth reassessing the grouping once this module grows larger.
 */
public interface DataCaptureServiceModule {

    /**
     * Captures breadcrumbs
     */
    public val activityBreadcrumbTracker: ActivityBreadcrumbTracker

    /**
     * Captures information from webviews
     */
    public val webviewService: WebViewService

    /**
     * Captures push notifications
     */
    public val pushNotificationService: PushNotificationCaptureService

    /**
     * Captures the startup time of the SDK
     */
    public val startupService: StartupService

    public val startupTracker: StartupTracker

    public val appStartupDataCollector: AppStartupDataCollector
}
