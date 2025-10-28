package io.embrace.android.embracesdk.internal.capture.crumbs

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.behavior.FakeBreadcrumbBehavior
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.opentelemetry.kotlin.semconv.UrlAttributes
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class WebViewUrlDataSourceTest {

    private lateinit var configService: ConfigService
    private lateinit var source: WebViewUrlDataSource
    private lateinit var destination: FakeTelemetryDestination

    @Before
    fun setUp() {
        destination = FakeTelemetryDestination()
    }

    @Test
    fun `add breadcrumb`() {
        configService = FakeConfigService(
            breadcrumbBehavior = FakeBreadcrumbBehavior(
                queryParamCaptureEnabled = true,
                webViewBreadcrumbCaptureEnabled = true
            )
        )
        source = WebViewUrlDataSource(
            configService.breadcrumbBehavior,
            destination,
            EmbLoggerImpl(),
        )
        source.logWebView(
            "http://www.google.com?query=123",
            15000000000
        )
        with(destination.addedEvents.single()) {
            assertEquals(EmbType.Ux.WebView, schemaType.telemetryType)
            assertEquals(15000000000, startTimeMs)
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
        configService = FakeConfigService(
            breadcrumbBehavior = FakeBreadcrumbBehavior(
                queryParamCaptureEnabled = false,
                webViewBreadcrumbCaptureEnabled = true
            )
        )
        source = WebViewUrlDataSource(
            configService.breadcrumbBehavior,
            destination,
            EmbLoggerImpl(),
        )
        source.logWebView(
            "http://www.google.com?query=123",
            15000000000
        )
        with(destination.addedEvents.single()) {
            assertEquals(EmbType.Ux.WebView, schemaType.telemetryType)
            assertEquals(15000000000, startTimeMs)
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
        configService = FakeConfigService(
            breadcrumbBehavior = FakeBreadcrumbBehavior(
                queryParamCaptureEnabled = false,
                webViewBreadcrumbCaptureEnabled = true
            )
        )
        source = WebViewUrlDataSource(
            configService.breadcrumbBehavior,
            destination,
            EmbLoggerImpl(),
        )
        repeat(150) { k ->
            source.logWebView(
                "http://www.google.com?query=$k",
                15000000000
            )
        }
        assertEquals(100, destination.addedEvents.size)
    }
}
