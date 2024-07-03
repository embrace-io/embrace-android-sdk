package io.embrace.android.embracesdk

import com.squareup.moshi.Types
import io.embrace.android.embracesdk.arch.SessionType
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.capture.webview.EmbraceWebViewService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.WebViewVitals
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.fakeWebViewVitalsBehavior
import io.embrace.android.embracesdk.fakes.injection.fakeDataSourceModule
import io.embrace.android.embracesdk.injection.DataSourceModule
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.payload.WebVital
import io.embrace.android.embracesdk.payload.WebVitalType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

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
    private lateinit var dataSourceModule: DataSourceModule
    private lateinit var configService: ConfigService
    private lateinit var embraceWebViewService: EmbraceWebViewService
    private var cfg: RemoteConfig? = RemoteConfig()

    @Before
    fun setup() {
        serializer = EmbraceSerializer()
        writer = FakeCurrentSessionSpan()
        openTelemetryModule = FakeOpenTelemetryModule(writer)
        dataSourceModule = fakeDataSourceModule(
            oTelModule = openTelemetryModule,
        ).apply {
            dataCaptureOrchestrator.currentSessionType = SessionType.FOREGROUND
        }
        cfg = RemoteConfig(webViewVitals = WebViewVitals(100f, 50))
        configService = FakeConfigService(webViewVitalsBehavior = fakeWebViewVitalsBehavior { cfg })
        embraceWebViewService = EmbraceWebViewService(
            configService,
            serializer,
            EmbLoggerImpl()
        ) { dataSourceModule }
    }

    @Test
    fun `test messages complete group by url and timestamp`() {
        embraceWebViewService.collectWebData("webView1", expectedCompleteData)
        embraceWebViewService.loadDataIntoSession()

        assertEquals(1, writer.addedEvents.size)
        writer.addedEvents.first().let {
            assert(it.schemaType is SchemaType.WebViewInfo)
            val webViewInfo = it.schemaType as SchemaType.WebViewInfo
            assertEquals("https://embrace.io/", webViewInfo.attributes()["emb.webview_info.url"])
            val webVitals = webViewInfo.attributes()["emb.webview_info.web_vitals"]?.let { it1 ->
                serializer.fromJson(it1, List::class.java)
            }
            assertEquals(4, webVitals?.size)
        }
    }

    @Test
    fun `test two complete groups by url and timestamp`() {
        embraceWebViewService.collectWebData("webView1", expectedCompleteData)
        embraceWebViewService.collectWebData("webView1", expectedCompleteData2)
        embraceWebViewService.loadDataIntoSession()

        assertEquals(2, writer.addedEvents.size)
        writer.addedEvents.forEach {
            assert(it.schemaType is SchemaType.WebViewInfo)
            val webViewInfo = it.schemaType as SchemaType.WebViewInfo
            assertEquals("https://embrace.io/", webViewInfo.attributes()["emb.webview_info.url"])
            val webVitals = webViewInfo.attributes()["emb.webview_info.web_vitals"]?.let { it1 ->
                serializer.fromJson(it1, List::class.java)
            }
            assertEquals(4, webVitals?.size)
        }
    }

    @Test
    fun `test two complete groups whit same url and timestamp keep correct CLS and LCP`() {
        embraceWebViewService.collectWebData("webView1", expectedCompleteData)
        embraceWebViewService.collectWebData("webView1", expectedCompleteRepeatedData)
        embraceWebViewService.loadDataIntoSession()

        assertEquals(1, writer.addedEvents.size)
        val event = writer.addedEvents.first()
        assert(event.schemaType is SchemaType.WebViewInfo)
        val webViewInfo = event.schemaType as SchemaType.WebViewInfo
        assertEquals("https://embrace.io/", webViewInfo.attributes()["emb.webview_info.url"])
        val webVitals: List<WebVital>? = webViewInfo.attributes()["emb.webview_info.web_vitals"]?.let { wv ->
            val type = Types.newParameterizedType(List::class.java, WebVital::class.java)
            serializer.fromJson(wv, type)
        }
        assertEquals(4, webVitals?.size)
        webVitals?.forEach { wv ->
            when (wv.type) {
                WebVitalType.CLS -> assertEquals(20L, wv.duration)
                WebVitalType.LCP -> assertEquals(2222, wv.startTime)
                else -> {}
            }
        }
    }

    @Test
    fun `test 3 groups 2 diff timestamps `() {
        embraceWebViewService.collectWebData("webView1", expectedCompleteData)
        embraceWebViewService.collectWebData("webView1", expectedCompleteData2)
        embraceWebViewService.collectWebData("webView1", expectedCompleteRepeatedData)
        embraceWebViewService.loadDataIntoSession()

        assertEquals(2, writer.addedEvents.size)
        writer.addedEvents.forEach {
            assert(it.schemaType is SchemaType.WebViewInfo)
            val webViewInfo = it.schemaType as SchemaType.WebViewInfo
            assertEquals("https://embrace.io/", webViewInfo.attributes()["emb.webview_info.url"])
            val webVitals = webViewInfo.attributes()["emb.webview_info.web_vitals"]?.let { it1 ->
                serializer.fromJson(it1, List::class.java)
            }
            assertEquals(4, webVitals?.size)
        }
    }

    @Test
    fun `test repeated elements in one message`() {
        embraceWebViewService.collectWebData("webView1", repeatedElementsSameMessage)
        embraceWebViewService.loadDataIntoSession()

        assertEquals(1, writer.addedEvents.size)
        val event = writer.addedEvents.first()
        assert(event.schemaType is SchemaType.WebViewInfo)
        val webViewInfo = event.schemaType as SchemaType.WebViewInfo
        assertEquals("https://embrace.io/", webViewInfo.attributes()["emb.webview_info.url"])
        val webVitals: List<WebVital>? = webViewInfo.attributes()["emb.webview_info.web_vitals"]?.let { wv ->
            val type = Types.newParameterizedType(List::class.java, WebVital::class.java)
            serializer.fromJson(wv, type)
        }
        assertEquals(4, webVitals?.size)
        webVitals?.forEach { wv ->
            when (wv.type) {
                WebVitalType.CLS -> assertEquals(30L, wv.duration)
                WebVitalType.LCP -> assertEquals(2222, wv.startTime)
                else -> {}
            }
        }
    }

    @Test
    fun `test limit collect web vital by maxVitals remote config`() {
        cfg = RemoteConfig(webViewVitals = WebViewVitals(100f, 1))

        embraceWebViewService.collectWebData("webViewMock", expectedCompleteData)
        embraceWebViewService.collectWebData("webViewMock", expectedCompleteData2)
        embraceWebViewService.loadDataIntoSession()
        assertEquals(1, writer.addedEvents.size)

        // same but bigger max vitals limit
        cfg = RemoteConfig(webViewVitals = WebViewVitals(100f, 10))

        embraceWebViewService.collectWebData("webViewMock", expectedCompleteData)
        embraceWebViewService.collectWebData("webViewMock", expectedCompleteData2)
        embraceWebViewService.loadDataIntoSession()
        assertEquals(2, writer.addedEvents.size)
    }

    @Test
    fun `test web vital is not collected if json exceeds max length`() {
        val repeatTimes = 2000 / EMBRACE_KEY_FOR_CONSOLE_LOGS.length
        val messageTooLong =
            "$EMBRACE_KEY_FOR_CONSOLE_LOGS ".repeat(repeatTimes) + "1" // limit is 800 characters

        embraceWebViewService.collectWebData("webViewMock", messageTooLong)
        embraceWebViewService.loadDataIntoSession()
        assertEquals(0, writer.addedEvents.size)
    }

    @Test
    fun `WebView console log is only collected if it has the Embrace key`() {
        val dataWithoutKey = expectedCompleteData2.replace(EMBRACE_KEY_FOR_CONSOLE_LOGS, "")

        embraceWebViewService.collectWebData("webView1", expectedCompleteData)
        embraceWebViewService.collectWebData("webView2", dataWithoutKey)
        embraceWebViewService.loadDataIntoSession()

        assertEquals(1, writer.addedEvents.size)
    }

    @Test
    fun testWebViewCleanCollections() {
        embraceWebViewService.collectWebData("webView1", repeatedElementsSameMessage)
        embraceWebViewService.cleanCollections()
        embraceWebViewService.loadDataIntoSession()
        assertEquals(0, writer.addedEvents.size)
    }
}

private const val EMBRACE_KEY_FOR_CONSOLE_LOGS = "EMBRACE_METRIC"
