package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.local.WebViewLocalConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.fakeBreadcrumbBehavior
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import org.junit.Assert
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
            InternalEmbraceLogger(),
        )
        source.logWebView(
            "http://www.google.com?query=123",
            15000000000
        )
        with(writer.addedEvents.single()) {
            Assert.assertEquals("web-view", schemaType.defaultName)
            Assert.assertEquals(15000000000.millisToNanos(), spanStartTimeMs)
            Assert.assertEquals(
                mapOf(
                    EmbType.Ux.WebView.toEmbraceKeyValuePair(),
                    "webview.url" to "http://www.google.com?query=123"
                ),
                schemaType.attributes()
            )
        }
    }

    @Test
    fun `webview capture disabled`() {
        configService = FakeConfigService(
            breadcrumbBehavior = fakeBreadcrumbBehavior(
                localCfg = {
                    SdkLocalConfig(
                        webViewConfig = WebViewLocalConfig(
                            captureWebViews = false,
                            captureQueryParams = false
                        )
                    )
                }
            )
        )
        source = WebViewUrlDataSource(
            configService.breadcrumbBehavior,
            writer,
            InternalEmbraceLogger(),
        )
        source.logWebView(
            "http://www.google.com?query=123",
            15000000000
        )
        Assert.assertTrue(writer.addedEvents.isEmpty())
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
            InternalEmbraceLogger(),
        )
        source.logWebView(
            "http://www.google.com?query=123",
            15000000000
        )
        with(writer.addedEvents.single()) {
            Assert.assertEquals("web-view", schemaType.defaultName)
            Assert.assertEquals(15000000000.millisToNanos(), spanStartTimeMs)
            Assert.assertEquals(
                mapOf(
                    EmbType.Ux.WebView.toEmbraceKeyValuePair(),
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
            InternalEmbraceLogger(),
        )
        repeat(150) { k ->
            source.logWebView(
                "http://www.google.com?query=$k",
                15000000000
            )
        }
        Assert.assertEquals(100, writer.addedEvents.size)
    }
}
