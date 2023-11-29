package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.EmbraceApiService.Companion.Endpoint
import io.embrace.android.embracesdk.comms.api.EmbraceUrl
import io.embrace.android.embracesdk.network.http.HttpMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class PendingApiCallsTest {

    private lateinit var pendingApiCalls: PendingApiCalls

    @Before
    fun setUp() {
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
    fun `test hasAnyPendingApiCall`() {
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/sessions"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        assertFalse(pendingApiCalls.hasAnyPendingApiCall())
        pendingApiCalls.add(PendingApiCall(request1, "payload_filename"))
        assertTrue(pendingApiCalls.hasAnyPendingApiCall())
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
    fun `test isBellowRetryLimit for sessions`() {
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/sessions"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        val pendingApiCall1 = PendingApiCall(request1, "payload_filename")
        repeat(SESSIONS_LIMIT - 1) {
            pendingApiCalls.add(pendingApiCall1)
        }

        assertTrue(pendingApiCalls.isBelowRetryLimit(Endpoint.SESSIONS))

        repeat(2) {
            pendingApiCalls.add(pendingApiCall1)
        }

        assertFalse(pendingApiCalls.isBelowRetryLimit(Endpoint.SESSIONS))
    }

    @Test
    fun `test isBellowRetryLimit for events`() {
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/events"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        val pendingApiCall1 = PendingApiCall(request1, "payload_filename")
        repeat(EVENTS_LIMIT - 1) {
            pendingApiCalls.add(pendingApiCall1)
        }

        assertTrue(pendingApiCalls.isBelowRetryLimit(Endpoint.EVENTS))

        repeat(2) {
            pendingApiCalls.add(pendingApiCall1)
        }

        assertFalse(pendingApiCalls.isBelowRetryLimit(Endpoint.EVENTS))
    }

    @Test
    fun `test isBellowRetryLimit for blobs`() {
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/blobs"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        val pendingApiCall1 = PendingApiCall(request1, "payload_filename")
        repeat(BLOBS_LIMIT - 1) {
            pendingApiCalls.add(pendingApiCall1)
        }

        assertTrue(pendingApiCalls.isBelowRetryLimit(Endpoint.BLOBS))

        repeat(2) {
            pendingApiCalls.add(pendingApiCall1)
        }

        assertFalse(pendingApiCalls.isBelowRetryLimit(Endpoint.BLOBS))
    }

    @Test
    fun `test isBellowRetryLimit for logging`() {
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/logging"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        val pendingApiCall1 = PendingApiCall(request1, "payload_filename")
        repeat(LOGGING_LIMIT - 1) {
            pendingApiCalls.add(pendingApiCall1)
        }

        assertTrue(pendingApiCalls.isBelowRetryLimit(Endpoint.LOGGING))

        repeat(2) {
            pendingApiCalls.add(pendingApiCall1)
        }

        assertFalse(pendingApiCalls.isBelowRetryLimit(Endpoint.LOGGING))
    }

    @Test
    fun `test isBellowRetryLimit for network`() {
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/network"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        val pendingApiCall1 = PendingApiCall(request1, "payload_filename")
        repeat(NETWORK_LIMIT - 1) {
            pendingApiCalls.add(pendingApiCall1)
        }

        assertTrue(pendingApiCalls.isBelowRetryLimit(Endpoint.NETWORK))

        repeat(2) {
            pendingApiCalls.add(pendingApiCall1)
        }

        assertFalse(pendingApiCalls.isBelowRetryLimit(Endpoint.NETWORK))
    }

    @Test
    fun `test isBellowRetryLimit for unknown`() {
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/any"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        val pendingApiCall1 = PendingApiCall(request1, "payload_filename")
        repeat(UNKNOWN_LIMIT - 1) {
            pendingApiCalls.add(pendingApiCall1)
        }

        assertTrue(pendingApiCalls.isBelowRetryLimit(Endpoint.UNKNOWN))

        repeat(2) {
            pendingApiCalls.add(pendingApiCall1)
        }

        assertFalse(pendingApiCalls.isBelowRetryLimit(Endpoint.UNKNOWN))
    }
}

private const val EVENTS_LIMIT = 100
private const val BLOBS_LIMIT = 50
private const val LOGGING_LIMIT = 100
private const val NETWORK_LIMIT = 50
private const val SESSIONS_LIMIT = 100
private const val UNKNOWN_LIMIT = 50
