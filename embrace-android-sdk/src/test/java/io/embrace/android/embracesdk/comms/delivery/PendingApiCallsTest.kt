package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.EmbraceUrl
import io.embrace.android.embracesdk.comms.api.Endpoint
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.worker.ScheduledWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class PendingApiCallsTest {

    private lateinit var worker: ScheduledWorker
    private lateinit var pendingApiCalls: PendingApiCalls

    @Before
    fun setUp() {
        worker = ScheduledWorker(BlockingScheduledExecutorService(blockingMode = false))
        pendingApiCalls = PendingApiCalls()
    }

    @Test
    fun `test adding pending api calls associated to endpoints`() {
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/sessions"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        val pendingApiCall1 = PendingApiCall(request1, "payload_filename")
        pendingApiCalls.add(pendingApiCall1)

        val request2 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/events"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_2",
            contentEncoding = "gzip"
        )
        val pendingApiCall2 = PendingApiCall(request2, "payload_filename")
        pendingApiCalls.add(pendingApiCall2)

        assertEquals(pendingApiCall1, pendingApiCalls.pollNextPendingApiCall())
        assertEquals(pendingApiCall2, pendingApiCalls.pollNextPendingApiCall())
        assertEquals(null, pendingApiCalls.pollNextPendingApiCall())
    }

    @Test
    fun `test hasPendingApiCallsToSend`() {
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/sessions"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        assertFalse(pendingApiCalls.hasPendingApiCallsToSend())
        pendingApiCalls.add(PendingApiCall(request1, "payload_filename"))
        assertTrue(pendingApiCalls.hasPendingApiCallsToSend())
    }

    @Test
    fun `test pollNextPendingApiCall always return session if exists`() {
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/events"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        val pendingApiCall1 = PendingApiCall(request1, "payload_filename")
        pendingApiCalls.add(pendingApiCall1)

        val request2 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/sessions"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_2",
            contentEncoding = "gzip"
        )
        val pendingApiCall2 = PendingApiCall(request2, "payload_filename")
        pendingApiCalls.add(pendingApiCall2)

        assertEquals(pendingApiCall2, pendingApiCalls.pollNextPendingApiCall())
        assertEquals(pendingApiCall1, pendingApiCalls.pollNextPendingApiCall())
        assertEquals(null, pendingApiCalls.pollNextPendingApiCall())
    }

    @Test
    fun `test pollNextPendingApiCall returns null if no pending api calls exist`() {
        assertEquals(null, pendingApiCalls.pollNextPendingApiCall())
    }

    @Test
    fun `test add on a full queue, removes the oldest API call and adds the new one`() {
        Endpoint.values().forEach {
            pendingApiCalls = PendingApiCalls()
            val queueLimit = it.getMaxPendingApiCalls()
            val path = it.path
            repeat(queueLimit) {
                val request = ApiRequest(
                    url = EmbraceUrl.create("http://test.url/$path"),
                    httpMethod = HttpMethod.POST,
                    appId = "test_app_id_1",
                    deviceId = "test_device_id",
                    eventId = "request_$it",
                    contentEncoding = "gzip"
                )
                val pendingApiCall = PendingApiCall(request, "payload_filename")

                pendingApiCalls.add(pendingApiCall)
            }

            val exceedingRequest = ApiRequest(
                url = EmbraceUrl.create("http://test.url/$path"),
                httpMethod = HttpMethod.POST,
                appId = "test_app_id_1",
                deviceId = "test_device_id",
                eventId = "request_exceeding",
                contentEncoding = "gzip"
            )
            val exceedingPendingApiCall = PendingApiCall(exceedingRequest, "payload_filename")

            pendingApiCalls.add(exceedingPendingApiCall)

            val headApiCall = pendingApiCalls.pollNextPendingApiCall()

            // Verify that after adding an api call when queue is full, the head of the queue is the second one added
            assertTrue(headApiCall?.apiRequest?.eventId == "request_1")

            // Verify that the exceeding api call was added to the queue
            repeat(queueLimit - 2) {
                pendingApiCalls.pollNextPendingApiCall()
            }
            assertEquals(exceedingPendingApiCall, pendingApiCalls.pollNextPendingApiCall())
        }
    }

    @Test
    fun `test pollNextPendingApiCall doesn't return api calls from rate limited endpoint`() {
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/events"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        val pendingApiCall1 = PendingApiCall(request1, "payload_filename")
        pendingApiCalls.add(pendingApiCall1)

        val endpoint = Endpoint.EVENTS
        with(endpoint) {
            updateRateLimitStatus()
            scheduleRetry(
                scheduledWorker = worker,
                1000
            ) {}
        }
        assertEquals(null, pendingApiCalls.pollNextPendingApiCall())

        endpoint.clearRateLimit()
        assertEquals(pendingApiCall1, pendingApiCalls.pollNextPendingApiCall())
    }

    private fun Endpoint.getMaxPendingApiCalls(): Int {
        return when (this) {
            Endpoint.EVENTS -> 100
            Endpoint.BLOBS -> 50
            Endpoint.LOGGING -> 100
            Endpoint.LOGS -> 10
            Endpoint.NETWORK -> 50
            Endpoint.SESSIONS -> 100
            Endpoint.UNKNOWN -> 50
            Endpoint.SESSIONS_V2 -> 100
        }
    }
}
