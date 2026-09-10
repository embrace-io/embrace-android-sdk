package io.embrace.android.embracesdk.internal.instrumentation.webview

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.behavior.FakeBreadcrumbBehavior
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.instrumented.schema.WebViewFragmentCapture
import io.opentelemetry.kotlin.semconv.UrlAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class WebViewUrlDataSourceTest {

    private lateinit var args: FakeInstrumentationArgs

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext())
    }

    private fun createSource(
        queryParamCaptureEnabled: Boolean = false,
        fragmentCapture: WebViewFragmentCapture = WebViewFragmentCapture.KEEP,
    ): WebViewUrlDataSource {
        args = FakeInstrumentationArgs(
            ApplicationProvider.getApplicationContext(),
            configService = FakeConfigService(
                breadcrumbBehavior = FakeBreadcrumbBehavior(
                    queryParamCaptureEnabled = queryParamCaptureEnabled,
                    fragmentCapture = fragmentCapture,
                    webViewBreadcrumbCaptureEnabled = true,
                ),
            ),
        )
        return WebViewUrlDataSource(args)
    }

    private fun assertCaptured(
        expected: String,
        url: String?,
        queryParamCaptureEnabled: Boolean = false,
        fragmentCapture: WebViewFragmentCapture = WebViewFragmentCapture.KEEP,
    ) {
        val source = createSource(queryParamCaptureEnabled, fragmentCapture)
        source.logWebView(url)
        with(args.destination.addedEvents.single()) {
            assertEquals(EmbType.Ux.WebView, schemaType.telemetryType)
            assertEquals(args.clock.now(), startTimeMs)
            assertEquals(mapOf(UrlAttributes.URL_FULL to expected), schemaType.attributes())
        }
    }

    @Test
    fun `add breadcrumb`() {
        assertCaptured(
            expected = "http://www.google.com?query=123",
            url = "http://www.google.com?query=123",
            queryParamCaptureEnabled = true,
        )
    }

    @Test
    fun `query param capture disabled`() {
        assertCaptured(
            expected = "http://www.google.com",
            url = "http://www.google.com?query=123",
        )
    }

    @Test
    fun `limit not exceeded`() {
        val source = createSource()
        repeat(150) { k ->
            source.logWebView("http://www.google.com?query=$k")
        }
        assertEquals(100, args.destination.addedEvents.size)
    }

    @Test
    fun `null url captures nothing`() {
        val source = createSource()
        source.logWebView(null)
        assertTrue(args.destination.addedEvents.isEmpty())
    }

    // the fragment setting, with the query stripped

    @Test
    fun `fragment retained by default`() {
        assertCaptured(
            expected = "http://g.com#tok=x",
            url = "http://g.com?q=1#tok=x",
            fragmentCapture = WebViewFragmentCapture.KEEP,
        )
    }

    @Test
    fun `fragment redacted`() {
        assertCaptured(
            expected = "http://g.com#tok=",
            url = "http://g.com?q=1#tok=x",
            fragmentCapture = WebViewFragmentCapture.REDACT,
        )
    }

    @Test
    fun `fragment removed`() {
        assertCaptured(
            expected = "http://g.com",
            url = "http://g.com?q=1#tok=x",
            fragmentCapture = WebViewFragmentCapture.REMOVE,
        )
    }

    @Test
    fun `hash route survives query strip`() {
        assertCaptured(
            expected = "http://g.com#/orders/123",
            url = "http://g.com?q=1#/orders/123",
            fragmentCapture = WebViewFragmentCapture.REDACT,
        )
    }

    @Test
    fun `oauth implicit redirect`() {
        assertCaptured(
            expected = "https://permissions.customer.eu/#access_token=",
            url = "https://permissions.customer.eu/#access_token=eyJ0eXAiOiJKV1Qi",
            fragmentCapture = WebViewFragmentCapture.REDACT,
        )
    }

    @Test
    fun `question mark inside fragment is not a query`() {
        // the '?' falls after the '#', so the query strip must not reach it
        assertCaptured(
            expected = "http://g.com#frag?x=1",
            url = "http://g.com#frag?x=1",
            fragmentCapture = WebViewFragmentCapture.KEEP,
        )
    }

    @Test
    fun `no query or fragment unchanged`() {
        assertCaptured(
            expected = "http://g.com",
            url = "http://g.com",
            fragmentCapture = WebViewFragmentCapture.REDACT,
        )
    }

    @Test
    fun `empty fragment survives as a trailing hash`() {
        assertCaptured(
            expected = "http://g.com#",
            url = "http://g.com#",
            fragmentCapture = WebViewFragmentCapture.REDACT,
        )
    }

    @Test
    fun `empty fragment is dropped by remove`() {
        assertCaptured(
            expected = "http://g.com",
            url = "http://g.com#",
            fragmentCapture = WebViewFragmentCapture.REMOVE,
        )
    }

    @Test
    fun `query only url`() {
        assertCaptured(expected = "", url = "?a=1")
    }

    // the two settings are independent

    @Test
    fun `capture enabled retains all`() {
        assertCaptured(
            expected = "http://g.com?q=1#tok=x",
            url = "http://g.com?q=1#tok=x",
            queryParamCaptureEnabled = true,
            fragmentCapture = WebViewFragmentCapture.KEEP,
        )
    }

    @Test
    fun `capture enabled still redacts the fragment`() {
        assertCaptured(
            expected = "http://g.com?q=1#tok=",
            url = "http://g.com?q=1#tok=x",
            queryParamCaptureEnabled = true,
            fragmentCapture = WebViewFragmentCapture.REDACT,
        )
    }

    @Test
    fun `capture enabled with remove keeps the query only`() {
        assertCaptured(
            expected = "http://g.com?q=1",
            url = "http://g.com?q=1#tok=x",
            queryParamCaptureEnabled = true,
            fragmentCapture = WebViewFragmentCapture.REMOVE,
        )
    }
}
