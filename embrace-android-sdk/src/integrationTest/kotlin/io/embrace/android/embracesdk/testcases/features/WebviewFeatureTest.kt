package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.moshi.Types
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.assertions.findEventsOfType
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.payload.WebVital
import io.embrace.android.embracesdk.internal.payload.WebVitalType
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import io.opentelemetry.semconv.UrlAttributes.URL_FULL
import org.junit.Assert.assertEquals
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

    @Test
    fun `webview info feature`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.trackWebViewPerformance("myWebView", expectedCompleteData)
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val events = message.findSessionSpan().findEventsOfType(EmbType.System.WebViewInfo)
                assertEquals(1, events.size)

                val event = events[0]
                assertEquals("emb-webview-info", event.name)
                event.attributes?.assertMatches {
                    "emb.webview_info.tag" to "myWebView"
                    URL_FULL.key to "https://embrace.io/"
                }


                val webVitalsAttr = checkNotNull(event.attributes?.findAttributeValue("emb.webview_info.web_vitals"))
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
        )
    }
}
