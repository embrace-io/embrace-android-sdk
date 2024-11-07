package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import io.opentelemetry.semconv.ExceptionAttributes
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
    fun `record completed GET request with long URL`() {
        assertSingleNetworkRequestInSession(
            EmbraceNetworkRequest.fromCompletedRequest(
                LONG_URL,
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
                null,
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
                null,
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
        testRule.runTest(
            remoteConfig = RemoteConfig(
                disabledUrlPatterns = setOf("dontlogmebro.pizza"),
                networkCaptureRules = setOf(
                    NetworkCaptureRuleRemoteConfig(
                        id = "test",
                        duration = 10000,
                        method = "GET",
                        urlRegex = "capture.me",
                        expiresIn = 10000
                    )
                )
            ),
            testCaseAction = {
                recordSession {
                    clock.tick(5)
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
                    clock.tick(5)
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
                    clock.tick(5)
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
            },
            assertAction = {
                validateAndReturnExpectedNetworkSpan().attributes?.assertMatches {
                    "url.full" to URL
                }
            }
        )
    }

    /**
     * This reproduces the bug that will be fixed. Uncomment when ready.
     */
    @Test
    fun `ensure network calls with the same start time are recorded properly`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    clock.tick(5)

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
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val spans =
                    checkNotNull(session.data.spans?.filter { it.attributes?.findAttributeValue("http.request.method") != null })
                assertEquals(
                    "Unexpected number of requests in sent session: ${spans.size}",
                    2,
                    spans.size
                )
            }
        )
    }

    private fun assertSingleNetworkRequestInSession(
        expectedRequest: EmbraceNetworkRequest,
        completed: Boolean = true,
    ) {

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    clock.tick(2L)
                    clock.tick(5L)
                    embrace.recordNetworkRequest(expectedRequest)
                }
            },
            assertAction = {
                val networkSpan = validateAndReturnExpectedNetworkSpan()
                with(networkSpan) {
                    assertEquals(expectedRequest.startTime.millisToNanos(), startTimeNanos)
                    assertEquals(expectedRequest.endTime.millisToNanos(), endTimeNanos)

                    if (completed) {
                        val statusCode = expectedRequest.responseCode
                        val expectedStatus =
                            if (statusCode != null && statusCode >= 200 && statusCode < 400) {
                                Span.Status.UNSET
                            } else {
                                Span.Status.ERROR
                            }
                        assertEquals(expectedStatus, status)
                    } else {
                        assertEquals(Span.Status.ERROR, status)
                    }

                    attributes?.assertMatches {
                        "url.full" to expectedRequest.url
                        HttpAttributes.HTTP_REQUEST_METHOD.key to expectedRequest.httpMethod
                        "emb.trace_id" to expectedRequest.traceId
                        "emb.w3c_traceparent" to expectedRequest.w3cTraceparent
                        HttpAttributes.HTTP_RESPONSE_STATUS_CODE.key to when {
                            completed -> expectedRequest.responseCode
                            else -> null
                        }
                        HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE.key to when {
                            completed -> expectedRequest.bytesSent
                            else -> null
                        }
                        HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE.key to when {
                            completed -> expectedRequest.bytesReceived
                            else -> null
                        }
                        "error.type" to when {
                            completed -> null
                            else -> expectedRequest.errorType
                        }
                        ExceptionAttributes.EXCEPTION_MESSAGE.key to when {
                            completed -> null
                            else -> expectedRequest.errorMessage
                        }
                    }
                }
            }
        )
    }

    private fun EmbracePayloadAssertionInterface.validateAndReturnExpectedNetworkSpan(): Span {
        val session = getSingleSessionEnvelope()

        val unfilteredSpans = checkNotNull(session.data.spans)
        val spans =
            checkNotNull(unfilteredSpans.filter { it.attributes?.findAttributeValue(HttpAttributes.HTTP_REQUEST_METHOD.key) != null })
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
        private val LONG_URL = "https://embrace.io/" + "s".repeat(1900)

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
