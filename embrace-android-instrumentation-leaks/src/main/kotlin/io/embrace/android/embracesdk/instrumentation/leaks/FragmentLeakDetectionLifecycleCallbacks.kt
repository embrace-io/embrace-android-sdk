package io.embrace.android.embracesdk.instrumentation.leaks

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import java.util.Collections
import java.util.WeakHashMap

/**
 * Tracks Fragment and Fragment View leaks by registering itself, recursively, on every [FragmentActivity]'s
 * [FragmentManager]. Only ever constructed from behind [createFragmentSupport]'s presence check.
 */
internal class FragmentLeakDetectionLifecycleCallbacks(
    private val leakDetector: LeakDetector,
    private val activeSessionIdsProvider: () -> SessionIdsSnapshot,
    private val errorHandler: ErrorHandler,
    private val webViewLeakDetectionEnabled: Boolean = false,
) : FragmentManager.FragmentLifecycleCallbacks(), FragmentSupport {

    private val pendingViews = FragmentViewMap()

    private val fragmentManagers: MutableSet<FragmentManager> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    private var closed = false

    override fun onActivityCreated(activity: Activity) {
        if (activity is FragmentActivity) {
            val fragmentManager = activity.supportFragmentManager
            synchronized(fragmentManagers) {
                if (!closed) {
                    fragmentManager.registerFragmentLifecycleCallbacks(this, true)
                    fragmentManagers.add(fragmentManager)
                }
            }
        }
    }

    override fun close() {
        val registered = synchronized(fragmentManagers) {
            closed = true
            fragmentManagers.toList().also { fragmentManagers.clear() }
        }
        registered.forEach { it.unregisterFragmentLifecycleCallbacks(this) }
    }

    override fun onFragmentAttached(fm: FragmentManager, f: Fragment, context: Context) {
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            leakDetector.trackOpened(f, TrackingSource.FRAGMENT)
        }
    }

    override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            leakDetector.trackClosed(
                f,
                TrackingSource.FRAGMENT,
                LeakContext(FRAGMENT_OBJECT_TYPE, activeSessionIdsProvider(), f.javaClass),
            )
        }
    }

    override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            pendingViews.opened(f, v)
            leakDetector.trackOpened(v, TrackingSource.FRAGMENT)
        }
    }

    // started rather than resumed: Fragments start before their Activity's onActivityResumed, so this opens their WebViews
    // ahead of the Activity's own scan, and owns them
    override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            if (webViewLeakDetectionEnabled) {
                WebViewLeakScanner.scan(f.view, errorHandler) { webView ->
                    leakDetector.trackOpened(webView, TrackingSource.FRAGMENT)
                }
            }
        }
    }

    override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            val view = pendingViews.closed(f) ?: return@guardSdkCode
            val sessionIds = activeSessionIdsProvider()
            leakDetector.trackClosed(view, TrackingSource.FRAGMENT, LeakContext(FRAGMENT_VIEW_OBJECT_TYPE, sessionIds, f.javaClass))

            if (webViewLeakDetectionEnabled) {
                WebViewLeakScanner.scan(view, errorHandler) { webView ->
                    leakDetector.trackClosed(
                        webView,
                        TrackingSource.FRAGMENT,
                        LeakContext(WebViewLeakScanner.WEBVIEW_OBJECT_TYPE, sessionIds, f.javaClass),
                    )
                }
            }
        }
    }

    private companion object {

        /**
         * Reported as [LeakContext.objectType].
         */
        const val FRAGMENT_OBJECT_TYPE = "fragment"

        /**
         * Reported as [LeakContext.objectType].
         */
        const val FRAGMENT_VIEW_OBJECT_TYPE = "fragment_view"
    }
}
