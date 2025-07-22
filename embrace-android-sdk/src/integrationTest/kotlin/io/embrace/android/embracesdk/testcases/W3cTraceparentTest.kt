package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSpanOfType
import io.embrace.android.embracesdk.assertions.toMap
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validation of the internal API
 */
@RunWith(AndroidJUnit4::class)
internal class W3cTraceparentTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `network request captured via internal API`() {
        val w3cTraceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    val internalInterface = EmbraceInternalApi.getInstance().internalInterface
                    val span =
                        internalInterface.startNetworkRequestSpan(HTTP_METHOD, URL, START_TIME)
                            ?: return@recordSession
                    val request = EmbraceNetworkRequest.fromCompletedRequest(
                        url = URL,
                        httpMethod = HTTP_METHOD,
                        startTime = START_TIME,
                        endTime = END_TIME,
                        bytesSent = 0L,
                        bytesReceived = 0L,
                        statusCode = 200,
                        traceId = null,
                        w3cTraceparent = w3cTraceparent
                    )
                    internalInterface.endNetworkRequestSpan(request, span)
                }


            }, assertAction = {
                val payload = getSingleSessionEnvelope()
                val request = payload.findSpanOfType(EmbType.Performance.Network)
                val attrs = checkNotNull(request.attributes).toMap()
                assertEquals(w3cTraceparent, attrs["emb.w3c_traceparent"])
            })
    }

    companion object {
        private const val URL = "https://embrace.io"
        private val HTTP_METHOD = HttpMethod.GET
        private const val START_TIME = 1692201601000L
        private const val END_TIME = 1692201603000L
    }
}
