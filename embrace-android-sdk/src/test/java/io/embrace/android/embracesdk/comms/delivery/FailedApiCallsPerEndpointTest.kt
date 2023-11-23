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

        assertEquals(2, failedApiCalls.failedApiCallsCount())
        assertEquals(1, failedApiCalls.failedApiCallsCount(Endpoint.SESSIONS))
        assertEquals(1, failedApiCalls.failedApiCallsCount(Endpoint.EVENTS))
        assertEquals(
            listOf("request_1"),
            failedApiCalls.get(Endpoint.SESSIONS)?.map { failedCall -> failedCall.apiRequest.eventId }
        )
        assertEquals(
            listOf("request_2"),
            failedApiCalls.get(Endpoint.EVENTS)?.map { failedCall -> failedCall.apiRequest.eventId }
        )
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
    fun `test get returns null if no failed api calls exist`() {
        assertEquals(null, failedApiCalls.get(Endpoint.EVENTS))
    }

    @Test
    fun `test get returns failed api calls if exist`() {
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

        assertEquals(failedApiCall1, failedApiCalls.get(Endpoint.EVENTS)?.first())
        failedApiCalls.clear()
        assertEquals(null, failedApiCalls.get(Endpoint.EVENTS))
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
}
