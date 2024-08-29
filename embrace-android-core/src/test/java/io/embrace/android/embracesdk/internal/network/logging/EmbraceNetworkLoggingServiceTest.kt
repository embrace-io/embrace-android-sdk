package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.fakes.FakeDomainCountLimiter
import io.embrace.android.embracesdk.fakes.FakeNetworkCaptureService
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.stripUrl
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceNetworkLoggingServiceTest {
    private lateinit var domainCountLimiter: FakeDomainCountLimiter
    private lateinit var networkCaptureService: FakeNetworkCaptureService
    private lateinit var spanService: FakeSpanService
    private lateinit var networkLoggingService: EmbraceNetworkLoggingService

    @Before
    fun setUp() {
        networkCaptureService = FakeNetworkCaptureService()
        domainCountLimiter = FakeDomainCountLimiter()
        spanService = FakeSpanService()
        networkLoggingService = EmbraceNetworkLoggingService(
            domainCountLimiter,
            networkCaptureService,
            spanService
        )
    }

    @Test
    fun `multiple network requests are recorded to the span service correctly`() {
        logNetworkRequest(
            url = "www.example1.com",
            startTime = 100,
            endTime = 200,
        )
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
        networkLoggingService.logNetworkRequest(
            EmbraceNetworkRequest.fromIncompleteRequest(
                "www.example5.com",
                HttpMethod.GET,
                600L,
                650L,
                "RuntimeException",
                ""
            )
        )

        val spans = getNetworkSpans()
        assertEquals(5, spans.size)

        val requestSpans = spans.associateBy { it.attributes?.single { attr -> attr.key == "url.full" }?.data }
        checkNotNull(requestSpans["www.example1.com"]).assertNetworkRequest(
            expectedStartTimeMs = 100L,
            expectedEndTimeMs = 200L,
        )
        checkNotNull(requestSpans["www.example2.com"]).assertNetworkRequest(
            expectedStartTimeMs = 200L,
            expectedEndTimeMs = 300L,
            expectedStatus = Span.Status.ERROR
        )
        checkNotNull(requestSpans["www.example3.com"]).assertNetworkRequest(
            expectedStartTimeMs = 300L,
            expectedEndTimeMs = 400L,
            expectedStatus = Span.Status.ERROR
        )
        checkNotNull(requestSpans["www.example4.com"]).assertNetworkRequest(
            expectedStartTimeMs = 400L,
            expectedEndTimeMs = 500L,
        )
        checkNotNull(requestSpans["www.example5.com"]).assertNetworkRequest(
            expectedStartTimeMs = 600L,
            expectedEndTimeMs = 650L,
            expectedStatus = Span.Status.ERROR
        )
    }

    @Test
    fun `network requests with network captured data are sent to the networkCaptureService`() {
        val networkCaptureData = NetworkCaptureData(
            requestHeaders = mapOf(Pair("x-emb-test", "holla")),
            requestQueryParams = "trackMe=noooooo",
            capturedRequestBody = "haha".toByteArray(),
            responseHeaders = mapOf(Pair("x-emb-response-header", "alloh")),
            capturedResponseBody = "woohoo".toByteArray(),
            dataCaptureErrorMessage = null
        )

        logNetworkRequest(
            url = "www.example.com",
            startTime = 100,
            endTime = 200,
            networkCaptureData = networkCaptureData
        )

        assertEquals(1, getNetworkSpans().size)

        // Network captured data is sent to the networkCaptureService
        assertTrue(networkCaptureService.urls.contains("www.example.com"))
    }

    @Test
    fun `network requests are not recorded if the URL domain is invalid`() {
        logNetworkRequest("examplecom", 100, 200)
        assertTrue(getNetworkSpans().isEmpty())
    }

    @Test
    fun `network requests are not recorded if the domain count limiter does not allow it`() {
        domainCountLimiter.canLog = false
        logNetworkRequest("www.example.com", 100, 200)
        assertTrue(getNetworkSpans().isEmpty())
    }

    @Test
    fun `network requests with the same start time will be recorded each time`() {
        val startTime = 99L
        val endTime = 300L

        repeat(2) {
            logNetworkRequest(url = "https://embrace.io", startTime = startTime, endTime = endTime)
        }

        assertEquals(2, getNetworkSpans().size)
    }

    @Test
    fun `URL parameters will be truncated`() {
        val url = "https://www.example1.com/a?b=c"
        logNetworkRequest(
            url = url,
            startTime = 100,
            endTime = 200,
        )

        with(checkNotNull(getNetworkSpans().single())) {
            assertEquals(stripUrl(url), attributes?.single { it.key == "url.full" }?.data)
        }
    }

    private fun logNetworkRequest(
        url: String,
        startTime: Long = 100,
        endTime: Long = 200,
        statusCode: Int = 200,
        networkCaptureData: NetworkCaptureData? = null
    ) {
        networkLoggingService.logNetworkRequest(
            EmbraceNetworkRequest.fromCompletedRequest(
                url,
                HttpMethod.GET,
                startTime,
                endTime,
                100L,
                1000L,
                statusCode,
                null,
                null,
                networkCaptureData
            )
        )
    }

    private fun getNetworkSpans() = spanService
        .createdSpans
        .filter { it.type == EmbType.Performance.Network }
        .mapNotNull { it.snapshot() }

    private fun Span.assertNetworkRequest(
        expectedStartTimeMs: Long,
        expectedEndTimeMs: Long,
        expectedStatus: Span.Status = Span.Status.UNSET
    ) {
        assertEquals(expectedStartTimeMs, startTimeNanos?.nanosToMillis())
        assertEquals(expectedEndTimeMs, endTimeNanos?.nanosToMillis())
        assertEquals(expectedStatus, status)
    }
}
