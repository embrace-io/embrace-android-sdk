package io.embrace.android.embracesdk.internal.comms.delivery

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.internal.comms.api.ApiRequest
import io.embrace.android.embracesdk.internal.comms.api.ApiRequestUrl
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.comms.api.limiter
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.network.http.HttpMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class PendingApiCallsTest {

    private lateinit var worker: BackgroundWorker
    private lateinit var pendingApiCalls: PendingApiCalls
    private lateinit var queue: PendingApiCallQueue

    @Before
    fun setUp() {
        worker = BackgroundWorker(
            BlockingScheduledExecutorService(blockingMode = false)
        )
        pendingApiCalls = PendingApiCalls()
        queue = PendingApiCallQueue(pendingApiCalls)
    }

    @Test
    fun `test adding pending api calls associated to endpoints`() {
        val request1 = ApiRequest(
            url = ApiRequestUrl("http://test.url/spans"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip",
            userAgent = ""
        )
        val pendingApiCall1 = PendingApiCall(request1, "payload_filename")
        queue.add(pendingApiCall1)

        val request2 = ApiRequest(
            url = ApiRequestUrl("http://test.url/events"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_2",
            contentEncoding = "gzip",
            userAgent = ""
        )
        val pendingApiCall2 = PendingApiCall(request2, "payload_filename")
        queue.add(pendingApiCall2)

        assertEquals(pendingApiCall1, queue.pollNextPendingApiCall())
        assertEquals(pendingApiCall2, queue.pollNextPendingApiCall())
        assertEquals(null, queue.pollNextPendingApiCall())
    }

    @Test
    fun `test hasPendingApiCallsToSend`() {
        val request1 = ApiRequest(
            url = ApiRequestUrl("http://test.url/sessions"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip",
            userAgent = ""
        )
        assertFalse(queue.hasPendingApiCallsToSend())
        queue.add(PendingApiCall(request1, "payload_filename"))
        assertTrue(queue.hasPendingApiCallsToSend())
    }

    @Test
    fun `test pollNextPendingApiCall always return session if exists`() {
        val request1 = ApiRequest(
            url = ApiRequestUrl("http://test.url/events"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip",
            userAgent = ""
        )
        val pendingApiCall1 = PendingApiCall(request1, "payload_filename")
        queue.add(pendingApiCall1)

        val request2 = ApiRequest(
            url = ApiRequestUrl("http://test.url/spans"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_2",
            contentEncoding = "gzip",
            userAgent = ""
        )
        val pendingApiCall2 = PendingApiCall(request2, "payload_filename")
        queue.add(pendingApiCall2)

        assertEquals(pendingApiCall2, queue.pollNextPendingApiCall())
        assertEquals(pendingApiCall1, queue.pollNextPendingApiCall())
        assertEquals(null, queue.pollNextPendingApiCall())
    }

    @Test
    fun `test pollNextPendingApiCall returns null if no pending api calls exist`() {
        assertEquals(null, queue.pollNextPendingApiCall())
    }

    @Test
    fun `test add on a full queue, removes the oldest API call and adds the new one`() {
        Endpoint.values().forEach {
            pendingApiCalls = PendingApiCalls()
            val queueLimit = it.getMaxPendingApiCalls()
            val path = it.path
            repeat(queueLimit) {
                val request = ApiRequest(
                    url = ApiRequestUrl("http://test.url/$path"),
                    httpMethod = HttpMethod.POST,
                    appId = "test_app_id_1",
                    deviceId = "test_device_id",
                    eventId = "request_$it",
                    contentEncoding = "gzip",
                    userAgent = ""
                )
                val pendingApiCall = PendingApiCall(request, "payload_filename")

                queue.add(pendingApiCall)
            }

            val exceedingRequest = ApiRequest(
                url = ApiRequestUrl("http://test.url/$path"),
                httpMethod = HttpMethod.POST,
                appId = "test_app_id_1",
                deviceId = "test_device_id",
                eventId = "request_exceeding",
                contentEncoding = "gzip",
                userAgent = ""
            )
            val exceedingPendingApiCall = PendingApiCall(exceedingRequest, "payload_filename")

            queue.add(exceedingPendingApiCall)

            val headApiCall = queue.pollNextPendingApiCall()

            // Verify that after adding an api call when queue is full, the head of the queue is the second one added
            assertTrue(headApiCall?.apiRequest?.eventId == "request_1")

            // Verify that the exceeding api call was added to the queue
            repeat(queueLimit - 2) {
                queue.pollNextPendingApiCall()
            }
            assertEquals(exceedingPendingApiCall, queue.pollNextPendingApiCall())
        }
    }

    @Test
    fun `test pollNextPendingApiCall doesn't return api calls from rate limited endpoint`() {
        val request1 = ApiRequest(
            url = ApiRequestUrl("http://test.url/events"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip",
            userAgent = ""
        )
        val pendingApiCall1 = PendingApiCall(request1, "payload_filename")
        queue.add(pendingApiCall1)

        val endpoint = Endpoint.EVENTS
        with(endpoint.limiter) {
            updateRateLimitStatus()
            scheduleRetry(
                worker = worker,
                1000
            ) {}
        }
        assertEquals(null, queue.pollNextPendingApiCall())

        endpoint.limiter.clearRateLimit()
        assertEquals(pendingApiCall1, queue.pollNextPendingApiCall())
    }

    private fun Endpoint.getMaxPendingApiCalls(): Int {
        return when (this) {
            Endpoint.EVENTS -> 100
            Endpoint.LOGS -> 10
            Endpoint.SESSIONS -> 100
            Endpoint.UNKNOWN -> 50
            Endpoint.SESSIONS_V2 -> 100
        }
    }
}
