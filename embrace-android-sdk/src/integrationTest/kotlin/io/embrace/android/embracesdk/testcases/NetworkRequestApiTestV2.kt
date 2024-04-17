package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.getLastSentSessionMessage
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
internal class NetworkRequestApiTestV2 {
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
                harness.overriddenConfigService.updateListeners()
                harness.overriddenClock.tick(5)
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
                harness.overriddenClock.tick(5)
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
                harness.overriddenClock.tick(5)
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

            val networkSpan = validateAndReturnExpectedNetworkSpan()
            assertEquals(URL, networkSpan.attributes["url.full"])
        }
    }

    @Ignore() //TODO: Fix this test
    @Test
    fun `ensure calls with same callId but different start times are deduped`() {
        val expectedStartTime = START_TIME + 1
        with(testRule) {
            harness.recordSession {
                harness.overriddenConfigService.updateListeners()
                harness.overriddenClock.tick(5)

                val callId = UUID.randomUUID().toString()
                embrace.internalInterface.recordURLConnectionNetworkRequest(
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
                embrace.internalInterface.recordURLConnectionNetworkRequest(
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

            val networkSpan = validateAndReturnExpectedNetworkSpan()
            assertEquals(URL, networkSpan.attributes["url.full"])
            assertEquals(expectedStartTime, networkSpan.startTimeNanos)
        }
    }

    /**
     * This reproduces the bug that will be fixed. Uncomment when ready.
     */
    @Test
    fun `ensure network calls with the same start time are recorded properly`() {
        with(testRule) {
            harness.recordSession {
                harness.overriddenConfigService.updateListeners()
                harness.overriddenClock.tick(5)

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

            val session = checkNotNull(testRule.harness.getLastSentSessionMessage())

            val spans = checkNotNull(session.spans?.filter { it.attributes.containsKey("http.request.method") })
            assertEquals(
                "Unexpected number of requests in sent session: ${spans.size}",
                2,
                spans.size
            )
        }
    }

    private fun assertSingleNetworkRequestInSession(
        expectedRequest: EmbraceNetworkRequest,
        completed: Boolean = true
    ) {
        with(testRule) {
            harness.recordSession {
                harness.overriddenClock.tick(2L)
                harness.overriddenConfigService.updateListeners()
                harness.overriddenClock.tick(5L)
                embrace.recordNetworkRequest(expectedRequest)
            }

            val networkSpan = validateAndReturnExpectedNetworkSpan()
            with(networkSpan) {
                assertEquals(expectedRequest.url, this.attributes["url.full"])
                assertEquals(expectedRequest.httpMethod, this.attributes["http.request.method"])
                assertEquals(expectedRequest.startTime.millisToNanos(), this.startTimeNanos)
                assertEquals(expectedRequest.endTime.millisToNanos(), this.endTimeNanos)
                assertEquals(expectedRequest.traceId, this.attributes["emb.trace_id"])
                assertEquals(expectedRequest.w3cTraceparent, this.attributes["emb.w3c_traceparent"])
                if (completed) {
                    assertEquals(expectedRequest.responseCode.toString(), this.attributes["http.response.status_code"])
                    assertEquals(expectedRequest.bytesSent.toString(), this.attributes["http.request.body.size"])
                    assertEquals(expectedRequest.bytesReceived.toString(), this.attributes["http.response.body.size"])
                    assertEquals(null, this.attributes["error.type"])
                    assertEquals(null, this.attributes["error.message"])
                } else {
                    assertEquals(null, this.attributes["http.response.status_code"])
                    assertEquals(null, this.attributes["http.request.body.size"])
                    assertEquals(null, this.attributes["http.response.body.size"])
                    assertEquals(expectedRequest.errorType, this.attributes["error.type"])
                    assertEquals(expectedRequest.errorMessage, this.attributes["error.message"])
                }
            }
        }
    }

    private fun validateAndReturnExpectedNetworkSpan(): EmbraceSpanData {
        val session = checkNotNull(testRule.harness.getLastSentSessionMessage())

        val spans = checkNotNull(session.spans?.filter { it.attributes.containsKey("http.request.method") })
        assertEquals(
            "Unexpected number of requests in sent session: ${spans.size}",
            1,
            spans.size
        )

        return spans.first()
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
