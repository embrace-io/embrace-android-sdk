package io.embrace.android.embracesdk.benchmark.endpoint

import io.embrace.android.testapiserver.FakeApiServer
import io.embrace.android.testapiserver.RemoteConfigEndpoint
import io.embrace.android.testapiserver.SdkBackendApi
import okhttp3.mockwebserver.MockWebServer
import java.io.IOException
import java.net.InetAddress
import java.security.MessageDigest

/**
 * Embrace's server backend API endpoints, served on the device, so a benchmark launch runs the SDK the way a
 * customer's app does without anything leaving the device. The benchmarked app's `embrace-config.json` gives it a
 * placeholder app ID, since the SDK takes a different startup path without one, and points
 * `sdk_config.base_urls.config` and `.data` at [LOCAL_ENDPOINT_BASE_URL].
 *
 * It answers as Embrace's backend answers an app whose remote config is [remoteConfig]: the config fetch as
 * [RemoteConfigEndpoint] does, under [remoteConfigEtag], and every delivery 200, so the SDK counts it delivered and
 * deletes it. Anything else gets 404.
 *
 * It is a [MockWebServer] running a [FakeApiServer] of [SdkBackendApi], the API the SDK calls, in the benchmark's own
 * process, beside the app it measures.
 * Request bodies are read to the end and dropped. [answered] counts what it answered, for the run's log.
 */
class LocalEmbraceEndpoint(
    /** The loopback port it listens on. */
    val port: Int = LOCAL_ENDPOINT_PORT,
    /** The remote config it serves, as the JSON body of the SDK's config fetch. */
    remoteConfig: String = REMOTE_CONFIG_JSON,
) : AutoCloseable {

    private val api = FakeApiServer(SdkBackendApi(RemoteConfigEndpoint(remoteConfig, remoteConfigEtag(remoteConfig))))

    @Volatile
    private var server: MockWebServer? = null

    /** How many requests it answered, keyed by `<method> <path> <status>`. */
    val answered: Map<String, Int> get() = api.answered

    /** Binds the loopback [port] and starts serving; a second start is refused. Throws [IOException] when the port is taken. */
    fun start() {
        check(server == null) { "the local Embrace endpoint is already started" }
        val mockServer = MockWebServer().apply {
            dispatcher = api
            bodyLimit = 0
        }
        try {
            mockServer.start(InetAddress.getByName(LOCAL_ENDPOINT_HOST), port)
        } catch (e: IOException) {
            mockServer.shutdown()
            throw IOException("cannot listen on $LOCAL_ENDPOINT_HOST:$port for the local Embrace endpoint: ${e.message}", e)
        }
        server = mockServer
    }

    /** Stops serving; [answered] stays readable. */
    override fun close() {
        server?.shutdown()
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
const val LOCAL_ENDPOINT_HOST: String = "127.0.0.1"

/** The loopback port the endpoint listens on; the benchmarked apps' `embrace-config.json` name it in their base URLs. */
const val LOCAL_ENDPOINT_PORT: Int = 48711

/** The base URL the benchmarked apps point the SDK's config and data endpoints at. */
const val LOCAL_ENDPOINT_BASE_URL: String = "http://$LOCAL_ENDPOINT_HOST:$LOCAL_ENDPOINT_PORT"

/** The remote config the endpoint serves by default: none, so the SDK runs on its defaults and the app's local config. */
const val REMOTE_CONFIG_JSON: String = "{}"

private const val ETAG_BYTES = 8
