package io.embrace.android.embracesdk.benchmark.endpoint

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Embrace's endpoints, served on the device, so a benchmark launch runs the SDK the way a customer's app does
 * without anything leaving the device. The benchmarked app's `embrace-config.json` gives it a placeholder app ID,
 * since the SDK takes a different startup path without one, and points `sdk_config.base_urls.config` and `.data`
 * at [LOCAL_ENDPOINT_BASE_URL]. It answers as [endpointResponse] says, serving [remoteConfig] as the app's remote config.
 *
 * It runs in the benchmark's own process, beside the app it measures, and serves one connection at a time on a
 * single daemon thread at the lowest priority. Request bodies are read to the end and dropped, so the work it adds
 * while a launch is measured is reading bytes off a local socket. [answered] counts what it answered, for the run's
 * log.
 */
class LocalEmbraceEndpoint(
    /** The loopback port it listens on. */
    val port: Int = LOCAL_ENDPOINT_PORT,
    /** The remote config it serves, as the JSON body of the SDK's config fetch. */
    private val remoteConfig: String = REMOTE_CONFIG_JSON,
) : Closeable {

    private val counts = ConcurrentHashMap<String, Int>()

    @Volatile
    private var server: ServerSocket? = null
    private var thread: Thread? = null

    /** How many requests it answered, keyed by `<method> <path> <status>`. */
    val answered: Map<String, Int> get() = counts.toSortedMap()

    /** Binds the loopback [port] and starts serving; a second start is refused. Throws [IOException] when the port is taken. */
    fun start() {
        check(server == null) { "the local Embrace endpoint is already started" }
        val socket = ServerSocket()
        try {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName(LOCAL_ENDPOINT_HOST), port))
        } catch (e: IOException) {
            socket.close()
            throw IOException("cannot listen on $LOCAL_ENDPOINT_HOST:$port for the local Embrace endpoint: ${e.message}", e)
        }
        server = socket
        thread = Thread({ serve(socket) }, THREAD_NAME).apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    /** Stops serving; [answered] stays readable. */
    override fun close() {
        server?.close()
        thread?.join(JOIN_MS)
    }

    private fun serve(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: IOException) {
                // closed by close()
                return
            }
            client.use { handle(it) }
        }
    }

    /** Reads one request from [client] and answers it; a connection that sends no whole request gets nothing. */
    private fun handle(client: Socket) {
        val request = try {
            client.soTimeout = READ_TIMEOUT_MS
            readHttpRequest(BufferedInputStream(client.getInputStream())) ?: return
        } catch (_: IOException) {
            counts.merge(MALFORMED_KEY, 1, Int::plus)
            return
        }
        val response = endpointResponse(request, remoteConfig)
        counts.merge("${request.method} ${request.path} ${response.status}", 1, Int::plus)
        try {
            client.getOutputStream().apply {
                write(httpResponseBytes(response))
                flush()
            }
        } catch (_: IOException) {
            // the client went away before reading the reply
        }
    }
}

/** The loopback address the endpoint listens on. */
const val LOCAL_ENDPOINT_HOST: String = "127.0.0.1"

/** The loopback port the endpoint listens on; the benchmarked apps' `embrace-config.json` name it in their base URLs. */
const val LOCAL_ENDPOINT_PORT: Int = 48711

/** The base URL the benchmarked apps point the SDK's config and data endpoints at. */
const val LOCAL_ENDPOINT_BASE_URL: String = "http://$LOCAL_ENDPOINT_HOST:$LOCAL_ENDPOINT_PORT"

/** The [LocalEmbraceEndpoint.answered] key for connections that sent no whole request. */
const val MALFORMED_KEY: String = "malformed"

private const val THREAD_NAME = "emb-local-endpoint"
private const val READ_TIMEOUT_MS = 10_000
private const val JOIN_MS = 2_000L
