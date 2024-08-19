package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.EventType
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.network.http.HttpMethod

public class ApiRequestMapper(
    private val urlBuilder: ApiUrlBuilder,
    private val lazyDeviceId: Lazy<String>,
    private val appId: String
) {

    private val apiUrlBuilders = Endpoint.values().associateWith {
        urlBuilder.getEmbraceUrlWithSuffix(it.version, it.path)
    }

    private fun Endpoint.asEmbraceUrl(): String = checkNotNull(apiUrlBuilders[this])

    private fun requestBuilder(url: String): ApiRequest {
        return ApiRequest(
            url = ApiRequestUrl(url),
            httpMethod = HttpMethod.POST,
            appId = appId,
            deviceId = lazyDeviceId.value,
            contentEncoding = "gzip",
            userAgent = "Embrace/a/" + BuildConfig.VERSION_NAME
        )
    }

    public fun configRequest(url: String): ApiRequest = ApiRequest(
        contentType = "application/json",
        userAgent = "Embrace/a/" + BuildConfig.VERSION_NAME,
        accept = "application/json",
        url = ApiRequestUrl(url),
        httpMethod = HttpMethod.GET,
    )

    @Suppress("UNUSED_PARAMETER")
    public fun logsEnvelopeRequest(envelope: Envelope<LogPayload>): ApiRequest {
        val url = Endpoint.LOGS.asEmbraceUrl()
        return requestBuilder(url)
    }

    @Suppress("UNUSED_PARAMETER")
    public fun sessionEnvelopeRequest(envelope: Envelope<SessionPayload>): ApiRequest {
        val url = Endpoint.SESSIONS_V2.asEmbraceUrl()
        return requestBuilder(url)
    }

    public fun sessionRequest(): ApiRequest {
        val url = Endpoint.SESSIONS_V2
        return requestBuilder(url.asEmbraceUrl())
    }

    public fun eventMessageRequest(eventMessage: EventMessage): ApiRequest {
        checkNotNull(eventMessage.event) { "event must be set" }
        val event = eventMessage.event
        checkNotNull(event.type) { "event type must be set" }
        checkNotNull(event.eventId) { "event ID must be set" }
        val url = Endpoint.EVENTS.asEmbraceUrl()
        val abbreviation = event.type.abbreviation
        val eventIdentifier: String = if (event.type == EventType.CRASH) {
            createCrashActiveEventsHeader(abbreviation, event.activeEventIds)
        } else {
            abbreviation + ":" + event.eventId
        }
        return requestBuilder(url).copy(eventId = eventIdentifier)
    }

    /**
     * Crashes are sent with a header containing the list of active stories.
     *
     * @param abbreviation the abbreviation for the event type
     * @param eventIds     the list of story IDs
     * @return the header
     */
    private fun createCrashActiveEventsHeader(
        abbreviation: String,
        eventIds: List<String>?
    ): String {
        val stories = eventIds?.joinToString(",") ?: ""
        return "$abbreviation:$stories"
    }
}
