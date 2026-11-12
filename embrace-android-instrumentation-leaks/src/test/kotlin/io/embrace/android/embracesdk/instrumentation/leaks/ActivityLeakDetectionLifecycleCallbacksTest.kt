package io.embrace.android.embracesdk.instrumentation.leaks

import android.app.Activity
import android.webkit.WebView
import android.widget.FrameLayout
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityCreated
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityLifecycle
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class ActivityLeakDetectionLifecycleCallbacksTest {

    private val errorHandler = FakeErrorHandler()
    private val eventBus = EventBus(errorHandler)
    private val leakDetector = LeakDetector(clock = { 0L }, errorHandler = errorHandler)

    @Test
    fun `resuming an activity with a webview in its decor view tracks it as opened, when enabled`() {
        callbacks(webViewLeakDetectionEnabled = true)
        val activity = activityWithWebView()

        resume(activity)

        assertNotNull(
            "onActivityResumed should have opened a sentinel for the webview found in the decor view",
            leakDetector.trackClosed(activity.webView, OWNER, WEBVIEW_CONTEXT),
        )
    }

    @Test
    fun `resuming an activity with a webview does nothing when webview leak detection is disabled`() {
        callbacks(webViewLeakDetectionEnabled = false)
        val activity = activityWithWebView()

        resume(activity)

        assertNull(
            "no sentinel should have been opened when webview leak detection is disabled (the default)",
            leakDetector.trackClosed(activity.webView, OWNER, WEBVIEW_CONTEXT),
        )
    }

    @Test
    fun `destroying an activity with a webview closes it, when enabled`() {
        callbacks(webViewLeakDetectionEnabled = true)
        val activity = activityWithWebView()

        resume(activity)
        eventBus.emit(ActivityLifecycle.DESTROYED, activity)

        assertNull(
            "onActivityDestroyed should already have released the sentinel opened on resume",
            leakDetector.trackClosed(activity.webView, OWNER, WEBVIEW_CONTEXT),
        )
    }

    @Test
    fun `creating an activity attaches fragment tracking to it`() {
        val attached = mutableListOf<Activity>()
        val fragmentSupport = object : FragmentSupport {
            override fun onActivityCreated(activity: Activity) {
                attached.add(activity)
            }

            override fun close() = Unit
        }
        callbacks(webViewLeakDetectionEnabled = false, fragmentSupport = fragmentSupport)
        val activity = buildActivity(Activity::class.java).get()

        eventBus.emit(ActivityLifecycle.CREATED, ActivityCreated(activity, null))

        assertEquals(listOf(activity), attached)
    }

    @Test
    fun `activity events are not handled after close`() {
        callbacks(webViewLeakDetectionEnabled = true).close()
        val activity = activityWithWebView()

        resume(activity)

        assertNull(
            "no sentinel should have been opened once the callbacks are closed",
            leakDetector.trackClosed(activity.webView, OWNER, WEBVIEW_CONTEXT),
        )
    }

    private fun callbacks(
        webViewLeakDetectionEnabled: Boolean,
        fragmentSupport: FragmentSupport = NoOpFragmentSupport,
    ) = ActivityLeakDetectionLifecycleCallbacks(
        leakDetector,
        { SESSION_IDS },
        fragmentSupport,
        eventBus,
        errorHandler,
        webViewLeakDetectionEnabled,
    ).apply { subscribe() }

    private fun resume(activity: Activity) = eventBus.emit(ActivityLifecycle.RESUMED, activity)

    private fun activityWithWebView(): ActivityWithWebView {
        val activity = buildActivity(ActivityWithWebView::class.java).create().get()
        activity.webView = WebView(activity)
        activity.setContentView(FrameLayout(activity).apply { addView(activity.webView) })
        return activity
    }

    private companion object {
        val OWNER = TrackingSource.ACTIVITY
        val SESSION_IDS = SessionIdsSnapshot(userSessionId = "session-1", sessionPartId = "part-1")
        val WEBVIEW_CONTEXT = LeakContext(WebViewLeakScanner.WEBVIEW_OBJECT_TYPE, SESSION_IDS, WebView::class.java)
    }
}

/**
 * An [Activity] with a settable [webView] field, so tests can assert against the exact instance placed in the
 * decor view rather than having to search for it themselves.
 */
internal class ActivityWithWebView : Activity() {
    lateinit var webView: WebView
}
