package io.embrace.android.embracesdk.testframework.server

import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.TypeUtils
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.utils.threadLocal
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.GZIPInputStream
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

/**
 * Fake API server that is used to capture log/session requests made by the SDK in integration tests.
 */
internal class FakeApiServer(private val remoteConfig: RemoteConfig) : Dispatcher() {

    private enum class Endpoint {
        LOGS,
        SESSIONS,
        CONFIG
    }

    private val serializer by threadLocal { TestPlatformSerializer() }
    private val sessionRequests = ConcurrentLinkedQueue<Envelope<SessionPayload>>()
    private val logRequests = ConcurrentLinkedQueue<Envelope<LogPayload>>()
    private val configRequests = ConcurrentLinkedQueue<String>()

    /**
     * Returns a list of session envelopes in the order in which the server received them.
     */
    fun getSessionEnvelopes(): List<Envelope<SessionPayload>> = sessionRequests.toList()

    /**
     * Returns a list of log envelopes in the order in which the server received them.
     */
    fun getLogEnvelopes(): List<Envelope<LogPayload>> = logRequests.toList()

    /**
     * Returns a list of config requests in the order in which the server received them.
     * The returned value is the query parameter.
     */
    fun getConfigRequests(): List<String> = configRequests.toList()

    override fun dispatch(request: RecordedRequest): MockResponse {
        return when (val endpoint = request.asEndpoint()) {
            Endpoint.LOGS, Endpoint.SESSIONS -> handleEnvelopeRequest(request, endpoint)

            // IMPORTANT NOTE: this response is not used until the SDK next starts!
            Endpoint.CONFIG -> handleConfigRequest(request)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleEnvelopeRequest(
        request: RecordedRequest,
        endpoint: Endpoint,
    ): MockResponse {
        val envelope = deserializeEnvelope(request, endpoint)
        validateHeaders(request.headers.toMultimap().mapValues { it.value.joinToString() })

        when (endpoint) {
            Endpoint.SESSIONS -> sessionRequests.add(envelope as Envelope<SessionPayload>)
            Endpoint.LOGS -> logRequests.add(envelope as Envelope<LogPayload>)
            else -> error("Unsupported endpoint $endpoint")
        }
        return MockResponse().setResponseCode(200)
    }

    private fun handleConfigRequest(request: RecordedRequest): MockResponse {
        configRequests.add(request.requestUrl?.toUrl()?.query)

        // serialize the config response
        val configResponseBuffer = Buffer()
        val gzipSink = GzipSink(configResponseBuffer).buffer()
        serializer.toJson(
            remoteConfig,
            RemoteConfig::class.java,
            gzipSink.outputStream()
        )
        return MockResponse()
            .setBody(configResponseBuffer)
            .addHeader("etag", "server_etag_value")
            .setResponseCode(200)
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

    private fun RecordedRequest.asEndpoint(): Endpoint {
        val path = requestUrl?.toUrl()?.path
        return when (val endpoint = path?.removePrefix("/api/v2/")) {
            "logs" -> Endpoint.LOGS
            "spans" -> Endpoint.SESSIONS
            "config" -> Endpoint.CONFIG
            else -> error("Unsupported path $endpoint")
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
