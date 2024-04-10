package io.embrace.android.embracesdk

import logTestMessage
import org.junit.Before
import org.junit.Test

internal class SessionSpanTest : BaseTest(useV2Payload = true) {

    @Before
    fun setup() {
        startEmbraceInForeground()
    }

    /**
     * Verifies that a session end message is sent.
     */
    @Test
    fun sessionEndMessageTest() {
        logTestMessage("Adding core web vitals")
        addCoreWebVitals()
        sendBackground()

        waitForRequest(
            listOf(
                RequestValidator(EmbraceEndpoint.SESSIONS) { request ->
                    validateMessageAgainstGoldenFile(request, "session-end-v2.json")
                })
        )
    }

    private fun addCoreWebVitals() {
        val webViewExpectedLog =
            mContext.assets.open("golden-files/${"expected-webview-core-vital.json"}")
                .bufferedReader().readText()
        Embrace.getInstance().trackWebViewPerformance("myWebView", webViewExpectedLog)
    }
}
