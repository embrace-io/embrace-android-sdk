package io.embrace.android.embracesdk.testframework.server

import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.TypeUtils
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.utils.threadLocal
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.GZIPInputStream
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

/**
 * Fake API server that is used to capture log/session requests made by the SDK in integration tests.
 */
internal class FakeApiServer : Dispatcher() {

    private val serializer by threadLocal { TestPlatformSerializer() }
    private val sessionRequests = ConcurrentLinkedQueue<Envelope<SessionPayload>>()
    private val logRequests = ConcurrentLinkedQueue<Envelope<LogPayload>>()

    /**
     * Returns a list of session envelopes in the order in which the server received them.
     */
    fun getSessionEnvelopes(): List<Envelope<SessionPayload>> = sessionRequests.toList()

    /**
     * Returns a list of log envelopes in the order in which the server received them.
     */
    fun getLogEnvelopes(): List<Envelope<LogPayload>> = logRequests.toList()

    @Suppress("UNCHECKED_CAST")
    override fun dispatch(request: RecordedRequest): MockResponse {
        val endpoint = findRequestEndpoint(request)
        val envelope = deserializeEnvelope(request, endpoint)
        validateHeaders(request.headers.toMultimap().mapValues { it.value.joinToString() })

        when (endpoint) {
            Endpoint.SESSIONS -> sessionRequests.add(envelope as Envelope<SessionPayload>)
            Endpoint.LOGS -> logRequests.add(envelope as Envelope<LogPayload>)
            else -> error("Unsupported request type $endpoint")
        }
        return MockResponse().setResponseCode(200)
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

    private fun findRequestEndpoint(request: RecordedRequest): Endpoint {
        return when (val path = request.path?.removePrefix("/api/v2/")) {
            Endpoint.LOGS.path -> Endpoint.LOGS
            Endpoint.SESSIONS.path -> Endpoint.SESSIONS
            else -> error("Unsupported path $path")
        }
    }

    private fun deserializeEnvelope(request: RecordedRequest, endpoint: Endpoint): Envelope<*> {
        try {
            val type = when (endpoint) {
                Endpoint.LOGS -> LogPayload::class
                Endpoint.SESSIONS -> SessionPayload::class
                else -> error("Unsupported endpoint $endpoint")
            }
            val envelopeType = TypeUtils.parameterizedType(Envelope::class, type)
            GZIPInputStream(request.body.inputStream()).use { stream ->
                return serializer.fromJson(stream, envelopeType)
            }
        } catch (exc: Exception) {
            throw IllegalStateException("Failed to deserialize request envelope", exc)
        }
    }
}
