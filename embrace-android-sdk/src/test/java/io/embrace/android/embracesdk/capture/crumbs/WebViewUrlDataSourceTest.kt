package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.fakeBreadcrumbBehavior
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.capture.crumbs.WebViewUrlDataSource
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.internal.config.local.WebViewLocalConfig
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class WebViewUrlDataSourceTest {

    private lateinit var configService: ConfigService
    private lateinit var source: WebViewUrlDataSource
    private lateinit var writer: FakeCurrentSessionSpan

    @Before
    fun setUp() {
        writer = FakeCurrentSessionSpan()
    }

    @Test
    fun `add breadcrumb`() {
        configService = FakeConfigService(
            breadcrumbBehavior = fakeBreadcrumbBehavior(
                localCfg = {
                    SdkLocalConfig(
                        webViewConfig = WebViewLocalConfig(
                            captureWebViews = true,
                            captureQueryParams = true
                        )
                    )
                }
            )
        )
        source = WebViewUrlDataSource(
            configService.breadcrumbBehavior,
            writer,
            EmbLoggerImpl(),
        )
        source.logWebView(
            "http://www.google.com?query=123",
            15000000000
        )
        with(writer.addedEvents.single()) {
            assertEquals(EmbType.Ux.WebView, schemaType.telemetryType)
            assertEquals(15000000000, spanStartTimeMs)
            assertEquals(
                mapOf(
                    "webview.url" to "http://www.google.com?query=123"
                ),
                schemaType.attributes()
            )
        }
    }

    @Test
    fun `query param capture disabled`() {
        configService = FakeConfigService(
            breadcrumbBehavior = fakeBreadcrumbBehavior(
                localCfg = {
                    SdkLocalConfig(
                        webViewConfig = WebViewLocalConfig(
                            captureWebViews = true,
                            captureQueryParams = false
                        )
                    )
                }
            )
        )
        source = WebViewUrlDataSource(
            configService.breadcrumbBehavior,
            writer,
            EmbLoggerImpl(),
        )
        source.logWebView(
            "http://www.google.com?query=123",
            15000000000
        )
        with(writer.addedEvents.single()) {
            assertEquals(EmbType.Ux.WebView, schemaType.telemetryType)
            assertEquals(15000000000, spanStartTimeMs)
            assertEquals(
                mapOf(
                    "webview.url" to "http://www.google.com"
                ),
                schemaType.attributes()
            )
        }
    }

    @Test
    fun `limit not exceeded`() {
        configService = FakeConfigService(
            breadcrumbBehavior = fakeBreadcrumbBehavior(
                localCfg = {
                    SdkLocalConfig(
                        webViewConfig = WebViewLocalConfig(
                            captureWebViews = true,
                            captureQueryParams = false
                        )
                    )
                }
            )
        )
        source = WebViewUrlDataSource(
            configService.breadcrumbBehavior,
            writer,
            EmbLoggerImpl(),
        )
        repeat(150) { k ->
            source.logWebView(
                "http://www.google.com?query=$k",
                15000000000
            )
        }
        assertEquals(100, writer.addedEvents.size)
    }
}
