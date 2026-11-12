package io.embrace.android.embracesdk.instrumentation.leaks

import android.app.Activity
import android.os.Build
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityCreated
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityLifecycle
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.utils.event.EventHandler

internal class ActivityLeakDetectionLifecycleCallbacks(
    private val leakDetector: LeakDetector,
    private val activeSessionIdsProvider: () -> SessionIdsSnapshot,
    private val fragmentSupport: FragmentSupport,
    private val eventBus: EventBus,
    private val errorHandler: ErrorHandler,
    private val webViewLeakDetectionEnabled: Boolean = false,
) : AutoCloseable {

    private val onPreCreated = EventHandler<Activity> { activity ->
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            leakDetector.trackOpened(activity, TrackingSource.ACTIVITY)
        }
    }

    private val onCreated = EventHandler<ActivityCreated> { event ->
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            val activity = event.activity
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                leakDetector.trackOpened(activity, TrackingSource.ACTIVITY)
            }
            fragmentSupport.onActivityCreated(activity)
        }
    }

    private val onDestroyed = EventHandler<Activity> { activity ->
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
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
        }
    }

    private val onResumed = EventHandler<Activity> { activity ->
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            if (webViewLeakDetectionEnabled) {
                WebViewLeakScanner.scan(activity.window?.decorView, errorHandler) { webView ->
                    leakDetector.trackOpened(webView, TrackingSource.ACTIVITY)
                }
            }
        }
    }

    /**
     * Starts tracking Activities, and the WebViews in them when enabled.
     */
    fun subscribe() {
        eventBus.addHandler(ActivityLifecycle.PRE_CREATED, onPreCreated)
        eventBus.addHandler(ActivityLifecycle.CREATED, onCreated)
        eventBus.addHandler(ActivityLifecycle.RESUMED, onResumed)
        eventBus.addHandler(ActivityLifecycle.DESTROYED, onDestroyed)
    }

    override fun close() {
        eventBus.removeHandler(ActivityLifecycle.PRE_CREATED, onPreCreated)
        eventBus.removeHandler(ActivityLifecycle.CREATED, onCreated)
        eventBus.removeHandler(ActivityLifecycle.RESUMED, onResumed)
        eventBus.removeHandler(ActivityLifecycle.DESTROYED, onDestroyed)
    }

    private companion object {

        /**
         * Reported as [LeakContext.objectType], naming what this instrumentation tracks.
         */
        const val OBJECT_TYPE = "activity"
    }
}
