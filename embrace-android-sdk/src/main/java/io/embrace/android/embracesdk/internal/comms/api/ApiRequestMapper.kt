package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.internal.EventType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent

internal class ApiRequestMapper(
    private val urlBuilder: ApiUrlBuilder,
    private val lazyDeviceId: Lazy<String>,
    private val appId: String
) {

    private val apiUrlBuilders = Endpoint.values().associateWith {
        urlBuilder.getEmbraceUrlWithSuffix(it.version, it.path)
    }

    private fun Endpoint.asEmbraceUrl(): EmbraceUrl {
        val urlString: String = checkNotNull(apiUrlBuilders[this])
        return EmbraceUrl.create(urlString)
    }

    private fun requestBuilder(url: EmbraceUrl): ApiRequest {
        return ApiRequest(
            url = url,
            httpMethod = HttpMethod.POST,
            appId = appId,
            deviceId = lazyDeviceId.value,
            contentEncoding = "gzip"
        )
    }

    fun configRequest(url: String) = ApiRequest(
        contentType = "application/json",
        userAgent = "Embrace/a/" + BuildConfig.VERSION_NAME,
        accept = "application/json",
        url = EmbraceUrl.create(url),
        httpMethod = HttpMethod.GET,
    )

    fun logRequest(
        eventMessage: EventMessage
    ): ApiRequest {
        checkNotNull(eventMessage.event) { "event must be set" }
        val event = eventMessage.event
        val type = checkNotNull(event.type) { "event type must be set" }
        checkNotNull(event.eventId) { "event ID must be set" }
        val url = Endpoint.LOGGING.asEmbraceUrl()
        val abbreviation = type.abbreviation
        val logIdentifier = abbreviation + ":" + event.messageId
        return requestBuilder(url).copy(logId = logIdentifier)
    }

    @Suppress("UNUSED_PARAMETER")
    fun logsEnvelopeRequest(envelope: Envelope<LogPayload>): ApiRequest {
        val url = Endpoint.LOGS.asEmbraceUrl()
        return requestBuilder(url)
    }

    @Suppress("UNUSED_PARAMETER")
    fun sessionEnvelopeRequest(envelope: Envelope<SessionPayload>): ApiRequest {
        val url = Endpoint.SESSIONS_V2.asEmbraceUrl()
        return requestBuilder(url)
    }

    fun sessionRequest(): ApiRequest {
        val url = Endpoint.SESSIONS_V2
        return requestBuilder(url.asEmbraceUrl())
    }

    fun eventMessageRequest(eventMessage: EventMessage): ApiRequest {
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

    fun networkEventRequest(networkEvent: NetworkEvent): ApiRequest {
        val url = Endpoint.NETWORK.asEmbraceUrl()
        val abbreviation = EventType.NETWORK_LOG.abbreviation
        val networkIdentifier = "$abbreviation:${networkEvent.eventId}"
        return requestBuilder(url).copy(logId = networkIdentifier)
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
