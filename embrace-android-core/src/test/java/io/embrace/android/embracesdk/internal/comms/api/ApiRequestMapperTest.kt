package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Event
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.EventType
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.network.http.HttpMethod
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
            assertEquals(CONFIG_URL, url.url)
            assertEquals("application/json", contentType)
            assertEquals("application/json", accept)
            assertTrue(userAgent.startsWith("Embrace/a/"))
            assertEquals(HttpMethod.GET, httpMethod)
        }
    }

    @Test
    fun testLogsRequest() {
        val request = mapper.logsEnvelopeRequest(
            Envelope(
                data = LogPayload(
                    logs = listOf(
                        Log(
                            traceId = "traceId",
                            spanId = "spanId",
                            timeUnixNano = 1234567890,
                            severityText = "severityText",
                            severityNumber = 1,
                            body = "a message",
                            attributes = listOf(Attribute("key", "value")),
                        )
                    )
                )
            )
        )
        request.assertCoreFieldsPopulated("/v2/logs")
    }

    @Test
    fun testSessionRequest() {
        val request = mapper.sessionRequest()
        request.assertCoreFieldsPopulated("/v2/spans")
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

    private fun ApiRequest.assertCoreFieldsPopulated(endpoint: String) {
        assertEquals("$BASE_URL$endpoint", url.url)
        assertEquals(HttpMethod.POST, httpMethod)
        assertEquals("appId", appId)
        assertEquals("deviceId", deviceId)
        assertEquals("gzip", contentEncoding)
    }
}
