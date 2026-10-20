package io.embrace.android.embracesdk.instrumentation.leaks

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot

internal class ActivityLeakDetectionLifecycleCallbacks(
    private val leakDetector: LeakDetector,
    private val activeSessionIdsProvider: () -> SessionIdsSnapshot,
    private val fragmentSupport: FragmentSupport,
    private val errorHandler: ErrorHandler,
    private val webViewLeakDetectionEnabled: Boolean = false,
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        try {
            leakDetector.trackOpened(activity, TrackingSource.ACTIVITY)
        } catch (exc: Throwable) {
            errorHandler.trackSdkError(SdkErrorType.UiCallbackFail, exc)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                leakDetector.trackOpened(activity, TrackingSource.ACTIVITY)
            }
            fragmentSupport.onActivityCreated(activity)
        } catch (exc: Throwable) {
            errorHandler.trackSdkError(SdkErrorType.UiCallbackFail, exc)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        try {
            val sessionIds = activeSessionIdsProvider()
            leakDetector.trackClosed(activity, TrackingSource.ACTIVITY, LeakContext(OBJECT_TYPE, sessionIds, activity.javaClass))

            if (webViewLeakDetectionEnabled) {
                // WebViews inside Fragments are opened by the Fragment instrumentation first, so are not closed here
                WebViewLeakScanner.scan(activity.window?.decorView, errorHandler) { webView ->
                    leakDetector.trackClosed(
                        webView,
                        TrackingSource.ACTIVITY,
                        LeakContext(WebViewLeakScanner.WEBVIEW_OBJECT_TYPE, sessionIds, activity.javaClass),
                    )
                }
            }
        } catch (exc: Throwable) {
            errorHandler.trackSdkError(SdkErrorType.UiCallbackFail, exc)
        }
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) {
        try {
            if (webViewLeakDetectionEnabled) {
                WebViewLeakScanner.scan(activity.window?.decorView, errorHandler) { webView ->
                    leakDetector.trackOpened(webView, TrackingSource.ACTIVITY)
                }
            }
        } catch (exc: Throwable) {
            errorHandler.trackSdkError(SdkErrorType.UiCallbackFail, exc)
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
