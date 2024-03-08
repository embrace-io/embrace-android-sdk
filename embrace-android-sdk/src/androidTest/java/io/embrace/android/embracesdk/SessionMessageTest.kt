package io.embrace.android.embracesdk

import logTestMessage
import org.junit.Before
import org.junit.Test

internal class SessionMessageTest : BaseTest() {

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
                RequestValidator(EmbraceEndpoint.EVENTS) { request ->
                    validateMessageAgainstGoldenFile(request, "moment-startup-end-event.json")
                },
                RequestValidator(EmbraceEndpoint.SESSIONS) { request ->
                    validateMessageAgainstGoldenFile(request, "session-end.json")
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
