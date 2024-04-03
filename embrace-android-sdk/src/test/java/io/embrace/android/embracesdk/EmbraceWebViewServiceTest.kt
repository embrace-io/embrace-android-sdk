package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.capture.webview.EmbraceWebViewService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.WebViewVitals
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeWebViewVitalsBehavior
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.WebVitalType
import io.embrace.android.embracesdk.utils.at
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class EmbraceWebViewServiceTest {

    companion object {
        private const val embraceKeyForConsoleLogs = "EMBRACE_METRIC"
    }

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

    private lateinit var configService: ConfigService
    private lateinit var embraceWebViewService: EmbraceWebViewService
    private var cfg: RemoteConfig? = RemoteConfig()

    @Before
    fun setup() {
        cfg = RemoteConfig(webViewVitals = WebViewVitals(100f, 50))
        configService = FakeConfigService(webViewVitalsBehavior = fakeWebViewVitalsBehavior { cfg })
        embraceWebViewService = EmbraceWebViewService(configService, EmbraceSerializer(), InternalEmbraceLogger())
    }

    @Test
    fun `test messages complete group by url and timestamp`() {
        embraceWebViewService.collectWebData("webView1", expectedCompleteData)

        assertEquals(1, embraceWebViewService.getCapturedData().size)
        assertEquals(4, embraceWebViewService.getCapturedData().at(0)?.webVitals?.size)
    }

    @Test
    fun `test two complete groups by url and timestamp`() {
        embraceWebViewService.collectWebData("webView1", expectedCompleteData)
        embraceWebViewService.collectWebData("webView1", expectedCompleteData2)

        assertEquals(2, embraceWebViewService.getCapturedData().size)
        assertEquals(4, embraceWebViewService.getCapturedData().at(0)?.webVitals?.size)
        assertEquals(4, embraceWebViewService.getCapturedData().at(1)?.webVitals?.size)
    }

    @Test
    fun `test two complete groups whit same url and timestamp keep correct CLS and LCP`() {
        embraceWebViewService.collectWebData("webView1", expectedCompleteData)
        embraceWebViewService.collectWebData("webView1", expectedCompleteRepeatedData)

        assertEquals(1, embraceWebViewService.getCapturedData().size)
        assertEquals(4, embraceWebViewService.getCapturedData().at(0)?.webVitals?.size)

        embraceWebViewService.getCapturedData().at(0)?.webVitals?.forEach {
            when (it.type) {
                WebVitalType.CLS -> {
                    assertEquals(
                        20L,
                        it.duration
                    ) // bigger duration from expectedCompleteRepeatedData
                }

                WebVitalType.LCP -> {
                    assertEquals(2222, it.startTime) // bigger starttime from expectedCompleteData
                }
                else -> {}
            }
        }
    }

    @Test
    fun `test 3 groups 2 diff timestamps `() {
        embraceWebViewService.collectWebData("webView1", expectedCompleteData)
        embraceWebViewService.collectWebData("webView1", expectedCompleteData2)
        embraceWebViewService.collectWebData("webView1", expectedCompleteRepeatedData)

        assertEquals(2, embraceWebViewService.getCapturedData().size)
        assertEquals(4, embraceWebViewService.getCapturedData().at(0)?.webVitals?.size)
        assertEquals(4, embraceWebViewService.getCapturedData().at(1)?.webVitals?.size)
    }

    @Test
    fun `test repeated elements in one message`() {
        embraceWebViewService.collectWebData("webView1", repeatedElementsSameMessage)

        assertEquals(1, embraceWebViewService.getCapturedData().size)
        assertEquals(4, embraceWebViewService.getCapturedData().at(0)?.webVitals?.size)

        embraceWebViewService.getCapturedData().at(0)?.webVitals?.forEach {
            when (it.type) {
                WebVitalType.CLS -> {
                    assertEquals(
                        30L,
                        it.duration
                    ) // bigger duration from expectedCompleteRepeatedData
                }

                WebVitalType.LCP -> {
                    assertEquals(2222, it.startTime) // bigger starttime from expectedCompleteData
                }
                else -> {}
            }
        }
    }

    @Test
    fun `test limit collect web vital by maxVitals remote config`() {
        cfg = RemoteConfig(webViewVitals = WebViewVitals(100f, 1))

        embraceWebViewService.collectWebData("webViewMock", expectedCompleteData)
        embraceWebViewService.collectWebData("webViewMock", expectedCompleteData2)
        assertEquals(1, embraceWebViewService.getCapturedData().size)

        // same but bigger max vitals limit
        cfg = RemoteConfig(webViewVitals = WebViewVitals(100f, 10))

        embraceWebViewService.collectWebData("webViewMock", expectedCompleteData)
        embraceWebViewService.collectWebData("webViewMock", expectedCompleteData2)
        assertEquals(2, embraceWebViewService.getCapturedData().size)
    }

    @Test
    fun `test web vital is not collected if json exceeds max length`() {
        val repeatTimes = 2000 / embraceKeyForConsoleLogs.length
        val messageTooLong =
            "$embraceKeyForConsoleLogs ".repeat(repeatTimes) + "1" // limit is 800 characters

        embraceWebViewService.collectWebData("webViewMock", messageTooLong)
        assertEquals(0, embraceWebViewService.getCapturedData().size)
    }

    @Test
    fun `WebView console log is only collected if it has the Embrace key`() {
        val dataWithoutKey = expectedCompleteData2.replace(embraceKeyForConsoleLogs, "")

        embraceWebViewService.collectWebData("webView1", expectedCompleteData)
        embraceWebViewService.collectWebData("webView2", dataWithoutKey)

        assertEquals(1, embraceWebViewService.getCapturedData().size)
    }

    @Test
    fun testWebViewCleanCollections() {
        embraceWebViewService.collectWebData("webView1", repeatedElementsSameMessage)
        assertEquals(1, embraceWebViewService.getCapturedData().size)

        embraceWebViewService.cleanCollections()
        assertEquals(0, embraceWebViewService.getCapturedData().size)
    }
}
