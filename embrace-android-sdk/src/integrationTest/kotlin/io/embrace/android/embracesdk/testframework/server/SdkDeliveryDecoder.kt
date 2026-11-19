package io.embrace.android.embracesdk.testframework.server

import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.utils.threadLocal
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.testapiserver.SdkBackendEndpoint
import io.opentelemetry.kotlin.semconv.LogAttributes
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPInputStream
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

/**
 * Decodes what the SDK sends the fake of Embrace's server backend in integration tests, as each request arrives: the
 * log and session envelopes, attachments and config fetches, kept in the order they arrived for assertions, and tells
 * [deliveryTracer] about each request.
 */
internal class SdkDeliveryDecoder(
    private val deliveryTracer: DeliveryTracer,
) {

    private val serializer by threadLocal { TestPlatformSerializer() }
    private val sessionRequests = CopyOnWriteArrayList<Envelope<SessionPartPayload>>()
    private val logRequests = CopyOnWriteArrayList<Envelope<LogPayload>>()
    private val attachments = CopyOnWriteArrayList<List<FormPart>>()
    private val configRequests = CopyOnWriteArrayList<String>()
    private val configRequestEtags = CopyOnWriteArrayList<String?>()
    private val payloadTypesHeaders = CopyOnWriteArrayList<String>()

    /**
     * Returns a list of session envelopes in the order in which the server received them.
     */
    fun getSessionEnvelopes(): List<Envelope<SessionPartPayload>> = sessionRequests.toList()

    /**
     * Returns a list of log envelopes in the order in which the server received them.
     */
    fun getLogEnvelopes(): List<Envelope<LogPayload>> = logRequests.toList()

    /**
     * Returns a list of attachments in the order in which the server received them.
     */
    fun getAttachments(): List<List<FormPart>> = attachments.toList()

    /**
     * Returns a list of config requests in the order in which the server received them.
     * The returned value is the query parameter.
     */
    fun getConfigRequests(): List<String> = configRequests.toList()

    /**
     * Returns the If-None-Match header of each config request in the order in which the server received them.
     * The value is null when the request did not send the header.
     */
    fun getConfigRequestEtags(): List<String?> = configRequestEtags.toList()

    /**
     * Returns a list of X-EM-PAYLOAD-TYPES headers in the order in which the server received them.
     */
    fun getPayloadTypesHeaders(): List<String> = payloadTypesHeaders.toList()

    /** Decodes [request], which called [backendEndpoint], before the fake backend answers it. */
    fun onRequest(backendEndpoint: SdkBackendEndpoint, request: RecordedRequest) {
        val endpoint = backendEndpoint.sdkEndpoint()
        deliveryTracer.onServerReceivedRequest(endpoint.name)
        when (endpoint) {
            Endpoint.LOGS, Endpoint.SESSIONS -> handleEnvelopeRequest(request, endpoint)
            Endpoint.ATTACHMENTS -> handleAttachmentRequest(request)

            // IMPORTANT NOTE: the config response is not used until the SDK next starts!
            Endpoint.CONFIG -> handleConfigRequest(request)
        }
    }

    private fun handleEnvelopeRequest(
        request: RecordedRequest,
        endpoint: Endpoint,
    ) {
        val envelope = deserializeEnvelope(request, endpoint)
        val headers = request.headers.toMultimap().mapValues { it.value.joinToString() }
        validateHeaders(headers)

        // Capture X-EM-PAYLOAD-TYPES header
        headers["x-em-payload-types"]?.let { payloadTypesHeaders.add(it) }

        when (endpoint) {
            Endpoint.SESSIONS -> handleSessionRequest(endpoint, envelope)
            Endpoint.LOGS -> handleLogRequest(endpoint, envelope)
            else -> error("Unsupported endpoint $endpoint")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleSessionRequest(endpoint: Endpoint, envelope: Envelope<*>) {
        val obj = envelope as Envelope<SessionPartPayload>
        sessionRequests.add(obj)
        deliveryTracer.onServerCompletedRequest(endpoint.name, obj.getUserSessionId())
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleLogRequest(endpoint: Endpoint, envelope: Envelope<*>) {
        val obj = envelope as Envelope<LogPayload>
        logRequests.add(obj)
        deliveryTracer.onServerCompletedRequest(
            endpoint.name,
            obj.getLastLog().attributes?.findAttributeValue(LogAttributes.LOG_RECORD_UID) ?: ""
        )
    }

    private fun handleAttachmentRequest(request: RecordedRequest) {
        try {
            MultipartFormReader().read(request).let { parts ->
                attachments.add(parts)
            }
        } catch (exc: Throwable) {
            throw IllegalStateException("Failed to handle attachment request", exc)
        }
    }

    private fun handleConfigRequest(request: RecordedRequest) {
        configRequestEtags.add(request.getHeader("If-None-Match"))
        configRequests.add(request.requestUrl?.toUrl()?.query)
        deliveryTracer.onServerCompletedRequest("config", "")
    }

    private fun validateHeaders(headers: Map<String, String>) {
        with(headers) {
            assertEquals("application/json", get("accept"))
            assertNotNull(get("user-agent"))
            assertEquals("application/json", get("content-type"))
            assertNotNull(get("x-em-aid"))
            assertNotNull(get("x-em-did"))
        }
    }

    private fun SdkBackendEndpoint.sdkEndpoint(): Endpoint = when (this) {
        SdkBackendEndpoint.LOGS -> Endpoint.LOGS
        SdkBackendEndpoint.SESSIONS -> Endpoint.SESSIONS
        SdkBackendEndpoint.CONFIG -> Endpoint.CONFIG
        SdkBackendEndpoint.ATTACHMENTS -> Endpoint.ATTACHMENTS
    }

    private fun deserializeEnvelope(request: RecordedRequest, endpoint: Endpoint): Envelope<*> {
        try {
            val envelopeSerializer = when (endpoint) {
                Endpoint.LOGS -> Envelope.logEnvelopeSerializer
                Endpoint.SESSIONS -> Envelope.sessionEnvelopeSerializer
                else -> error("Unsupported endpoint $endpoint")
            }
            GZIPInputStream(request.body.inputStream()).use { stream ->
                return serializer.fromJson(stream, envelopeSerializer)
            }
        } catch (exc: Exception) {
            throw IllegalStateException("Failed to deserialize request envelope", exc)
        }
    }
}
