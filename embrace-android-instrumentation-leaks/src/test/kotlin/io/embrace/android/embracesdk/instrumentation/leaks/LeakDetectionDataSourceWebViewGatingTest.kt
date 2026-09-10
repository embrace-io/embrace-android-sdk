package io.embrace.android.embracesdk.instrumentation.leaks

import android.webkit.WebView
import android.widget.FrameLayout
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Covers whether [LeakDetectionDataSource] wires up WebView leak detection depending on
 * [io.embrace.android.embracesdk.internal.config.behavior.AutoDataCaptureBehavior.isWebViewLeakDetectionEnabled].
 * Kept separate from [LeakDetectionDataSourceTest] for the same reason as
 * [LeakDetectionDataSourceFragmentGatingTest] - each test here needs its own [LeakDetectionDataSource], built
 * with different behavior.
 */
@RunWith(RobolectricTestRunner::class)
internal class LeakDetectionDataSourceWebViewGatingTest {

    private lateinit var dataSource: LeakDetectionDataSource

    @After
    fun tearDown() {
        dataSource.onDataCaptureDisabled()
    }

    @Test
    fun `webview leak detection is not wired when disabled (the default)`() {
        dataSource = LeakDetectionDataSource(FakeInstrumentationArgs(RuntimeEnvironment.getApplication()))
        dataSource.onDataCaptureEnabled()

        val webView = resumeActivityWithWebView()

        assertNull(
            "webview tracking should be a no-op when the RemoteConfig flag is off",
            dataSource.leakDetector.trackClosed(webView, LeakContext(WebViewLeakScanner.WEBVIEW_OBJECT_TYPE, SESSION_IDS)),
        )
    }

    @Test
    fun `webview leak detection is wired when enabled`() {
        val args = FakeInstrumentationArgs(RuntimeEnvironment.getApplication())
        args.configService.autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(webViewLeakDetectionEnabled = true)
        dataSource = LeakDetectionDataSource(args)
        dataSource.onDataCaptureEnabled()

        val webView = resumeActivityWithWebView()

        assertNotNull(
            "resuming an activity with a webview should have opened a sentinel once webview leak detection is enabled",
            dataSource.leakDetector.trackClosed(webView, LeakContext(WebViewLeakScanner.WEBVIEW_OBJECT_TYPE, SESSION_IDS)),
        )
    }

    /**
     * Builds and resumes an [Activity] with a [WebView] as its content, dispatching through the real
     * [android.app.Application.ActivityLifecycleCallbacks] registered by [LeakDetectionDataSource.onDataCaptureEnabled].
     */
    private fun resumeActivityWithWebView(): WebView {
        val controller = Robolectric.buildActivity(android.app.Activity::class.java).create()
        val activity = controller.get()
        val webView = WebView(activity)
        activity.setContentView(FrameLayout(activity).apply { addView(webView) })
        controller.start().resume()
        return webView
    }

    private companion object {
        val SESSION_IDS = SessionIdsSnapshot(userSessionId = "session-1", sessionPartId = "part-1")
    }
}
