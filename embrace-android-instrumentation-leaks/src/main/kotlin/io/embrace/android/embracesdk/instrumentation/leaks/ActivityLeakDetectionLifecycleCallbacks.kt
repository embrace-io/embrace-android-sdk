package io.embrace.android.embracesdk.instrumentation.leaks

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot

internal class ActivityLeakDetectionLifecycleCallbacks(
    private val leakDetector: LeakDetector,
    private val activeSessionIdsProvider: () -> SessionIdsSnapshot,
    private val fragmentSupport: FragmentSupport,
    private val logger: InternalLogger,
    private val webViewLeakDetectionEnabled: Boolean = false,
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        leakDetector.trackOpened(activity, TrackingSource.ACTIVITY)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            leakDetector.trackOpened(activity, TrackingSource.ACTIVITY)
        }
        fragmentSupport.onActivityCreated(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        val sessionIds = activeSessionIdsProvider()
        leakDetector.trackClosed(activity, TrackingSource.ACTIVITY, LeakContext(OBJECT_TYPE, sessionIds, activity.javaClass))

        if (webViewLeakDetectionEnabled) {
            // WebViews inside Fragments are opened by the Fragment instrumentation first, so are not closed here
            WebViewLeakScanner.scan(activity.window?.decorView, logger) { webView ->
                leakDetector.trackClosed(
                    webView,
                    TrackingSource.ACTIVITY,
                    LeakContext(WebViewLeakScanner.WEBVIEW_OBJECT_TYPE, sessionIds, activity.javaClass),
                )
            }
        }
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) {
        if (webViewLeakDetectionEnabled) {
            WebViewLeakScanner.scan(activity.window?.decorView, logger) { webView ->
                leakDetector.trackOpened(webView, TrackingSource.ACTIVITY)
            }
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit

    private companion object {

        /**
         * Reported as [LeakContext.objectType], naming what this instrumentation tracks.
         */
        const val OBJECT_TYPE = "activity"
    }
}
