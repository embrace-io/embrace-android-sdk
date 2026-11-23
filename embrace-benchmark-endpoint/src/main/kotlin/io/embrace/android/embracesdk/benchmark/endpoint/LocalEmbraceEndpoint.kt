package io.embrace.android.embracesdk.benchmark.endpoint

import io.embrace.android.testapiserver.FakeApiServer
import io.embrace.android.testapiserver.FakeBackendTls
import io.embrace.android.testapiserver.RemoteConfigEndpoint
import io.embrace.android.testapiserver.SdkBackendApi
import io.embrace.android.testapiserver.bundledFakeBackendTls
import okhttp3.Protocol
import okhttp3.mockwebserver.MockWebServer
import java.io.IOException
import java.net.InetAddress
import java.security.MessageDigest

/**
 * Embrace's server backend API endpoints, served on the device, so a benchmark launch runs the SDK the way a
 * customer's app does without anything leaving the device. The benchmarked app's `embrace-config.json` gives it a
 * placeholder app ID, since the SDK takes a different startup path without one, and points
 * `sdk_config.base_urls.config` at [LOCAL_ENDPOINT_CONFIG_BASE_URL] and `.data` at [LOCAL_ENDPOINT_DATA_BASE_URL].
 *
 * It serves HTTPS (HTTP/2, or HTTP/1.1 to a client without ALPN) with [tls], whose root the benchmarked apps trust for
 * `localhost`, so the app does the TLS work it does against Embrace's servers: a handshake on each of two connections,
 * config and data, as to the backend's two hosts, and verification of a chain shaped like theirs. Over plain HTTP, a
 * low-end phone's cold start took measurably less time than against Embrace's servers.
 *
 * It answers as Embrace's backend answers an app whose remote config is [remoteConfig]: the config fetch as
 * [RemoteConfigEndpoint] does, under [remoteConfigEtag], and every delivery 200, so the SDK counts it delivered and
 * deletes it. Anything else gets 404.
 *
 * It is a [MockWebServer] on each port running one [FakeApiServer] of [SdkBackendApi], the API the SDK calls, in the
 * benchmark's own process, beside the app it measures.
 * Request bodies are read to the end and dropped. [answered] counts what it answered on both ports, for the run's log.
 */
class LocalEmbraceEndpoint(
    /** The loopback port it serves the SDK's config endpoint on. */
    val configPort: Int = LOCAL_ENDPOINT_CONFIG_PORT,
    /** The loopback port it serves the SDK's data endpoints on. */
    val dataPort: Int = LOCAL_ENDPOINT_DATA_PORT,
    /** The remote config it serves, as the JSON body of the SDK's config fetch. */
    remoteConfig: String = REMOTE_CONFIG_JSON,
    /** The certificates it serves HTTPS with: by default those this build generated, which the benchmarked apps trust. */
    val tls: FakeBackendTls = bundledFakeBackendTls(),
) : AutoCloseable {

    private val api = FakeApiServer(SdkBackendApi(RemoteConfigEndpoint(remoteConfig, remoteConfigEtag(remoteConfig))))

    private val servers = mutableListOf<MockWebServer>()

    /** How many requests it answered, keyed by `<method> <path> <status>`. */
    val answered: Map<String, Int> get() = api.answered

    /**
     * Binds the loopback [configPort] and [dataPort] and starts serving; a second start is refused. Throws
     * [IOException] when a port is taken.
     */
    fun start() {
        check(servers.isEmpty()) { "the local Embrace endpoint is already started" }
        try {
            listOf(configPort, dataPort).forEach { servers += listen(it) }
        } catch (e: IOException) {
            close()
            servers.clear()
            throw e
        }
    }

    /** Stops serving; [answered] stays readable. */
    override fun close() {
        servers.forEach { it.shutdown() }
    }

    private fun listen(port: Int): MockWebServer {
        val server = MockWebServer().apply {
            dispatcher = api
            bodyLimit = 0
            useHttps(tls.serverCertificates.sslSocketFactory(), false)
            protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
        }
        try {
            server.start(InetAddress.getByName(LOCAL_ENDPOINT_ADDRESS), port)
        } catch (e: IOException) {
            server.shutdown()
            throw IOException("cannot listen on $LOCAL_ENDPOINT_ADDRESS:$port for the local Embrace endpoint: ${e.message}", e)
        }
        return server
    }
}

/**
 * The ETag the endpoint serves [remoteConfig] with: from its content, so a config cached from another test's endpoint,
 * which served something else, isn't revalidated as current.
 */
fun remoteConfigEtag(remoteConfig: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(remoteConfig.toByteArray(Charsets.UTF_8))
    return "\"benchmark-" + digest.take(ETAG_BYTES).joinToString("") { "%02x".format(it) } + "\""
}

/** The loopback address the endpoint listens on. */
const val LOCAL_ENDPOINT_ADDRESS: String = "127.0.0.1"

/**
 * The host the benchmarked apps reach the endpoint by: a name, resolved as the app resolves Embrace's hosts, which the
 * endpoint's certificate names.
 */
const val LOCAL_ENDPOINT_HOST: String = "localhost"

/** The loopback port of the SDK's config endpoint; the benchmarked apps' `embrace-config.json` name it. */
const val LOCAL_ENDPOINT_CONFIG_PORT: Int = 48711

/** The loopback port of the SDK's data endpoints; the benchmarked apps' `embrace-config.json` name it. */
const val LOCAL_ENDPOINT_DATA_PORT: Int = 48712

/** The base URL the benchmarked apps point the SDK's config endpoint at. */
const val LOCAL_ENDPOINT_CONFIG_BASE_URL: String = "https://$LOCAL_ENDPOINT_HOST:$LOCAL_ENDPOINT_CONFIG_PORT"

/** The base URL the benchmarked apps point the SDK's data endpoints at. */
const val LOCAL_ENDPOINT_DATA_BASE_URL: String = "https://$LOCAL_ENDPOINT_HOST:$LOCAL_ENDPOINT_DATA_PORT"

/** The remote config the endpoint serves by default: none, so the SDK runs on its defaults and the app's local config. */
const val REMOTE_CONFIG_JSON: String = "{}"

private const val ETAG_BYTES = 8
