package io.embrace.android.embracesdk.capture.webview

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.internal.capture.webview.WebViewDataSource
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.payload.WebViewInfo
import io.embrace.android.embracesdk.payload.WebVital
import io.embrace.android.embracesdk.payload.WebVitalType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class WebViewDataSourceTest {

    private lateinit var clock: FakeClock
    private lateinit var writer: FakeCurrentSessionSpan
    private lateinit var dataSource: WebViewDataSource

    @Before
    fun setUp() {
        clock = FakeClock()
        writer = FakeCurrentSessionSpan()
        dataSource = WebViewDataSource(
            FakeConfigService().webViewVitalsBehavior,
            writer,
            EmbLoggerImpl(),
            EmbraceSerializer()
        )
    }

    @Test
    fun `calling loadDataIntoSession with an empty list, doesn't add any event`() {
        dataSource.loadDataIntoSession(emptyList())
        assertEquals(0, writer.addedEvents.size)
    }

    @Test
    fun `calling loadDataIntoSession with a list of WebViewInfo, adds the events`() {
        val webViewInfo1 = getWebViewInfo("https://example1.com")
        val webViewInfo2 = getWebViewInfo("https://example2.com")
        dataSource.loadDataIntoSession(listOf(webViewInfo1, webViewInfo2))
        assertEquals(2, writer.addedEvents.size)
        assertEquals(2, writer.addedEvents.count { it.schemaType.fixedObjectName == "webview-info" })
        assertEquals("https://example1.com", writer.addedEvents[0].schemaType.attributes()["emb.webview_info.url"])
        assertEquals("https://example2.com", writer.addedEvents[1].schemaType.attributes()["emb.webview_info.url"])
    }

    private fun getWebViewInfo(url: String): WebViewInfo {
        return WebViewInfo(
            url = url,
            webVitals = mutableListOf(
                WebVital(
                    type = WebVitalType.LCP,
                    name = "largest-contentful-paint",
                    startTime = 1715203972149,
                    duration = 200,
                    properties = emptyMap(),
                    score = 0.5
                ),
                WebVital(
                    type = WebVitalType.FCP,
                    name = "first-paint",
                    startTime = 1715203972149,
                    duration = 270,
                    properties = emptyMap(),
                    score = 0.5
                )
            ),
            tag = "tag",
            startTime = clock.now()
        )
    }
}
