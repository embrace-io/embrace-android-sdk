package io.embrace.android.embracesdk.benchmark.endpoint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.GZIPInputStream

class EndpointResponsesTest {

    @Test
    fun `a first config fetch gets the remote config, by default the empty one, gzipped, with its ETag`() {
        val response = endpointResponse(request("GET", "/v2/config?appId=abcde&deviceId=d"))
        assertEquals(200, response.status)
        assertEquals("gzip", header(response, "Content-Encoding"))
        assertEquals("application/json", header(response, "Content-Type"))
        assertEquals(remoteConfigEtag("{}"), header(response, "ETag"))
        assertEquals("{}", gunzip(response.body))

        val configured = endpointResponse(request("GET", "/v2/config"), remoteConfig = CONFIG)
        assertEquals(CONFIG, gunzip(configured.body))
        assertEquals(remoteConfigEtag(CONFIG), header(configured, "ETag"))
    }

    @Test
    fun `a config fetch that sends the served config's ETag gets 304 with no body`() {
        val response = endpointResponse(request("GET", "/v2/config?appId=abcde", "if-none-match" to remoteConfigEtag(CONFIG)), CONFIG)
        assertEquals(304, response.status)
        assertEquals(remoteConfigEtag(CONFIG), header(response, "ETag"))
        assertEquals(0, response.body.size)
    }

    @Test
    fun `a config fetch with another config's ETag gets this config`() {
        val response = endpointResponse(request("GET", "/v2/config", "if-none-match" to remoteConfigEtag("{}")), CONFIG)
        assertEquals(200, response.status)
        assertEquals(CONFIG, gunzip(response.body))
    }

    @Test
    fun `a config's ETag is a quoted tag that depends on its content alone`() {
        assertEquals(remoteConfigEtag(CONFIG), remoteConfigEtag(CONFIG))
        assertNotEquals(remoteConfigEtag(CONFIG), remoteConfigEtag("{}"))
        assertTrue(remoteConfigEtag("{}"), Regex("\"benchmark-[0-9a-f]{16}\"").matches(remoteConfigEtag("{}")))
    }

    @Test
    fun `a delivery is 200 whatever its path, and anything else 404`() {
        assertEquals(200, endpointResponse(request("POST", "/v2/spans")).status)
        assertEquals(200, endpointResponse(request("POST", "/v2/logs")).status)
        assertEquals(404, endpointResponse(request("GET", "/v2/spans")).status)
        assertEquals(404, endpointResponse(request("PUT", "/v2/config")).status)
    }

    @Test
    fun `a reply carries its status, headers, length and body, and closes the connection`() {
        val bytes = httpResponseBytes(EndpointResponse(304, listOf("ETag" to "\"e\""), ByteArray(0)))
        assertEquals(
            "HTTP/1.1 304 Not Modified\r\nETag: \"e\"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
            String(bytes, Charsets.ISO_8859_1),
        )
        val withBody = String(httpResponseBytes(EndpointResponse(299, emptyList(), "ab".toByteArray())), Charsets.ISO_8859_1)
        assertTrue(withBody, withBody.startsWith("HTTP/1.1 299 Status\r\nContent-Length: 2\r\n"))
        assertTrue(withBody, withBody.endsWith("\r\n\r\nab"))
    }

    private fun request(method: String, target: String, vararg headers: Pair<String, String>) =
        HttpRequest(method, target, headers.toMap(), 0L)

    private fun header(response: EndpointResponse, name: String): String? =
        response.headers.lastOrNull { it.first == name }?.second

    private companion object {
        const val CONFIG = "{\"user_session\":{\"inactivity_timeout_seconds\":30}}"
    }
}

/** [bytes] gunzipped, as the SDK reads a config body. */
internal fun gunzip(bytes: ByteArray): String = GZIPInputStream(bytes.inputStream()).use { String(it.readBytes()) }
