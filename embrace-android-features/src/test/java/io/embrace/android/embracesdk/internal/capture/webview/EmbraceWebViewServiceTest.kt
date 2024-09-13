package io.embrace.android.embracesdk.internal.capture.webview

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.behavior.FakeWebViewVitalsBehavior
import io.embrace.android.embracesdk.internal.TypeUtils
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.payload.WebVital
import io.embrace.android.embracesdk.internal.payload.WebVitalType
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.opentelemetry.semconv.UrlAttributes
import org.junit.Assert
import org.junit.Before
import org.junit.Test

private const val EMBRACE_KEY_FOR_CONSOLE_LOGS = "EMBRACE_METRIC"

internal class EmbraceWebViewServiceTest {

    private val expectedCompleteData =
        ResourceReader.readResourceAsText("expected_core_vital_script.json")
    private val expectedCompleteData2 =
        ResourceReader.readResourceAsText("expected_core_vital_script1.json")

    // same url and start time than expected_core_vital_script.json
    private val expectedCompleteRepeatedData =
        ResourceReader.readResourceAsText("expected_core_vital_script_repeated.json")

    // repeated elements in the same console message
    private val repeatedElementsSameMessage =
        ResourceReader.readResourceAsText("expected_core_vital_repeated_elements_script.json")

    private lateinit var serializer: EmbraceSerializer
    private lateinit var writer: FakeCurrentSessionSpan
    private lateinit var openTelemetryModule: FakeOpenTelemetryModule
    private lateinit var configService: FakeConfigService
    private lateinit var embraceWebViewService: EmbraceWebViewService

    @Before
    fun setup() {
        serializer = EmbraceSerializer()
        writer = FakeCurrentSessionSpan()
        openTelemetryModule = FakeOpenTelemetryModule(writer)
        configService = FakeConfigService(webViewVitalsBehavior = FakeWebViewVitalsBehavior(50, true))
        embraceWebViewService = EmbraceWebViewService(
            configService,
            serializer,
            EmbLoggerImpl()
        ) {
            WebViewDataSource(
                configService.webViewVitalsBehavior,
                writer,
                FakeEmbLogger(),
                serializer
            )
        }
    }

    @Test
    fun `test messages complete group by url and timestamp`() {
        embraceWebViewService.collectWebData("webView1", expectedCompleteData)

        Assert.assertEquals(1, writer.addedEvents.size)
        writer.addedEvents.first().let {
            assert(it.schemaType is SchemaType.WebViewInfo)
            val webViewInfo = it.schemaType as SchemaType.WebViewInfo
            Assert.assertEquals(
                "https://embrace.io/",
                webViewInfo.attributes()[UrlAttributes.URL_FULL.key]
            )
            val webVitals = webViewInfo.attributes()["emb.webview_info.web_vitals"]?.let { it1 ->
                serializer.fromJson(it1, List::class.java)
            }
            Assert.assertEquals(4, webVitals?.size)
        }
    }

    @Test
    fun `test two complete groups by url and timestamp`() {
        embraceWebViewService.collectWebData("webView1", expectedCompleteData)
        embraceWebViewService.collectWebData("webView1", expectedCompleteData2)

        Assert.assertEquals(2, writer.addedEvents.size)
        writer.addedEvents.forEach {
            assert(it.schemaType is SchemaType.WebViewInfo)
            val webViewInfo = it.schemaType as SchemaType.WebViewInfo
            Assert.assertEquals(
                "https://embrace.io/",
                webViewInfo.attributes()[UrlAttributes.URL_FULL.key]
            )
            val webVitals = webViewInfo.attributes()["emb.webview_info.web_vitals"]?.let { it1 ->
                serializer.fromJson(it1, List::class.java)
            }
            Assert.assertEquals(4, webVitals?.size)
        }
    }

    @Test
    fun `test two complete groups whit same url and timestamp keep correct CLS and LCP`() {
        embraceWebViewService.collectWebData("webView1", expectedCompleteData)
        embraceWebViewService.collectWebData("webView1", expectedCompleteRepeatedData)

        Assert.assertEquals(1, writer.addedEvents.size)
        val event = writer.addedEvents.first()
        assert(event.schemaType is SchemaType.WebViewInfo)
        val webViewInfo = event.schemaType as SchemaType.WebViewInfo
        Assert.assertEquals(
            "https://embrace.io/",
            webViewInfo.attributes()[UrlAttributes.URL_FULL.key]
        )
        val webVitals: List<WebVital>? = webViewInfo.attributes()["emb.webview_info.web_vitals"]?.let { wv ->
            val type = TypeUtils.typedList(WebVital::class)
            serializer.fromJson(wv, type)
        }
        Assert.assertEquals(4, webVitals?.size)
        webVitals?.forEach { wv ->
            when (wv.type) {
                WebVitalType.CLS -> Assert.assertEquals(20L, wv.duration)
                WebVitalType.LCP -> Assert.assertEquals(2222, wv.startTime)
                else -> {}
            }
        }
    }

    @Test
    fun `test 3 groups 2 diff timestamps `() {
        embraceWebViewService.collectWebData("webView1", expectedCompleteData)
        embraceWebViewService.collectWebData("webView1", expectedCompleteData2)
        embraceWebViewService.collectWebData("webView1", expectedCompleteRepeatedData)

        Assert.assertEquals(2, writer.addedEvents.size)
        writer.addedEvents.forEach {
            assert(it.schemaType is SchemaType.WebViewInfo)
            val webViewInfo = it.schemaType as SchemaType.WebViewInfo
            Assert.assertEquals(
                "https://embrace.io/",
                webViewInfo.attributes()[UrlAttributes.URL_FULL.key]
            )
            val webVitals = webViewInfo.attributes()["emb.webview_info.web_vitals"]?.let { it1 ->
                serializer.fromJson(it1, List::class.java)
            }
            Assert.assertEquals(4, webVitals?.size)
        }
    }

    @Test
    fun `test repeated elements in one message`() {
        embraceWebViewService.collectWebData("webView1", repeatedElementsSameMessage)

        Assert.assertEquals(1, writer.addedEvents.size)
        val event = writer.addedEvents.first()
        assert(event.schemaType is SchemaType.WebViewInfo)
        val webViewInfo = event.schemaType as SchemaType.WebViewInfo
        Assert.assertEquals(
            "https://embrace.io/",
            webViewInfo.attributes()[UrlAttributes.URL_FULL.key]
        )
        val webVitals: List<WebVital>? = webViewInfo.attributes()["emb.webview_info.web_vitals"]?.let { wv ->
            val type = TypeUtils.typedList(WebVital::class)
            serializer.fromJson(wv, type)
        }
        Assert.assertEquals(4, webVitals?.size)
        webVitals?.forEach { wv ->
            when (wv.type) {
                WebVitalType.CLS -> Assert.assertEquals(30L, wv.duration)
                WebVitalType.LCP -> Assert.assertEquals(2222, wv.startTime)
                else -> {}
            }
        }
    }

    @Test
    fun `test limit collect web vital by maxVitals remote config`() {
        configService.webViewVitalsBehavior = FakeWebViewVitalsBehavior(1, true)

        embraceWebViewService.collectWebData("webViewMock", expectedCompleteData)
        embraceWebViewService.collectWebData("webViewMock", expectedCompleteData2)
        Assert.assertEquals(1, writer.addedEvents.size)

        // same but bigger max vitals limit
        configService.webViewVitalsBehavior = FakeWebViewVitalsBehavior(10, true)

        embraceWebViewService.collectWebData("webViewMock", expectedCompleteData)
        embraceWebViewService.collectWebData("webViewMock", expectedCompleteData2)
        Assert.assertEquals(2, writer.addedEvents.size)
    }

    @Test
    fun `test web vital is not collected if json exceeds max length`() {
        val repeatTimes = 2000 / EMBRACE_KEY_FOR_CONSOLE_LOGS.length
        val messageTooLong =
            "$EMBRACE_KEY_FOR_CONSOLE_LOGS ".repeat(repeatTimes) + "1" // limit is 800 characters

        embraceWebViewService.collectWebData("webViewMock", messageTooLong)
        Assert.assertEquals(0, writer.addedEvents.size)
    }

    @Test
    fun `WebView console log is only collected if it has the Embrace key`() {
        val dataWithoutKey = expectedCompleteData2.replace(EMBRACE_KEY_FOR_CONSOLE_LOGS, "")

        embraceWebViewService.collectWebData("webView1", expectedCompleteData)
        embraceWebViewService.collectWebData("webView2", dataWithoutKey)

        Assert.assertEquals(1, writer.addedEvents.size)
    }

    @Test
    fun testWebViewCleanCollections() {
        embraceWebViewService.collectWebData("webView1", repeatedElementsSameMessage)
        embraceWebViewService.cleanCollections()
        Assert.assertEquals(1, writer.addedEvents.size)
    }
}
