package io.embrace.android.embracesdk.network.logging

import io.embrace.android.embracesdk.fakes.FakeDomainCountLimiter
import io.embrace.android.embracesdk.fakes.FakeNetworkCaptureService
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.utils.at
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
        logNetworkRequest("www.example1.com", 100, 200)
        logNetworkRequest("www.example2.com", 200, 300)
        logNetworkRequest("www.example3.com", 300, 400)
        logNetworkRequest("www.example4.com", 400, 500)

        val spans = spanService
            .createdSpans
            .filter { it.attributes.containsKey("http.request.method") }
            .mapNotNull { it.snapshot() }

        assertEquals(4, spans.size)

        val sortedRequests = spans.sortedBy { it.startTimeUnixNano }
        assertEquals("www.example1.com", sortedRequests.at(0)?.attributes?.first { it.key == "url.full" }?.data)
        assertEquals("www.example2.com", sortedRequests.at(1)?.attributes?.first { it.key == "url.full" }?.data)
        assertEquals("www.example3.com", sortedRequests.at(2)?.attributes?.first { it.key == "url.full" }?.data)
        assertEquals("www.example4.com", sortedRequests.at(3)?.attributes?.first { it.key == "url.full" }?.data)
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

        logNetworkRequest("www.example.com", 100, 200, networkCaptureData)

        // Network request is recorded correctly
        val spans = spanService
            .createdSpans
            .filter { it.attributes.containsKey("http.request.method") }
            .mapNotNull { it.snapshot() }
        assertEquals(1, spans.size)

        // Network captured data is sent to the networkCaptureService
        assertTrue(networkCaptureService.urls.contains("www.example.com"))
    }

    @Test
    fun `network requests are not recorded if the URL domain is invalid`() {
        logNetworkRequest("examplecom", 100, 200)

        val spans = spanService
            .createdSpans
            .filter { it.attributes.containsKey("http.request.method") }
            .mapNotNull { it.snapshot() }

        assertTrue(spans.isEmpty())
    }

    @Test
    fun `network requests are not recorded if the domain count limiter does not allow it`() {
        domainCountLimiter.canLog = false
        logNetworkRequest("www.example.com", 100, 200)

        val spans = spanService
            .createdSpans
            .filter { it.attributes.containsKey("http.request.method") }
            .mapNotNull { it.snapshot() }

        assertTrue(spans.isEmpty())
    }

    @Test
    fun `network requests with the same start time will be recorded each time`() {
        val startTime = 99L
        val endTime = 300L

        repeat(2) {
            logNetworkRequest(url = "https://embrace.io", startTime = startTime, endTime = endTime)
        }

        assertEquals(2, spanService.createdSpans.size)
    }

    private fun logNetworkRequest(url: String, startTime: Long = 100, endTime: Long = 200, networkCaptureData: NetworkCaptureData? = null) {
        networkLoggingService.logNetworkRequest(
            EmbraceNetworkRequest.fromCompletedRequest(
                /* url = */ url,
                /* httpMethod = */ HttpMethod.GET,
                /* startTime = */ startTime,
                /* endTime = */ endTime,
                /* bytesSent = */ 100L,
                /* bytesReceived = */ 1000L,
                /* statusCode = */ 200,
                /* traceId = */ null,
                /* w3cTraceparent = */ null,
                /* networkCaptureData = */ networkCaptureData
            )
        )
    }
}
