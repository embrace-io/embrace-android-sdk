package io.embrace.android.embracesdk.internal.instrumentation.webview

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.behavior.FakeBreadcrumbBehavior
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.opentelemetry.kotlin.semconv.UrlAttributes
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class WebViewUrlDataSourceTest {

    private lateinit var source: WebViewUrlDataSource
    private lateinit var args: FakeInstrumentationArgs

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(mockk())
    }

    @Test
    fun `add breadcrumb`() {
        source = WebViewUrlDataSource(args)
        source.logWebView("http://www.google.com?query=123")
        with(args.destination.addedEvents.single()) {
            assertEquals(EmbType.Ux.WebView, schemaType.telemetryType)
            assertEquals(args.clock.now(), startTimeMs)
            assertEquals(
                mapOf(
                    UrlAttributes.URL_FULL to "http://www.google.com?query=123"
                ),
                schemaType.attributes()
            )
        }
    }

    @Test
    fun `query param capture disabled`() {
        args = FakeInstrumentationArgs(
            mockk(),
            configService = FakeConfigService(
                breadcrumbBehavior = FakeBreadcrumbBehavior(
                    queryParamCaptureEnabled = false,
                    webViewBreadcrumbCaptureEnabled = true
                )
            )
        )

        val clock = FakeClock()
        source = WebViewUrlDataSource(args)
        source.logWebView("http://www.google.com?query=123")
        with(args.destination.addedEvents.single()) {
            assertEquals(EmbType.Ux.WebView, schemaType.telemetryType)
            assertEquals(clock.now(), startTimeMs)
            assertEquals(
                mapOf(
                    UrlAttributes.URL_FULL to "http://www.google.com"
                ),
                schemaType.attributes()
            )
        }
    }

    @Test
    fun `limit not exceeded`() {
        args = FakeInstrumentationArgs(
            mockk(),
            configService = FakeConfigService(
                breadcrumbBehavior = FakeBreadcrumbBehavior(
                    queryParamCaptureEnabled = false,
                    webViewBreadcrumbCaptureEnabled = true
                )
            )
        )

        source = WebViewUrlDataSource(args)
        repeat(150) { k ->
            source.logWebView(
                "http://www.google.com?query=$k",
            )
        }
        assertEquals(100, args.destination.addedEvents.size)
    }
}
