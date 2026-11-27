package io.embrace.android.embracesdk.testcases

import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.assertions.getLogsOfType
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SendMode
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.network.EmbraceNetworkRequestDsl
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.network.NetworkRequestDsl
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.network.http.HttpRequestInfoModifier
import io.embrace.android.embracesdk.semconv.EmbNetworkCapturedRequestAttributes
import io.embrace.android.embracesdk.semconv.EmbNetworkRequestAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.embrace.android.embracesdk.testframework.assertions.assertSessionIds
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.opentelemetry.kotlin.semconv.HttpAttributes
import io.opentelemetry.kotlin.semconv.UrlAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class NetworkRequestApiTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

    @Test
    fun `record basic completed GET request`() {
        assertSingleNetworkRequestInSession(HttpMethod.GET) {
            completed {
                bytesSent = BYTES_SENT
                bytesReceived = BYTES_RECEIVED
                statusCode = 200
            }
        }
    }

    @Test
    fun `record completed GET request with long URL`() {
        assertSingleNetworkRequestInSession(HttpMethod.GET, url = LONG_URL) {
            completed {
                bytesSent = BYTES_SENT
                bytesReceived = BYTES_RECEIVED
                statusCode = 200
            }
        }
    }

    @Test
    fun `record completed POST request with traceId`() {
        assertSingleNetworkRequestInSession(HttpMethod.POST) {
            completed {
                bytesSent = BYTES_SENT
                bytesReceived = BYTES_RECEIVED
                statusCode = 200
            }
            traceId = TRACE_ID
        }
    }

    @Test
    fun `record completed request that failed with captured response`() {
        assertSingleNetworkRequestInSession(HttpMethod.GET) {
            completed {
                bytesSent = BYTES_SENT
                bytesReceived = BYTES_RECEIVED
                statusCode = 500
            }
            traceId = TRACE_ID
            networkCaptureData = NETWORK_CAPTURE_DATA
        }
    }

    @Test
    fun `record completed request with traceparent`() {
        assertSingleNetworkRequestInSession(HttpMethod.GET) {
            completed {
                bytesSent = BYTES_SENT
                bytesReceived = BYTES_RECEIVED
                statusCode = 200
            }
            traceId = TRACE_ID
            w3cTraceparent = TRACEPARENT
            networkCaptureData = NETWORK_CAPTURE_DATA
        }
    }

    @Test
    fun `record completed request through the deprecated API`() {
        assertSingleNetworkRequestInSession(HttpMethod.POST, deprecatedApi = true) {
            completed {
                bytesSent = BYTES_SENT
                bytesReceived = BYTES_RECEIVED
                statusCode = 200
            }
            traceId = TRACE_ID
            w3cTraceparent = TRACEPARENT
            networkCaptureData = NETWORK_CAPTURE_DATA
        }
    }

    @Test
    fun `record basic incomplete request`() {
        assertSingleNetworkRequestInSession(HttpMethod.GET, completed = false) {
            incomplete {
                errorType = NullPointerException::class.toString()
                errorMessage = "Dang nothing there"
            }
        }
    }

    @Test
    fun `record incomplete POST request with trace ID`() {
        assertSingleNetworkRequestInSession(HttpMethod.POST, completed = false) {
            incomplete {
                errorType = NullPointerException::class.toString()
                errorMessage = "Dang nothing there"
            }
            traceId = TRACE_ID
        }
    }

    @Test
    fun `record incomplete request with network capture`() {
        assertSingleNetworkRequestInSession(HttpMethod.GET, completed = false) {
            incomplete {
                errorType = NullPointerException::class.toString()
                errorMessage = "Dang nothing there"
            }
            traceId = TRACE_ID
            networkCaptureData = NETWORK_CAPTURE_DATA
        }
    }

    @Test
    fun `record incomplete request with traceparent`() {
        assertSingleNetworkRequestInSession(HttpMethod.GET, completed = false) {
            incomplete {
                errorType = NullPointerException::class.toString()
                errorMessage = "Dang nothing there"
            }
            traceId = TRACE_ID
            w3cTraceparent = TRACEPARENT
            networkCaptureData = NETWORK_CAPTURE_DATA
        }
    }

    @Test
    fun `record incomplete request through the deprecated API`() {
        assertSingleNetworkRequestInSession(HttpMethod.GET, completed = false, deprecatedApi = true) {
            incomplete {
                errorType = NullPointerException::class.toString()
                errorMessage = "Dang nothing there"
            }
            traceId = TRACE_ID
            w3cTraceparent = TRACEPARENT
            networkCaptureData = NETWORK_CAPTURE_DATA
        }
    }

    @Test
    fun `disabled URLs not recorded`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
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
                    embrace.recordNetworkRequest(DISABLED_URL, HttpMethod.GET, START_TIME, END_TIME) {
                        completed {
                            bytesSent = BYTES_SENT
                            bytesReceived = BYTES_RECEIVED
                            statusCode = 200
                        }
                    }
                    clock.tick(5)
                    embrace.recordNetworkRequest(DISABLED_URL, HttpMethod.GET, START_TIME + 1, END_TIME) {
                        incomplete {
                            errorType = NullPointerException::class.toString()
                            errorMessage = "Dang nothing there"
                        }
                    }
                    clock.tick(5)
                    embrace.recordNetworkRequest(URL, HttpMethod.GET, START_TIME + 2, END_TIME) {
                        completed {
                            bytesSent = BYTES_SENT
                            bytesReceived = BYTES_RECEIVED
                            statusCode = 200
                        }
                    }
                }
            },
            assertAction = {
                validateAndReturnExpectedNetworkSpan().attributes?.assertMatches(mapOf(
                    "url.full" to URL
                ))
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

                    repeat(2) {
                        embrace.recordNetworkRequest(URL, HttpMethod.GET, START_TIME, END_TIME) {
                            completed {
                                bytesSent = BYTES_SENT
                                bytesReceived = BYTES_RECEIVED
                                statusCode = 200
                            }
                        }
                    }
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

    @Test
    fun `a network capture rule stops at its max count with the superior storage system enabled`() {
        testRule.runTest(
            setupAction = {
                superiorStorageSystemEnabled = true
            },
            persistedRemoteConfig = RemoteConfig(
                networkCaptureRules = setOf(
                    NetworkCaptureRuleRemoteConfig(
                        id = "test",
                        duration = 0,
                        method = "GET",
                        urlRegex = "embrace.io",
                        expiresIn = 10000,
                        maxCount = 1,
                        statusCodes = setOf(200),
                    )
                )
            ),
            testCaseAction = {
                recordSession {
                    repeat(2) {
                        embrace.recordNetworkRequest(URL, HttpMethod.GET, START_TIME + it, END_TIME) {
                            completed {
                                bytesSent = BYTES_SENT
                                bytesReceived = BYTES_RECEIVED
                                statusCode = 200
                            }
                            networkCaptureData = NETWORK_CAPTURE_DATA
                        }
                    }
                }
            },
            assertAction = {
                val captured = getLogEnvelopes(1).flatMap { it.getLogsOfType(EmbType.System.NetworkCapturedRequest) }
                assertEquals(1, captured.size)
            }
        )
    }

    @Test
    fun `captured network body log carries expected attributes`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                networkCaptureRules = setOf(
                    NetworkCaptureRuleRemoteConfig(
                        id = "test",
                        duration = 0,
                        method = "GET",
                        urlRegex = "embrace.io",
                        expiresIn = 10000,
                        statusCodes = setOf(200),
                    )
                )
            ),
            testCaseAction = {
                recordSession {
                    embrace.recordNetworkRequest(URL, HttpMethod.GET, START_TIME, END_TIME) {
                        completed {
                            bytesSent = BYTES_SENT
                            bytesReceived = BYTES_RECEIVED
                            statusCode = 200
                        }
                        networkCaptureData = NETWORK_CAPTURE_DATA
                    }
                }
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val capturedLog = getSingleLogEnvelope().getLogOfType(EmbType.System.NetworkCapturedRequest)
                val attrs = checkNotNull(capturedLog.attributes)

                // the captured request body and metadata
                attrs.assertMatches(
                    mapOf(
                        EmbNetworkCapturedRequestAttributes.DURATION to END_TIME - START_TIME,
                        EmbNetworkCapturedRequestAttributes.START_TIME to START_TIME,
                        EmbNetworkCapturedRequestAttributes.END_TIME to END_TIME,
                        EmbNetworkCapturedRequestAttributes.URL to URL,
                        EmbNetworkCapturedRequestAttributes.REQUEST_BODY to "haha",
                        EmbNetworkCapturedRequestAttributes.REQUEST_SIZE to 4,
                        EmbNetworkCapturedRequestAttributes.REQUEST_QUERY to "trackMe=noooooo",
                        EmbNetworkCapturedRequestAttributes.RESPONSE_BODY to "woohoo",
                        EmbNetworkCapturedRequestAttributes.RESPONSE_SIZE to 6,
                        EmbSessionAttributes.EMB_PRIVATE_SEND_MODE to SendMode.IMMEDIATE,
                        HttpAttributes.HTTP_REQUEST_HEADER to mapOf("x-emb-test" to "holla").toString(),
                        HttpAttributes.HTTP_REQUEST_METHOD to "GET",
                        HttpAttributes.HTTP_REQUEST_BODY_SIZE to 4,
                        HttpAttributes.HTTP_RESPONSE_STATUS_CODE to 200,
                        HttpAttributes.HTTP_RESPONSE_BODY_SIZE to 6,
                        HttpAttributes.HTTP_RESPONSE_HEADER to mapOf("x-emb-response-header" to "alloh").toString(),
                        UrlAttributes.URL_FULL to "embrace.io",
                    )
                )

                assertFalse(attrs.findAttributeValue(EmbNetworkCapturedRequestAttributes.NETWORK_ID).isNullOrBlank())
                capturedLog.assertSessionIds(
                    expectedUserSessionId = session.getUserSessionId(),
                    expectedSessionPartId = session.getSessionPartId(),
                )
            }
        )
    }

    @Test
    fun `a registered modifier alters the recorded network request`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.addHttpRequestInfoModifier { info ->
                        info.url = MODIFIED_URL
                        info.httpMethod = "POST"
                    }
                    clock.tick(5)
                    embrace.recordNetworkRequest(URL, HttpMethod.GET, START_TIME, END_TIME) {
                        completed {
                            bytesSent = BYTES_SENT
                            bytesReceived = BYTES_RECEIVED
                            statusCode = 200
                        }
                    }
                }
            },
            assertAction = {
                validateAndReturnExpectedNetworkSpan().attributes?.assertMatches(
                    mapOf(
                        "url.full" to MODIFIED_URL,
                        HttpAttributes.HTTP_REQUEST_METHOD to "POST",
                    )
                )
            }
        )
    }

    @Test
    fun `a removed modifier no longer alters the recorded network request`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    val modifier = HttpRequestInfoModifier { info ->
                        info.url = MODIFIED_URL
                    }
                    embrace.addHttpRequestInfoModifier(modifier)
                    embrace.removeHttpRequestInfoModifier(modifier)
                    clock.tick(5)
                    embrace.recordNetworkRequest(URL, HttpMethod.GET, START_TIME, END_TIME) {
                        completed {
                            bytesSent = BYTES_SENT
                            bytesReceived = BYTES_RECEIVED
                            statusCode = 200
                        }
                    }
                }
            },
            assertAction = {
                validateAndReturnExpectedNetworkSpan().attributes?.assertMatches(
                    mapOf("url.full" to URL)
                )
            }
        )
    }

    @Test
    fun `modifiers do not affect the captured network body payload`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                networkCaptureRules = setOf(
                    NetworkCaptureRuleRemoteConfig(
                        id = "test",
                        duration = 0,
                        method = "GET",
                        urlRegex = "embrace.io",
                        expiresIn = 10000,
                        statusCodes = setOf(200),
                    )
                )
            ),
            testCaseAction = {
                recordSession {
                    embrace.addHttpRequestInfoModifier { info ->
                        info.url = MODIFIED_URL
                        info.httpMethod = "POST"
                    }
                    embrace.recordNetworkRequest(URL, HttpMethod.GET, START_TIME, END_TIME) {
                        completed {
                            bytesSent = BYTES_SENT
                            bytesReceived = BYTES_RECEIVED
                            statusCode = 200
                        }
                        networkCaptureData = NETWORK_CAPTURE_DATA
                    }
                }
            },
            assertAction = {
                // the network span reflects the modification...
                validateAndReturnExpectedNetworkSpan().attributes?.assertMatches(
                    mapOf(
                        "url.full" to MODIFIED_URL,
                        HttpAttributes.HTTP_REQUEST_METHOD to "POST",
                    )
                )
                // ...but the captured body payload retains the raw, unmodified request data.
                val capturedLog = getSingleLogEnvelope().getLogOfType(EmbType.System.NetworkCapturedRequest)
                checkNotNull(capturedLog.attributes).assertMatches(
                    mapOf(
                        EmbNetworkCapturedRequestAttributes.URL to URL,
                        HttpAttributes.HTTP_REQUEST_METHOD to "GET",
                    )
                )
            }
        )
    }

    @Suppress("DEPRECATION")
    private fun assertSingleNetworkRequestInSession(
        httpMethod: HttpMethod,
        url: String = URL,
        completed: Boolean = true,
        deprecatedApi: Boolean = false,
        action: NetworkRequestDsl.() -> Unit,
    ) {
        val expectedRequest = EmbraceNetworkRequestDsl(url, httpMethod, START_TIME, END_TIME).apply(action).build()

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    clock.tick(2L)
                    clock.tick(5L)
                    if (!deprecatedApi) {
                        embrace.recordNetworkRequest(url, httpMethod, START_TIME, END_TIME, action)
                    } else if (completed) {
                        embrace.recordNetworkRequest(
                            io.embrace.android.embracesdk.network.EmbraceNetworkRequest.fromCompletedRequest(
                                url,
                                httpMethod,
                                START_TIME,
                                END_TIME,
                                checkNotNull(expectedRequest.bytesSent),
                                checkNotNull(expectedRequest.bytesReceived),
                                checkNotNull(expectedRequest.responseCode),
                                expectedRequest.traceId,
                                expectedRequest.w3cTraceparent,
                                expectedRequest.networkCaptureData,
                            )
                        )
                    } else {
                        embrace.recordNetworkRequest(
                            io.embrace.android.embracesdk.network.EmbraceNetworkRequest.fromIncompleteRequest(
                                url,
                                httpMethod,
                                START_TIME,
                                END_TIME,
                                checkNotNull(expectedRequest.errorType),
                                checkNotNull(expectedRequest.errorMessage),
                                expectedRequest.traceId,
                                expectedRequest.w3cTraceparent,
                                expectedRequest.networkCaptureData,
                            )
                        )
                    }
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

                    attributes?.assertMatches(mapOf(
                        "url.full" to expectedRequest.url,
                        HttpAttributes.HTTP_REQUEST_METHOD to expectedRequest.httpMethod,
                        EmbNetworkRequestAttributes.EMB_TRACE_ID to expectedRequest.traceId,
                        EmbNetworkRequestAttributes.EMB_W3C_TRACEPARENT to expectedRequest.w3cTraceparent,
                        EmbNetworkRequestAttributes.EMB_FORWARD_TELEMETRY to expectedRequest.w3cTraceparent?.let { "true" },
                        HttpAttributes.HTTP_RESPONSE_STATUS_CODE to when {
                            completed -> expectedRequest.responseCode
                            else -> null
                        },
                        HttpAttributes.HTTP_REQUEST_BODY_SIZE to when {
                            completed -> expectedRequest.bytesSent
                            else -> null
                        },
                        HttpAttributes.HTTP_RESPONSE_BODY_SIZE to when {
                            completed -> expectedRequest.bytesReceived
                            else -> null
                        },
                        "error.type" to when {
                            completed -> null
                            else -> expectedRequest.errorType
                        },
                        ExceptionAttributes.EXCEPTION_MESSAGE to when {
                            completed -> null
                            else -> expectedRequest.errorMessage
                        },
                    ))
                }
            }
        )
    }

    private fun EmbracePayloadAssertionInterface.validateAndReturnExpectedNetworkSpan(): Span {
        val session = getSingleSessionEnvelope()

        val unfilteredSpans = checkNotNull(session.data.spans)
        val spans =
            checkNotNull(unfilteredSpans.filter { it.attributes?.findAttributeValue(HttpAttributes.HTTP_REQUEST_METHOD) != null })
        assertEquals(
            "Unexpected number of requests in sent session: ${spans.size}",
            1,
            spans.size
        )

        return spans.first()
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()

        private const val URL = "https://embrace.io"
        private const val MODIFIED_URL = "https://redacted.io"
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
