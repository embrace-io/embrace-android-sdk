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

internal class FailedApiCallsPerEndpointTest {

    private lateinit var failedApiCalls: FailedApiCallsPerEndpoint

    @Before
    fun setUp() {
        failedApiCalls = FailedApiCallsPerEndpoint()
    }

    @Test
    fun `test adding failed api calls associated to endpoints`() {
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/sessions"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        val failedApiCall1 = DeliveryFailedApiCall(request1, "payload_filename")
        failedApiCalls.add(failedApiCall1)

        val request2 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/events"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_2",
            contentEncoding = "gzip"
        )
        val failedApiCall2 = DeliveryFailedApiCall(request2, "payload_filename")
        failedApiCalls.add(failedApiCall2)

        assertEquals(failedApiCall1, failedApiCalls.pollNextFailedApiCall())
        assertEquals(failedApiCall2, failedApiCalls.pollNextFailedApiCall())
        assertEquals(null, failedApiCalls.pollNextFailedApiCall())
    }

    @Test
    fun `test hasAnyFailedApiCalls`() {
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/sessions"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        assertFalse(failedApiCalls.hasAnyFailedApiCalls())
        failedApiCalls.add(DeliveryFailedApiCall(request1, "payload_filename"))
        assertTrue(failedApiCalls.hasAnyFailedApiCalls())
    }

    @Test
    fun `test hasNoFailedApiCalls`() {
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/sessions"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        assertTrue(failedApiCalls.hasNoFailedApiCalls())
        failedApiCalls.add(DeliveryFailedApiCall(request1, "payload_filename"))
        assertFalse(failedApiCalls.hasNoFailedApiCalls())
    }

    @Test
    fun `test pollNextFailedApiCall always return session if exists`() {
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/events"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        val failedApiCall1 = DeliveryFailedApiCall(request1, "payload_filename")
        failedApiCalls.add(failedApiCall1)

        val request2 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/sessions"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_2",
            contentEncoding = "gzip"
        )
        val failedApiCall2 = DeliveryFailedApiCall(request2, "payload_filename")
        failedApiCalls.add(failedApiCall2)

        assertEquals(failedApiCall2, failedApiCalls.pollNextFailedApiCall())
        assertEquals(failedApiCall1, failedApiCalls.pollNextFailedApiCall())
        assertEquals(null, failedApiCalls.pollNextFailedApiCall())
    }

    @Test
    fun `test pollNextFailedApiCall returns null if no failed api calls exist`() {
        assertEquals(null, failedApiCalls.pollNextFailedApiCall())
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
        val failedApiCall1 = DeliveryFailedApiCall(request1, "payload_filename")
        repeat(SESSIONS_LIMIT - 1) {
            failedApiCalls.add(failedApiCall1)
        }

        assertTrue(failedApiCalls.isBelowRetryLimit(Endpoint.SESSIONS))

        repeat(2) {
            failedApiCalls.add(failedApiCall1)
        }

        assertFalse(failedApiCalls.isBelowRetryLimit(Endpoint.SESSIONS))
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
        val failedApiCall1 = DeliveryFailedApiCall(request1, "payload_filename")
        repeat(EVENTS_LIMIT - 1) {
            failedApiCalls.add(failedApiCall1)
        }

        assertTrue(failedApiCalls.isBelowRetryLimit(Endpoint.EVENTS))

        repeat(2) {
            failedApiCalls.add(failedApiCall1)
        }

        assertFalse(failedApiCalls.isBelowRetryLimit(Endpoint.EVENTS))
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
        val failedApiCall1 = DeliveryFailedApiCall(request1, "payload_filename")
        repeat(BLOBS_LIMIT - 1) {
            failedApiCalls.add(failedApiCall1)
        }

        assertTrue(failedApiCalls.isBelowRetryLimit(Endpoint.BLOBS))

        repeat(2) {
            failedApiCalls.add(failedApiCall1)
        }

        assertFalse(failedApiCalls.isBelowRetryLimit(Endpoint.BLOBS))
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
        val failedApiCall1 = DeliveryFailedApiCall(request1, "payload_filename")
        repeat(LOGGING_LIMIT - 1) {
            failedApiCalls.add(failedApiCall1)
        }

        assertTrue(failedApiCalls.isBelowRetryLimit(Endpoint.LOGGING))

        repeat(2) {
            failedApiCalls.add(failedApiCall1)
        }

        assertFalse(failedApiCalls.isBelowRetryLimit(Endpoint.LOGGING))
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
        val failedApiCall1 = DeliveryFailedApiCall(request1, "payload_filename")
        repeat(NETWORK_LIMIT - 1) {
            failedApiCalls.add(failedApiCall1)
        }

        assertTrue(failedApiCalls.isBelowRetryLimit(Endpoint.NETWORK))

        repeat(2) {
            failedApiCalls.add(failedApiCall1)
        }

        assertFalse(failedApiCalls.isBelowRetryLimit(Endpoint.NETWORK))
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
        val failedApiCall1 = DeliveryFailedApiCall(request1, "payload_filename")
        repeat(UNKNOWN_LIMIT - 1) {
            failedApiCalls.add(failedApiCall1)
        }

        assertTrue(failedApiCalls.isBelowRetryLimit(Endpoint.UNKNOWN))

        repeat(2) {
            failedApiCalls.add(failedApiCall1)
        }

        assertFalse(failedApiCalls.isBelowRetryLimit(Endpoint.UNKNOWN))
    }
}

private const val EVENTS_LIMIT = 100
private const val BLOBS_LIMIT = 50
private const val LOGGING_LIMIT = 100
private const val NETWORK_LIMIT = 50
private const val SESSIONS_LIMIT = 100
private const val UNKNOWN_LIMIT = 50
