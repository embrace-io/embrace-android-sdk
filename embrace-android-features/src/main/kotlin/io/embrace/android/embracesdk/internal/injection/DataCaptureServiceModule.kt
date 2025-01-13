package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.activity.UiLoadDataListener
import io.embrace.android.embracesdk.internal.capture.crumbs.ActivityBreadcrumbTracker
import io.embrace.android.embracesdk.internal.capture.crumbs.PushNotificationCaptureService
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.internal.capture.startup.StartupService
import io.embrace.android.embracesdk.internal.capture.startup.StartupTracker
import io.embrace.android.embracesdk.internal.capture.webview.WebViewService
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener

/**
 * This modules provides services that capture data from within an application. It could be argued
 * that a lot of classes could fit in this module, so to keep it small (<15 properties) it's best
 * to only include services whose main responsibility is just capturing data. It would be well
 * worth reassessing the grouping once this module grows larger.
 */
interface DataCaptureServiceModule {

    /**
     * Captures breadcrumbs
     */
    val activityBreadcrumbTracker: ActivityBreadcrumbTracker

    /**
     * Captures information from webviews
     */
    val webviewService: WebViewService

    /**
     * Captures push notifications
     */
    val pushNotificationService: PushNotificationCaptureService

    /**
     * Captures the startup time of the SDK
     */
    val startupService: StartupService

    /**
     * Collects data happening during app startup so that startup traces can be created from it
     */
    val appStartupDataCollector: AppStartupDataCollector

    /**
     * Listens for events in the app to pass along to [appStartupDataCollector]
     */
    val startupTracker: StartupTracker

    /**
     * Creates UI Load traces based data it collects
     */
    val uiLoadDataListener: UiLoadDataListener?

    /**
     * Listens for lifecycle events during the loading of Activities proxies them to [uiLoadDataListener]
     */
    val activityLoadEventEmitter: ActivityLifecycleListener?
}
