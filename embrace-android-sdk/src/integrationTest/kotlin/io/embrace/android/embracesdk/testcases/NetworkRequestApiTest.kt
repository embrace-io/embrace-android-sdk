package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.getLastSentSession
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.recordSession
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

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
            assertEquals(URL, networkSpan.attributes?.findAttributeValue("url.full"))
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

            val session = checkNotNull(testRule.harness.getLastSentSession())

            val spans = checkNotNull(session.data.spans?.filter { it.attributes?.findAttributeValue("http.request.method") != null })
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
                val attrs = checkNotNull(attributes)
                assertEquals(expectedRequest.url, attrs.findAttributeValue("url.full"))
                assertEquals(expectedRequest.httpMethod, attrs.findAttributeValue(HttpAttributes.HTTP_REQUEST_METHOD.key))
                assertEquals(expectedRequest.startTime.millisToNanos(), startTimeNanos)
                assertEquals(expectedRequest.endTime.millisToNanos(), endTimeNanos)
                assertEquals(expectedRequest.traceId, attrs.findAttributeValue("emb.trace_id"))
                assertEquals(expectedRequest.w3cTraceparent, attrs.findAttributeValue("emb.w3c_traceparent"))
                if (completed) {
                    assertEquals(expectedRequest.responseCode.toString(), attrs.findAttributeValue(HttpAttributes.HTTP_RESPONSE_STATUS_CODE.key))
                    assertEquals(expectedRequest.bytesSent.toString(), attrs.findAttributeValue(HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE.key))
                    assertEquals(expectedRequest.bytesReceived.toString(), attrs.findAttributeValue(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE.key))
                    assertEquals(null, attrs.findAttributeValue("error.type"))
                    assertEquals(null, attrs.findAttributeValue("error.message"))
                    val statusCode = expectedRequest.responseCode
                    val expectedStatus = if (statusCode != null && statusCode >= 200 && statusCode < 400) {
                        Span.Status.UNSET
                    } else {
                        Span.Status.ERROR
                    }
                    assertEquals(expectedStatus, status)
                } else {
                    assertEquals(null, attrs.findAttributeValue(HttpAttributes.HTTP_RESPONSE_STATUS_CODE.key))
                    assertEquals(null, attrs.findAttributeValue(HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE.key))
                    assertEquals(null, attrs.findAttributeValue(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE.key))
                    assertEquals(expectedRequest.errorType, attrs.findAttributeValue("error.type"))
                    assertEquals(expectedRequest.errorMessage, attrs.findAttributeValue("error.message"))
                    assertEquals(Span.Status.ERROR, status)
                }
            }
        }
    }

    private fun validateAndReturnExpectedNetworkSpan(): Span {
        val session = checkNotNull(testRule.harness.getLastSentSession())

        val unfilteredSpans = checkNotNull(session.data.spans)
        val spans = checkNotNull(unfilteredSpans.filter { it.attributes?.findAttributeValue(HttpAttributes.HTTP_REQUEST_METHOD.key) != null })
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
