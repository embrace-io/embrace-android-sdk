package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.payload.NetworkCallV2
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.math.max

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
internal class NetworkRequestApiTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `record basic completed GET request`() {
        assertSingleNetworkRequestInSession(
            EmbraceNetworkRequest.fromCompletedRequest(
                URL,
                HttpMethod.GET,
                START_TIME,
                END_TIME,
                BYTES_SENT,
                BYTES_RECEIVED,
                200
            )
        )
    }

    @Test
    fun `record completed POST request with traceId`() {
        assertSingleNetworkRequestInSession(
            expectedRequest = EmbraceNetworkRequest.fromCompletedRequest(
                URL,
                HttpMethod.POST,
                START_TIME,
                END_TIME,
                BYTES_SENT,
                BYTES_RECEIVED,
                200,
                TRACE_ID,
            )
        )
    }

    @Test
    fun `record completed request that failed with captured response`() {
        assertSingleNetworkRequestInSession(
            expectedRequest = EmbraceNetworkRequest.fromCompletedRequest(
                URL,
                HttpMethod.GET,
                START_TIME,
                END_TIME,
                BYTES_SENT,
                BYTES_RECEIVED,
                500,
                TRACE_ID,
                NETWORK_CAPTURE_DATA
            )
        )
    }

    @Test
    fun `record completed request with traceparent`() {
        assertSingleNetworkRequestInSession(
            expectedRequest = EmbraceNetworkRequest.fromCompletedRequest(
                URL,
                HttpMethod.GET,
                START_TIME,
                END_TIME,
                BYTES_SENT,
                BYTES_RECEIVED,
                200,
                TRACE_ID,
                TRACEPARENT,
                NETWORK_CAPTURE_DATA
            )
        )
    }

    @Test
    fun `record basic incomplete request`() {
        assertSingleNetworkRequestInSession(
            EmbraceNetworkRequest.fromIncompleteRequest(
                URL,
                HttpMethod.GET,
                START_TIME,
                END_TIME,
                NullPointerException::class.toString(),
                "Dang nothing there"
            ),
            completed = false
        )
    }

    @Test
    fun `record incomplete POST request with trace ID`() {
        assertSingleNetworkRequestInSession(
            EmbraceNetworkRequest.fromIncompleteRequest(
                URL,
                HttpMethod.POST,
                START_TIME,
                END_TIME,
                NullPointerException::class.toString(),
                "Dang nothing there",
                TRACE_ID
            ),
            completed = false
        )
    }

    @Test
    fun `record incomplete request with network capture`() {
        assertSingleNetworkRequestInSession(
            EmbraceNetworkRequest.fromIncompleteRequest(
                URL,
                HttpMethod.GET,
                START_TIME,
                END_TIME,
                NullPointerException::class.toString(),
                "Dang nothing there",
                TRACE_ID,
                NETWORK_CAPTURE_DATA
            ),
            completed = false
        )
    }

    @Test
    fun `record incomplete request with traceparent`() {
        assertSingleNetworkRequestInSession(
            EmbraceNetworkRequest.fromIncompleteRequest(
                URL,
                HttpMethod.GET,
                START_TIME,
                END_TIME,
                NullPointerException::class.toString(),
                "Dang nothing there",
                TRACE_ID,
                TRACEPARENT,
                NETWORK_CAPTURE_DATA
            ),
            completed = false
        )
    }

    @Test
    fun `disabled URLs not recorded`() {
        with(testRule) {
            harness.recordSession {
                harness.fakeConfigService.updateListeners()
                harness.fakeClock.tick(5)
                embrace.recordNetworkRequest(
                    EmbraceNetworkRequest.fromCompletedRequest(
                        DISABLED_URL,
                        HttpMethod.GET,
                        START_TIME,
                        END_TIME,
                        BYTES_SENT,
                        BYTES_RECEIVED,
                        200
                    )
                )
                harness.fakeClock.tick(5)
                embrace.recordNetworkRequest(
                    EmbraceNetworkRequest.fromIncompleteRequest(
                        DISABLED_URL,
                        HttpMethod.GET,
                        START_TIME + 1,
                        END_TIME,
                        NullPointerException::class.toString(),
                        "Dang nothing there"
                    )
                )
                harness.fakeClock.tick(5)
                embrace.recordNetworkRequest(
                    EmbraceNetworkRequest.fromCompletedRequest(
                        URL,
                        HttpMethod.GET,
                        START_TIME + 2,
                        END_TIME,
                        BYTES_SENT,
                        BYTES_RECEIVED,
                        200
                    )
                )
            }

            val networkCall = validateAndReturnExpectedNetworkCall()
            assertEquals(URL, networkCall.url)
        }
    }

    @Test
    fun `ensure calls with same callId but different start times are deduped`() {
        val expectedStartTime = START_TIME + 1
        with(testRule) {
            harness.recordSession {
                harness.fakeConfigService.updateListeners()
                harness.fakeClock.tick(5)

                val callId = UUID.randomUUID().toString()
                embrace.internalInterface.recordAndDeduplicateNetworkRequest(
                    callId,
                    EmbraceNetworkRequest.fromCompletedRequest(
                        "$URL/bad",
                        HttpMethod.GET,
                        START_TIME,
                        END_TIME,
                        BYTES_SENT,
                        BYTES_RECEIVED,
                        200
                    )
                )
                embrace.internalInterface.recordAndDeduplicateNetworkRequest(
                    callId,
                    EmbraceNetworkRequest.fromCompletedRequest(
                        URL,
                        HttpMethod.GET,
                        expectedStartTime,
                        expectedStartTime + 1,
                        BYTES_SENT,
                        BYTES_RECEIVED,
                        200
                    )
                )
            }

            val networkCall = validateAndReturnExpectedNetworkCall()
            assertEquals(URL, networkCall.url)
            assertEquals(expectedStartTime, networkCall.startTime)
        }
    }

    /**
     * This reproduces the bug that will be fixed. Uncomment when ready.
     */
    @Test
    fun `ensure network calls with the same start time are recorded properly`() {
        with(testRule) {
            harness.recordSession {
                harness.fakeConfigService.updateListeners()
                harness.fakeClock.tick(5)

                val request = EmbraceNetworkRequest.fromCompletedRequest(
                    URL,
                    HttpMethod.GET,
                    START_TIME,
                    END_TIME,
                    BYTES_SENT,
                    BYTES_RECEIVED,
                    200
                )

                embrace.recordNetworkRequest(request)
                embrace.recordNetworkRequest(request)
            }

            val session = testRule.harness.fakeDeliveryModule.deliveryService.lastSentSessions[0].first
            val requests = checkNotNull(session.performanceInfo?.networkRequests?.networkSessionV2?.requests)
            assertEquals(
                "Unexpected number of requests in sent session: ${requests.size}",
                2,
                requests.size
            )
        }
    }

    private fun assertSingleNetworkRequestInSession(
        expectedRequest: EmbraceNetworkRequest,
        completed: Boolean = true
    ) {
        with(testRule) {
            harness.recordSession {
                harness.fakeClock.tick(2L)
                harness.fakeConfigService.updateListeners()
                harness.fakeClock.tick(5L)
                embrace.recordNetworkRequest(expectedRequest)
            }

            val networkCall = validateAndReturnExpectedNetworkCall()
            with(networkCall) {
                assertEquals(expectedRequest.url, url)
                assertEquals(expectedRequest.httpMethod, httpMethod)
                assertEquals(expectedRequest.startTime, startTime)
                assertEquals(expectedRequest.endTime, endTime)
                assertEquals(max(expectedRequest.endTime - expectedRequest.startTime, 0L), duration)
                assertEquals(expectedRequest.traceId, traceId)
                assertEquals(expectedRequest.w3cTraceparent, w3cTraceparent)
                if (completed) {
                    assertEquals(expectedRequest.responseCode, responseCode)
                    assertEquals(expectedRequest.bytesSent, bytesSent)
                    assertEquals(expectedRequest.bytesReceived, bytesReceived)
                    assertEquals(null, errorType)
                    assertEquals(null, errorMessage)
                } else {
                    assertEquals(null, responseCode)
                    assertEquals(0, bytesSent)
                    assertEquals(0, bytesReceived)
                    assertEquals(expectedRequest.errorType, errorType)
                    assertEquals(expectedRequest.errorMessage, errorMessage)
                }
            }
        }
    }

    private fun validateAndReturnExpectedNetworkCall(): NetworkCallV2 {
        val session = testRule.harness.fakeDeliveryModule.deliveryService.lastSentSessions[0].first

        // Look for a specific error where the fetch from the cache returns a stale value
        session.session.exceptionError?.exceptionErrors?.forEach { errorInfo ->
            errorInfo.exceptions?.forEach { exception ->
                val msg = exception.message
                assertTrue(
                    "Wrong network call count returned: $msg",
                    msg?.startsWith("Cached network call count") == false
                )
            }
        }

        val requests = checkNotNull(session.performanceInfo?.networkRequests?.networkSessionV2?.requests)
        assertEquals(
            "Unexpected number of requests in sent session: ${requests.size}",
            1,
            requests.size
        )

        return requests.first()
    }

    companion object {
        private const val URL = "https://embrace.io"
        private const val DISABLED_URL = "https://dontlogmebro.pizza/yum"
        private const val START_TIME = 1692201601000L
        private const val END_TIME = 1692201603000L
        private const val BYTES_SENT = 100L
        private const val BYTES_RECEIVED = 500L
        private const val TRACE_ID = "rAnDoM-traceId"
        private const val TRACEPARENT = "00-c4ada96c31e1b6b9e351a1cffc99ae38-331f3a8acf49d295-01"

        private val NETWORK_CAPTURE_DATA = NetworkCaptureData(
            requestHeaders = mapOf(Pair("x-emb-test", "holla")),
            requestQueryParams = "trackMe=noooooo",
            capturedRequestBody = "haha".toByteArray(),
            responseHeaders = mapOf(Pair("x-emb-response-header", "alloh")),
            capturedResponseBody = "woohoo".toByteArray(),
            dataCaptureErrorMessage = null
        )
    }
}
