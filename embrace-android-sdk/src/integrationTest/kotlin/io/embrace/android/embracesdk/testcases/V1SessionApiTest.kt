package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.validatePayloadAgainstGoldenFile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class V1SessionApiTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        IntegrationTestRule.Harness(useV2Payload = false)
    }

    /**
     * Verifies that a session end message is sent.
     */
    @Test
    fun sessionEndMessageTest() {
        with(testRule) {
            val message = harness.recordSession {
                embrace.setUserIdentifier("some id")
                embrace.setUserEmail("user@email.com")
                embrace.setUsername("John Doe")

                // add webview information
                val msg = ResourceReader.readResourceAsText("expected-webview-core-vital.json")
                embrace.trackWebViewPerformance("myWebView", msg)
            }
            validatePayloadAgainstGoldenFile(message, "v1_session_expected.json")
        }
    }
}
