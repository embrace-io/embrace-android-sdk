package io.embrace.android.embracesdk.benchmark.endpoint

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket
import java.util.zip.GZIPInputStream

internal class LocalEmbraceEndpointTest {

    private val client = OkHttpClient()

    @Test
    fun `the SDK's config fetch, its conditional refetch and a delivery are answered on the loopback port, and counted`() {
        LocalEmbraceEndpoint(port = freePort(), remoteConfig = CONFIG).use { endpoint ->
            endpoint.start()

            call(endpoint, "GET", "/v2/config?appId=abcde").use { first ->
                assertEquals(200, first.code)
                assertEquals(remoteConfigEtag(CONFIG), first.header("ETag"))
                assertEquals(CONFIG, decompressGzip(checkNotNull(first.body).bytes()))
            }
            call(endpoint, "GET", "/v2/config?appId=abcde", "If-None-Match" to remoteConfigEtag(CONFIG)).use {
                assertEquals(304, it.code)
            }
            call(endpoint, "POST", "/v2/spans").use { assertEquals(200, it.code) }
            call(endpoint, "GET", "/v2/spans").use { assertEquals(404, it.code) }

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
    fun `an endpoint serves the empty config by default, and starts once, on a port nothing else holds`() {
        val port = freePort()
        LocalEmbraceEndpoint(port = port).use { endpoint ->
            endpoint.start()
            call(endpoint, "GET", "/v2/config").use {
                assertEquals("{}", decompressGzip(checkNotNull(it.body).bytes()))
            }
            assertThrows(IllegalStateException::class.java) { endpoint.start() }
            LocalEmbraceEndpoint(port = port).use { second ->
                assertThrows(IOException::class.java) { second.start() }
            }
        }
    }

    @Test
    fun `a config's ETag is a quoted tag that depends on its content alone`() {
        assertEquals(remoteConfigEtag(CONFIG), remoteConfigEtag(CONFIG))
        assertNotEquals(remoteConfigEtag(CONFIG), remoteConfigEtag("{}"))
        assertTrue(remoteConfigEtag("{}"), Regex("\"benchmark-[0-9a-f]{16}\"").matches(remoteConfigEtag("{}")))
    }

    /** Calls [endpoint] at [path], asking for gzip itself as the SDK does, so OkHttp leaves a gzipped body as sent. */
    private fun call(endpoint: LocalEmbraceEndpoint, method: String, path: String, vararg headers: Pair<String, String>): Response {
        val builder = Request.Builder()
            .url("http://$LOCAL_ENDPOINT_HOST:${endpoint.port}$path")
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
    }
}
