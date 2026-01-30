package io.embrace.android.embracesdk.internal.instrumentation.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.utils.NetworkUtils
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.stripUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NetworkRequestDataSourceTest {
    private lateinit var harness: NetworkRequestDataSourceTestHarness

    @Before
    fun setUp() {
        harness = NetworkRequestDataSourceTestHarness()
    }

    @Test
    fun `multiple network requests are recorded to the span service correctly`() {
        val firstUrl = "https://www.example1.com/api?foo=bar"
        logNetworkRequest(url = firstUrl)
        logNetworkRequest(
            url = "www.example2.com",
            startTime = 200,
            endTime = 300,
            statusCode = 404,
        )
        logNetworkRequest(
            url = "www.example3.com",
            startTime = 300,
            endTime = 400,
            statusCode = 500,
        )
        logNetworkRequest(
            url = "www.example4.com",
            startTime = 400,
            endTime = 500,
            statusCode = 203,
        )
        harness.dataSource.recordNetworkRequest(
            HttpNetworkRequest(
                url = "www.example5.com",
                httpMethod = "GET",
                startTime = 600L,
                endTime = 650L,
                errorType = "RuntimeException",
                errorMessage = ""
            )
        )

        harness.args.destination.createdSpans

        val expectedSpanName = "GET "
        val spans = harness.getNetworkSpans()
        assertEquals(5, spans.size)

        val strippedFirstUrl = stripUrl(firstUrl)
        val requestSpans = spans.associateBy { it.attributes["url.full"] }
        harness.assertNetworkRequest(
            spanToken = requestSpans[strippedFirstUrl],
            expectedName = "GET /api",
            expectedStartTimeMs = 100L,
            expectedEndTimeMs = 200L,
        )
        harness.assertNetworkRequest(
            spanToken = requestSpans["www.example2.com"],
            expectedName = expectedSpanName,
            expectedStartTimeMs = 200L,
            expectedEndTimeMs = 300L,
            expectedErrorCode = ErrorCodeAttribute.Failure
        )
        harness.assertNetworkRequest(
            spanToken = requestSpans["www.example3.com"],
            expectedName = expectedSpanName,
            expectedStartTimeMs = 300L,
            expectedEndTimeMs = 400L,
            expectedErrorCode = ErrorCodeAttribute.Failure
        )
        harness.assertNetworkRequest(
            spanToken = requestSpans["www.example4.com"],
            expectedName = expectedSpanName,
            expectedStartTimeMs = 400L,
            expectedEndTimeMs = 500L,
        )
        harness.assertNetworkRequest(
            spanToken = requestSpans["www.example5.com"],
            expectedName = expectedSpanName,
            expectedStartTimeMs = 600L,
            expectedEndTimeMs = 650L,
            expectedErrorCode = ErrorCodeAttribute.Failure
        )
    }

    @Test
    fun `network requests are not recorded if the URL domain is invalid`() {
        logNetworkRequest("examplecom", 100, 200)
        assertTrue(harness.getNetworkSpans().isEmpty())
    }

    @Test
    fun `network requests are not recorded if the domain count limiter does not allow it`() {
        harness.domainCountLimiter.canLog = false
        logNetworkRequest("www.example.com", 100, 200)
        assertTrue(harness.getNetworkSpans().isEmpty())
    }

    @Test
    fun `applied limit is tracked when domain count limiter does not allow logging`() {
        harness.domainCountLimiter.canLog = false
        logNetworkRequest(url = "www.example.com")
        assertEquals("network_request" to AppliedLimitType.DROP, harness.telemetryService.appliedLimits.first())
    }

    @Test
    fun `network requests with the same start time will be recorded each time`() {
        val startTime = 99L
        val endTime = 300L

        repeat(2) {
            logNetworkRequest(url = "https://embrace.io", startTime = startTime, endTime = endTime)
        }

        assertEquals(2, harness.getNetworkSpans().size)
    }

    @Test
    fun `URL parameters will be truncated`() {
        val url = "https://www.example1.com/a?b=c"
        logNetworkRequest(url = url)

        with(checkNotNull(harness.getNetworkSpans().single())) {
            assertEquals(
                NetworkUtils.stripUrl(url),
                attributes["url.full"]
            )
        }
    }

    private fun logNetworkRequest(
        url: String,
        startTime: Long = 100,
        endTime: Long = 200,
        statusCode: Int = 200,
        body: HttpNetworkRequest.HttpRequestBody? = null,
    ) {
        harness.dataSource.recordNetworkRequest(
            HttpNetworkRequest(
                url = url,
                httpMethod = "GET",
                startTime = startTime,
                endTime = endTime,
                bytesSent = 100L,
                bytesReceived = 1000L,
                statusCode = statusCode,
                body = body
            )
        )
    }
}
