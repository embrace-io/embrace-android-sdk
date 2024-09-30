package io.embrace.android.embracesdk.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.moshi.Types
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.fakes.behavior.FakeWebViewVitalsBehavior
import io.embrace.android.embracesdk.findEventsOfType
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getSentSessions
import io.embrace.android.embracesdk.getSingleSession
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.payload.WebVital
import io.embrace.android.embracesdk.internal.payload.WebVitalType
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.recordSession
import io.opentelemetry.semconv.UrlAttributes.URL_FULL
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class WebviewFeatureTest {

    private val expectedCompleteData =
        ResourceReader.readResourceAsText("expected_core_vital_script.json")
    private val serializer = EmbraceSerializer()

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Before
    fun setup() {
        testRule.harness.overriddenConfigService.webViewVitalsBehavior = FakeWebViewVitalsBehavior(50, true)
    }

    @Test
    fun `webview info feature`() {
        with(testRule) {
            harness.recordSession {
                embrace.trackWebViewPerformance("myWebView", expectedCompleteData)
            }

            val message = harness.getSingleSession()
            val events = message.findSessionSpan().findEventsOfType(EmbType.System.WebViewInfo)
            assertEquals(1, events.size)

            val event = events[0]
            val attrs = checkNotNull(event.attributes)
            assertEquals("emb-webview-info", event.name)
            assertEquals("myWebView", attrs.findAttributeValue("emb.webview_info.tag"))
            assertEquals("https://embrace.io/", attrs.findAttributeValue(URL_FULL.key))

            val webVitalsAttr = checkNotNull(attrs.findAttributeValue("emb.webview_info.web_vitals"))
            val type = Types.newParameterizedType(List::class.java, WebVital::class.java)
            val webVitals: List<WebVital> = serializer.fromJson(webVitalsAttr, type)

            assertEquals(4, webVitals.size)
            webVitals.forEach { wv ->
                when (wv.type) {
                    WebVitalType.CLS -> assertEquals(10L, wv.duration)
                    WebVitalType.LCP -> assertEquals(2222, wv.startTime)
                    else -> {}
                }
            }
        }
    }
}
