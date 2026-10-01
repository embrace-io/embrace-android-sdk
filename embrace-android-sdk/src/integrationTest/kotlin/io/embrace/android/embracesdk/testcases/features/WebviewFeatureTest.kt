package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.assertions.findEventsOfType
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.instrumentation.webview.WebViewUrlDataSource
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.opentelemetry.kotlin.semconv.UrlAttributes.URL_FULL
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class WebviewFeatureTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

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
                val events = message.findSessionPartSpan().findEventsOfType(EmbType.Ux.WebView)
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

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
