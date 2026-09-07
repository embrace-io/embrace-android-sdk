package io.embrace.android.embracesdk.instrumentation.leaks

import android.app.Activity
import android.webkit.WebView
import android.widget.FrameLayout
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class ActivityLeakDetectionLifecycleCallbacksTest {

    private val leakDetector = LeakDetector(clock = { 0L })
    private val logger = FakeInternalLogger()

    @Test
    fun `resuming an activity with a webview in its decor view tracks it as opened, when enabled`() {
        val callbacks = callbacks(webViewLeakDetectionEnabled = true)
        val activity = activityWithWebView()

        callbacks.onActivityResumed(activity)

        assertNotNull(
            "onActivityResumed should have opened a sentinel for the webview found in the decor view",
            leakDetector.trackClosed(activity.webView, LeakContext(WebViewLeakScanner.WEBVIEW_OBJECT_TYPE, SESSION_IDS)),
        )
    }

    @Test
    fun `resuming an activity with a webview does nothing when webview leak detection is disabled`() {
        val callbacks = callbacks(webViewLeakDetectionEnabled = false)
        val activity = activityWithWebView()

        callbacks.onActivityResumed(activity)

        assertNull(
            "no sentinel should have been opened when webview leak detection is disabled (the default)",
            leakDetector.trackClosed(activity.webView, LeakContext(WebViewLeakScanner.WEBVIEW_OBJECT_TYPE, SESSION_IDS)),
        )
    }

    @Test
    fun `destroying an activity with a webview closes it, when enabled`() {
        val callbacks = callbacks(webViewLeakDetectionEnabled = true)
        val activity = activityWithWebView()

        callbacks.onActivityResumed(activity)
        callbacks.onActivityDestroyed(activity)

        assertNull(
            "onActivityDestroyed should already have released the sentinel opened on resume",
            leakDetector.trackClosed(activity.webView, LeakContext(WebViewLeakScanner.WEBVIEW_OBJECT_TYPE, SESSION_IDS)),
        )
    }

    private fun callbacks(webViewLeakDetectionEnabled: Boolean) = ActivityLeakDetectionLifecycleCallbacks(
        leakDetector,
        { SESSION_IDS },
        NoOpFragmentSupport,
        logger,
        webViewLeakDetectionEnabled,
    )

    private fun activityWithWebView(): ActivityWithWebView {
        val activity = buildActivity(ActivityWithWebView::class.java).create().get()
        activity.webView = WebView(activity)
        activity.setContentView(FrameLayout(activity).apply { addView(activity.webView) })
        return activity
    }

    private companion object {
        val SESSION_IDS = SessionIdsSnapshot(userSessionId = "session-1", sessionPartId = "part-1")
    }
}

/**
 * An [Activity] with a settable [webView] field, so tests can assert against the exact instance placed in the
 * decor view rather than having to search for it themselves.
 */
internal class ActivityWithWebView : Activity() {
    lateinit var webView: WebView
}
