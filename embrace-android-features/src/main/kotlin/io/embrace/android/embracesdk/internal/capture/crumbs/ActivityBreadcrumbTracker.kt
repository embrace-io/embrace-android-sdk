package io.embrace.android.embracesdk.internal.capture.crumbs

import android.app.Activity
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Handles the logging of breadcrumbs.
 *
 * Breadcrumbs record a user's journey through the app and are split into:
 *
 *  * View breadcrumbs: Each time the user changes view in the app
 *  * Tap breadcrumbs: Each time the user taps a UI element in the app
 *  * Custom breadcrumbs: User-defined interactions within the app
 *
 * Breadcrumbs are limited at query-time by default to 100 per session, but this can be overridden
 * in server-side configuration. They are stored in an unbounded queue.
 */
public class ActivityBreadcrumbTracker(
    private val configService: ConfigService,
    private val viewDataSourceProvider: Provider<ViewDataSource?>
) : ActivityLifecycleListener {

    public fun logView(screen: String?) {
        viewDataSourceProvider()?.changeView(screen)
    }

    override fun onActivityStarted(activity: Activity) {
        if (configService.breadcrumbBehavior.isAutomaticActivityCaptureEnabled()) {
            logView(activity.javaClass.name)
        }
    }

    /**
     * Close all open fragments when the activity closes
     */
    override fun onActivityStopped(activity: Activity) {
        if (configService.breadcrumbBehavior.isAutomaticActivityCaptureEnabled()) {
            viewDataSourceProvider()?.onViewClose()
        }
    }
}
