package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.payload.AppInfo
import io.embrace.android.embracesdk.payload.BlobMessage
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkCapturedCall
import io.embrace.android.embracesdk.payload.NetworkEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ApiRequestMapperTest {

    companion object {
        private const val BASE_URL = "http://example.com/api"
        private const val CONFIG_URL = "http://example.com/config"
    }

    private val deviceId = lazy { "deviceId" }
    private val mapper = ApiRequestMapper(
        urlBuilder = EmbraceApiUrlBuilder(
            BASE_URL,
            CONFIG_URL,
            "appId",
            deviceId,
            lazy { "appVersionName" }
        ),
        lazyDeviceId = deviceId,
        appId = "appId"
    )

    @Test
    fun testConfigRequest() {
        with(mapper.configRequest(CONFIG_URL)) {
            assertEquals(CONFIG_URL, url.toString())
            assertEquals("application/json", contentType)
            assertEquals("application/json", accept)
            assertTrue(userAgent.startsWith("Embrace/a/"))
            assertEquals(HttpMethod.GET, httpMethod)
        }
    }

    @Test
    fun testLogRequest() {
        val request = mapper.logRequest(
            EventMessage(
                Event(
                    type = EventType.INFO_LOG,
                    eventId = "eventId",
                    messageId = "messageId"
                )
            )
        )
        request.assertCoreFieldsPopulated("/v1/log/logging")
        assertEquals("il:messageId", request.logId)
    }

    @Test
    fun testSessionRequest() {
        val request = mapper.sessionRequest()
        request.assertCoreFieldsPopulated("/v1/log/sessions")
    }

    @Test
    fun testEventMessageRequest() {
        val request = mapper.eventMessageRequest(
            EventMessage(
                Event(
                    type = EventType.INFO_LOG,
                    eventId = "eventId"
                )
            )
        )
        request.assertCoreFieldsPopulated("/v1/log/events")
        assertEquals("il:eventId", request.eventId)
    }

    @Test
    fun testCrashRequest() {
        val request = mapper.eventMessageRequest(
            EventMessage(
                Event(
                    type = EventType.CRASH,
                    eventId = "eventId",
                    activeEventIds = listOf("activeEventId1", "activeEventId2")
                )
            )
        )
        request.assertCoreFieldsPopulated("/v1/log/events")
        assertEquals("c:activeEventId1,activeEventId2", request.eventId)
    }

    @Test
    fun testNetworkEventRequest() {
        val request = mapper.networkEventRequest(
            NetworkEvent(
                "eventId",
                AppInfo(),
                deviceId.value,
                "eventId",
                NetworkCapturedCall(),
                "timestamp",
                null,
                null
            )
        )
        request.assertCoreFieldsPopulated("/v1/log/network")
        assertEquals("n:eventId", request.logId)
    }

    @Test
    fun testAeiBlobRequest() {
        val request = mapper.aeiBlobRequest(BlobMessage())
        request.assertCoreFieldsPopulated("/v1/log/blobs")
    }

    private fun ApiRequest.assertCoreFieldsPopulated(endpoint: String) {
        assertEquals("$BASE_URL$endpoint", url.toString())
        assertEquals(HttpMethod.POST, httpMethod)
        assertEquals("appId", appId)
        assertEquals("deviceId", deviceId)
        assertEquals("gzip", contentEncoding)
    }
}
