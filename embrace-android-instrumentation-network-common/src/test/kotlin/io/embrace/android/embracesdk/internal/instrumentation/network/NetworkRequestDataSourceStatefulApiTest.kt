package io.embrace.android.embracesdk.internal.instrumentation.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.utils.NetworkUtils
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.stripUrl
import io.opentelemetry.kotlin.semconv.ErrorAttributes
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.opentelemetry.kotlin.semconv.HttpAttributes
import io.opentelemetry.kotlin.semconv.IncubatingApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NetworkRequestDataSourceStatefulApiTest {

    private lateinit var harness: NetworkRequestDataSourceTestHarness

    @Before
    fun setUp() {
        harness = NetworkRequestDataSourceTestHarness()
    }

    @OptIn(IncubatingApi::class)
    @Test
    fun `multiple serial network requests are recorded`() {
        harness.networkSpanForwardingBehavior.networkSpanForwardingEnabled = true

        val firstUrl = "https://www.example1.com/api?foo=bar"
        runRequest(url = firstUrl)
        runRequest(
            url = "www.example2.com",
            startTime = 200,
            endTime = 300,
            statusCode = 404
        )
        runRequest(
            url = "www.example3.com",
            startTime = 300,
            endTime = 400,
            statusCode = 500,
        )
        runRequest(
            url = "www.example4.com",
            startTime = 400,
            endTime = 500,
            statusCode = 203,
        )
        runRequest(
            url = "www.example5.com",
            httpMethod = "GET",
            statusCode = null,
            startTime = 600L,
            endTime = 650L,
            errorType = "RuntimeException",
            errorMessage = "err"
        )

        val spans = harness.getNetworkSpans()
        assertEquals(5, spans.size)

        val strippedFirstUrl = stripUrl(firstUrl)
        val requestSpans = spans.associateBy { it.attributes["url.full"] }
        val firstRequestSpan = checkNotNull(requestSpans[strippedFirstUrl])
        harness.assertNetworkRequest(
            spanToken = firstRequestSpan,
            expectedName = "GET /api",
            expectedStartTimeMs = 100L,
            expectedEndTimeMs = 200L,
            expectedAttributes = mapOf(
                "url.full" to strippedFirstUrl,
                HttpAttributes.HTTP_REQUEST_METHOD to "GET",
                HttpAttributes.HTTP_RESPONSE_STATUS_CODE to "200",
                HttpAttributes.HTTP_REQUEST_BODY_SIZE to "100",
                HttpAttributes.HTTP_RESPONSE_BODY_SIZE to "1000",
                "emb.w3c_traceparent" to firstRequestSpan.asW3cTraceparent(),
                "emb.trace_id" to "fake-trace-id",
            )
        )

        harness.assertNetworkRequest(
            spanToken = requestSpans["www.example2.com"],
            expectedName = "GET ",
            expectedStartTimeMs = 200L,
            expectedEndTimeMs = 300L,
            expectedErrorCode = ErrorCodeAttribute.Failure
        )
        harness.assertNetworkRequest(
            spanToken = requestSpans["www.example3.com"],
            expectedName = "GET ",
            expectedStartTimeMs = 300L,
            expectedEndTimeMs = 400L,
            expectedErrorCode = ErrorCodeAttribute.Failure
        )
        harness.assertNetworkRequest(
            spanToken = requestSpans["www.example4.com"],
            expectedName = "GET ",
            expectedStartTimeMs = 400L,
            expectedEndTimeMs = 500L,
        )
        harness.assertNetworkRequest(
            spanToken = requestSpans["www.example5.com"],
            expectedName = "GET ",
            expectedStartTimeMs = 600L,
            expectedEndTimeMs = 650L,
            expectedErrorCode = ErrorCodeAttribute.Failure,
            expectedAttributes = mapOf(
                ErrorAttributes.ERROR_TYPE to "RuntimeException",
                ExceptionAttributes.EXCEPTION_MESSAGE to "err",
            )
        )
    }

    @Test
    fun `network requests are not recorded if the URL domain is invalid`() {
        runRequest(url = "examplecom", startTime = 100, endTime = 200)
        assertTrue(harness.getNetworkSpans().isEmpty())
    }

    @Test
    fun `network requests are not recorded if the domain count limiter does not allow it`() {
        harness.domainCountLimiter.canLog = false
        runRequest(url = "www.example.com", startTime = 100, endTime = 200)
        assertTrue(harness.getNetworkSpans().isEmpty())
    }

    @Test
    fun `applied limit is tracked when domain count limiter does not allow logging`() {
        harness.domainCountLimiter.canLog = false
        runRequest("https://www.example.com")
        assertEquals("network_request" to AppliedLimitType.DROP, harness.telemetryService.appliedLimits.first())
    }

    @Test
    fun `network requests with the same start time will be recorded each time`() {
        val startTime = 99L
        val endTime = 300L

        repeat(2) {
            runRequest(url = "https://embrace.io", startTime = startTime, endTime = endTime)
        }

        assertEquals(2, harness.getNetworkSpans().size)
    }

    @Test
    fun `URL parameters will be truncated`() {
        val url = "https://www.example1.com/a?b=c"
        runRequest(url = url)

        with(checkNotNull(harness.getNetworkSpans().single())) {
            assertEquals(
                NetworkUtils.stripUrl(url),
                attributes["url.full"]
            )
        }
    }

    @Test
    fun `URL sent with request end will be used`() {
        val url = "https://www.example1.com/blah/"
        val endUrl = "https://www.example1.com"
        runRequest(
            url = url,
            endUrl = endUrl
        )
        assertNotNull(harness.getNetworkSpans().single { it.attributes["url.full"] == endUrl })
    }

    @Test
    fun `concurrent network requests`() {
        val url = "https://api.example1.com"
        val expectedSpanName = "GET "

        val request1 = startRequest(
            url = url,
            startTime = 10L
        )

        val request2 = startRequest(
            url = url,
            startTime = 11L
        )

        val request3 = startRequest(
            url = url,
            startTime = 15L
        )

        endRequest(
            callId = request2,
            url = url,
            endTime = 1050L
        )

        endRequest(
            callId = request3,
            url = url,
            endTime = 1100L
        )

        endRequest(
            callId = request1,
            url = url,
            endTime = 1150L
        )

        val spans = harness.getNetworkSpans().sortedBy { it.startTimeMs }
        assertEquals(3, spans.size)
        harness.assertNetworkRequest(
            spanToken = spans[0],
            expectedName = expectedSpanName,
            expectedStartTimeMs = 10,
            expectedEndTimeMs = 1150,
        )
        harness.assertNetworkRequest(
            spanToken = spans[1],
            expectedName = expectedSpanName,
            expectedStartTimeMs = 11,
            expectedEndTimeMs = 1050,
        )
        harness.assertNetworkRequest(
            spanToken = spans[2],
            expectedName = expectedSpanName,
            expectedStartTimeMs = 15,
            expectedEndTimeMs = 1100,
        )
    }

    private fun runRequest(
        url: String,
        httpMethod: String = "GET",
        startTime: Long = 100,
        endTime: Long = 200,
        statusCode: Int? = 200,
        endUrl: String = url,
        errorType: String? = null,
        errorMessage: String? = null,

    ) {
        startRequest(
            url = url,
            httpMethod = httpMethod,
            startTime = startTime,
        )?.let { id ->
            endRequest(
                callId = id,
                url = endUrl,
                endTime = endTime,
                statusCode = statusCode,
                errorType = errorType,
                errorMessage = errorMessage
            )
        }
    }

    private fun startRequest(
        url: String,
        httpMethod: String = "GET",
        startTime: Long = 100,
    ): String? =
        harness.dataSource.startRequest(
            startData = RequestStartData(
                url = url,
                httpMethod = httpMethod,
                sdkClockStartTime = startTime
            )
        )

    private fun endRequest(
        callId: String?,
        url: String,
        startTime: Long = 100,
        endTime: Long = 200,
        statusCode: Int? = 200,
        bytesSent: Long? = 100L,
        bytesReceived: Long? = 1000L,
        errorType: String? = null,
        errorMessage: String? = null,
        traceId: String? = "fake-trace-id",
    ) {
        harness.dataSource.endRequest(
            RequestEndData(
                id = checkNotNull(callId),
                url = url,
                sdkClockStartTime = startTime,
                sdkClockEndTime = endTime,
                bytesSent = bytesSent,
                bytesReceived = bytesReceived,
                statusCode = statusCode,
                errorType = errorType,
                errorMessage = errorMessage,
                traceId = traceId,
            )
        )
    }
}
