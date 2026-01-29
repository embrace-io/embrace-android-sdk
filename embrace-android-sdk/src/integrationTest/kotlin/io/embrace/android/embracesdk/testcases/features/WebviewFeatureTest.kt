package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.assertions.findEventsOfType
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.instrumentation.webview.WebViewUrlDataSource
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.opentelemetry.kotlin.semconv.UrlAttributes.URL_FULL
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class WebviewFeatureTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `webview info feature`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    findDataSource<WebViewUrlDataSource>().logWebView("myWebView")
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val events = message.findSessionSpan().findEventsOfType(EmbType.System.WebViewInfo)
                assertEquals(1, events.size)

                val event = events[0]
                assertEquals("emb-web-view", event.name)
                event.attributes?.assertMatches(
                    mapOf(
                        "emb.type" to "ux.webview",
                        URL_FULL to "myWebView"
                    )
                )
            }
        )
    }
}
