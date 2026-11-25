package io.embrace.android.embracesdk.benchmark.endpoint

import io.embrace.android.testapiserver.bundledFakeBackendTls
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.zip.GZIPInputStream

internal class LocalEmbraceEndpointTest {

    private val tls = bundledFakeBackendTls()

    private val client = OkHttpClient.Builder()
        .sslSocketFactory(tls.clientCertificates.sslSocketFactory(), tls.clientCertificates.trustManager)
        .build()

    @Test
    fun `the SDK's config fetch and refetch are answered on the config port, deliveries on the data port, and counted`() {
        LocalEmbraceEndpoint(configPort = freePort(), dataPort = freePort(), remoteConfig = CONFIG).use { endpoint ->
            endpoint.start()

            call(endpoint.configPort, "GET", "/v2/config?appId=abcde").use { first ->
                assertEquals(200, first.code)
                assertEquals(Protocol.HTTP_2, first.protocol)
                assertEquals(remoteConfigEtag(CONFIG), first.header("ETag"))
                assertEquals(CONFIG, decompressGzip(checkNotNull(first.body).bytes()))
            }
            call(endpoint.configPort, "GET", "/v2/config?appId=abcde", "If-None-Match" to remoteConfigEtag(CONFIG)).use {
                assertEquals(304, it.code)
            }
            call(endpoint.dataPort, "POST", "/v2/spans").use {
                assertEquals(200, it.code)
                assertEquals(Protocol.HTTP_2, it.protocol)
            }
            call(endpoint.dataPort, "GET", "/v2/spans").use { assertEquals(404, it.code) }

            endpoint.close()
            assertEquals(
                mapOf(
                    "GET /v2/config 200" to 1,
                    "GET /v2/config 304" to 1,
                    "GET /v2/spans 404" to 1,
                    "POST /v2/spans 200" to 1,
                ),
                endpoint.answered,
            )
        }
    }

    @Test
    fun `an endpoint serves the empty config by default, and starts once, on ports nothing else holds`() {
        val configPort = freePort()
        val dataPort = freePort()
        LocalEmbraceEndpoint(configPort = configPort, dataPort = dataPort).use { endpoint ->
            endpoint.start()
            call(configPort, "GET", "/v2/config").use {
                assertEquals("{}", decompressGzip(checkNotNull(it.body).bytes()))
            }
            assertThrows(IllegalStateException::class.java) { endpoint.start() }
            LocalEmbraceEndpoint(configPort = freePort(), dataPort = dataPort).use { second ->
                assertThrows(IOException::class.java) { second.start() }
            }
        }
    }

    @Test
    fun `an endpoint that cannot take its data port lets go of its config port`() {
        val configPort = freePort()
        ServerSocket(0, 1, InetAddress.getByName(LOCAL_ENDPOINT_ADDRESS)).use { taken ->
            LocalEmbraceEndpoint(configPort = configPort, dataPort = taken.localPort).use { endpoint ->
                assertThrows(IOException::class.java) { endpoint.start() }
            }
        }
        LocalEmbraceEndpoint(configPort = configPort, dataPort = freePort()).use { it.start() }
    }

    @Test
    fun `a warm-up is answered as the SDK's requests are, refused before start, and leaves nothing counted`() {
        LocalEmbraceEndpoint(configPort = freePort(), dataPort = freePort(), remoteConfig = CONFIG).use { endpoint ->
            assertThrows(IllegalStateException::class.java) { endpoint.warmUp() }
            endpoint.start()
            endpoint.warmUp(rounds = 12)
            assertEquals(emptyMap<String, Int>(), endpoint.answered)

            call(endpoint.dataPort, "POST", "/v2/logs").use { assertEquals(200, it.code) }
            assertEquals(mapOf("POST /v2/logs 200" to 1), endpoint.answered)
        }
    }

    @Test
    fun `a config's ETag is a quoted tag that depends on its content alone`() {
        assertEquals(remoteConfigEtag(CONFIG), remoteConfigEtag(CONFIG))
        assertNotEquals(remoteConfigEtag(CONFIG), remoteConfigEtag("{}"))
        assertTrue(remoteConfigEtag("{}"), Regex("\"benchmark-[0-9a-f]{16}\"").matches(remoteConfigEtag("{}")))
    }

    @Test
    fun `every benchmarked app has a placeholder app ID and its endpoints pointed here`() {
        val configs = checkNotNull(System.getProperty(BENCHMARKED_CONFIGS_PROPERTY)).split(File.pathSeparator).map(::File)
        assertTrue("no benchmarked app configs", configs.isNotEmpty())
        configs.forEach { file ->
            val config = file.readText()
            val urls = BASE_URL.findAll(config).associate { it.groupValues[1] to it.groupValues[2] }
            assertEquals(
                file.path,
                mapOf("config" to LOCAL_ENDPOINT_CONFIG_BASE_URL, "data" to LOCAL_ENDPOINT_DATA_BASE_URL),
                urls,
            )
            assertEquals(file.path, "abcde", APP_ID.find(config)?.groupValues?.get(1))
        }
        assertEquals("https://localhost:$LOCAL_ENDPOINT_CONFIG_PORT", LOCAL_ENDPOINT_CONFIG_BASE_URL)
        assertEquals("https://localhost:$LOCAL_ENDPOINT_DATA_PORT", LOCAL_ENDPOINT_DATA_BASE_URL)
    }

    /**
     * Calls the endpoint's [port] at [path] by its host name, over HTTPS, asking for gzip itself as the SDK does, so
     * OkHttp leaves a gzipped body as sent.
     */
    private fun call(port: Int, method: String, path: String, vararg headers: Pair<String, String>): Response {
        val builder = Request.Builder()
            .url("https://$LOCAL_ENDPOINT_HOST:$port$path")
            .header("Accept-Encoding", "gzip")
            .method(method, "abcd".toByteArray().toRequestBody().takeIf { method == "POST" })
        headers.forEach { (name, value) -> builder.header(name, value) }
        return client.newCall(builder.build()).execute()
    }

    /** [bytes] decompressed from gzip, as text, as the SDK reads a config body. */
    private fun decompressGzip(bytes: ByteArray): String =
        GZIPInputStream(bytes.inputStream()).use { String(it.readBytes()) }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private companion object {
        const val CONFIG = "{\"user_session\":{\"inactivity_timeout_seconds\":30}}"

        // set by this module's build file, which lists the files
        const val BENCHMARKED_CONFIGS_PROPERTY = "benchmarkEndpoint.benchmarkedConfigs"
        val BASE_URL = Regex("\"(config|data)\"\\s*:\\s*\"([^\"]+)\"")
        val APP_ID = Regex("\"app_id\"\\s*:\\s*\"([^\"]+)\"")
    }
}
