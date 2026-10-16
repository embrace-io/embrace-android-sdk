package io.embrace.android.embracesdk.instrumentation.leaks

import android.R.id.content
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController

/**
 * Covers which instrumentation a leaked WebView is reported under when both the Activity and Fragment instrumentation can
 * see it: whichever opened it first owns it, and Fragments open theirs before the Activity does. Every test leaks the
 * WebView by holding it here, and drives real collections until the leak is confirmed.
 */
@RunWith(RobolectricTestRunner::class)
internal class WebViewLeakAttributionTest {

    private lateinit var dataSource: LeakDetectionDataSource

    @Before
    fun setUp() {
        val args = FakeInstrumentationArgs(RuntimeEnvironment.getApplication())
        args.configService.config = EmbraceConfig(autoDataCapture = {
            AutoDataCaptureConfig(fragmentLeakDetectionEnabled = { true }, webViewLeakDetectionEnabled = { true })
        })
        dataSource = LeakDetectionDataSource(args)
        dataSource.onDataCaptureEnabled()
    }

    @After
    fun tearDown() {
        dataSource.leakDetector.stop()
    }

    @Test
    fun `a webview nested in a fragment is reported under the fragment when its activity is destroyed`() {
        val fragment = WebViewFragment()
        val controller = startWith(fragment)

        controller.pause().stop().destroy()

        val context = awaitSuspect(dataSource.leakDetector, WebViewLeakScanner.WEBVIEW_OBJECT_TYPE)
        assertEquals(WebViewFragment::class.java, context.reportedClass)
    }

    @Test
    fun `a webview nested in a fragment is reported under the fragment when only the fragment is removed`() {
        val fragment = WebViewFragment()
        val controller = startWith(fragment)

        controller.get().supportFragmentManager.beginTransaction().remove(fragment).commitNow()

        val context = awaitSuspect(dataSource.leakDetector, WebViewLeakScanner.WEBVIEW_OBJECT_TYPE)
        assertEquals(WebViewFragment::class.java, context.reportedClass)
    }

    @Test
    fun `a webview that is a fragment's root view is reported as that fragment's view when its activity is destroyed`() {
        val fragment = RootWebViewFragment()
        val controller = startWith(fragment)

        controller.pause().stop().destroy()

        val context = awaitSuspect(dataSource.leakDetector, "fragment_view")
        assertEquals(RootWebViewFragment::class.java, context.reportedClass)
    }

    @Test
    fun `a webview hosted directly by an activity is reported under the activity`() {
        val controller = buildActivity(ActivityWithWebView::class.java).create()
        val activity = controller.get()
        activity.webView = WebView(activity)
        activity.setContentView(FrameLayout(activity).apply { addView(activity.webView) })
        controller.start().resume().visible()

        controller.pause().stop().destroy()

        val context = awaitSuspect(dataSource.leakDetector, WebViewLeakScanner.WEBVIEW_OBJECT_TYPE)
        assertEquals(ActivityWithWebView::class.java, context.reportedClass)
    }

    /**
     * Adds [fragment] before its Activity starts - the case where the Activity's own WebView scan could otherwise reach
     * the Fragment's WebViews first.
     */
    private fun startWith(fragment: Fragment): ActivityController<FragmentActivity> {
        val controller = buildActivity(FragmentActivity::class.java).create()
        controller.get().supportFragmentManager.beginTransaction().add(content, fragment).commitNow()
        return controller.start().postCreate(null).resume().visible()
    }
}

/**
 * A [Fragment] whose root view is itself a [WebView]. Must be a top-level, non-private class - same requirement as
 * [ViewFragment].
 */
internal class RootWebViewFragment : Fragment() {
    lateinit var webView: WebView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        webView = WebView(requireContext())
        return webView
    }
}
