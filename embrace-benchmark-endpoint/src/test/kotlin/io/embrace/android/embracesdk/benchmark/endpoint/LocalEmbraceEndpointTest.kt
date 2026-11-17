package io.embrace.android.embracesdk.benchmark.endpoint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

class LocalEmbraceEndpointTest {

    @Test
    fun `the SDK's config fetch, its conditional refetch and a delivery are answered over a real socket, and counted`() {
        val config = "{\"user_session\":{\"inactivity_timeout_seconds\":30}}"
        LocalEmbraceEndpoint(port = freePort(), remoteConfig = config).use { endpoint ->
            endpoint.start()

            val first = exchange(endpoint, "GET /v2/config?appId=abcde HTTP/1.1\r\nAccept-Encoding: gzip\r\n\r\n".toByteArray())
            assertTrue(String(first, Charsets.ISO_8859_1), String(first, Charsets.ISO_8859_1).startsWith("HTTP/1.1 200 OK\r\n"))
            assertEquals(config, gunzip(bodyOf(first)))

            val refetch = "GET /v2/config?appId=abcde HTTP/1.1\r\nIf-None-Match: ${remoteConfigEtag(config)}\r\n\r\n"
            assertTrue(String(exchange(endpoint, refetch.toByteArray()), Charsets.ISO_8859_1).startsWith("HTTP/1.1 304 Not Modified\r\n"))

            val delivery = "POST /v2/spans HTTP/1.1\r\nContent-Encoding: gzip\r\nTransfer-Encoding: chunked\r\n\r\n" +
                "4\r\nabcd\r\n0\r\n\r\n"
            assertTrue(String(exchange(endpoint, delivery.toByteArray()), Charsets.ISO_8859_1).startsWith("HTTP/1.1 200 OK\r\n"))

            assertEquals("not a request", "", String(exchange(endpoint, "not http\r\n\r\n".toByteArray())))

            endpoint.close()
            assertEquals(
                mapOf(
                    "GET /v2/config 200" to 1,
                    "GET /v2/config 304" to 1,
                    "POST /v2/spans 200" to 1,
                    MALFORMED_KEY to 1,
                ),
                endpoint.answered,
            )
        }
    }

    @Test
    fun `an endpoint starts once, on a port nothing else holds`() {
        val port = freePort()
        LocalEmbraceEndpoint(port = port).use { endpoint ->
            endpoint.start()
            assertThrows(IllegalStateException::class.java) { endpoint.start() }
            LocalEmbraceEndpoint(port = port).use { second ->
                assertThrows(IOException::class.java) { second.start() }
            }
        }
    }

    @Test
    fun `every benchmarked app has a placeholder app ID and its endpoints pointed here`() {
        val configs = checkNotNull(System.getProperty(BENCHMARKED_CONFIGS_PROPERTY)).split(File.pathSeparator).map(::File)
        assertTrue("no benchmarked app configs", configs.isNotEmpty())
        configs.forEach { file ->
            val config = file.readText()
            val urls = BASE_URL.findAll(config).associate { it.groupValues[1] to it.groupValues[2] }
            assertEquals(file.path, mapOf("config" to LOCAL_ENDPOINT_BASE_URL, "data" to LOCAL_ENDPOINT_BASE_URL), urls)
            assertEquals(file.path, "abcde", APP_ID.find(config)?.groupValues?.get(1))
        }
        assertEquals("http://127.0.0.1:$LOCAL_ENDPOINT_PORT", LOCAL_ENDPOINT_BASE_URL)
    }

    /** Sends [raw] on a new connection to [endpoint] and returns everything it replied before closing. */
    private fun exchange(endpoint: LocalEmbraceEndpoint, raw: ByteArray): ByteArray {
        Socket(LOCAL_ENDPOINT_HOST, endpoint.port).use { socket ->
            socket.getOutputStream().apply {
                write(raw)
                flush()
            }
            socket.shutdownOutput()
            return socket.getInputStream().readBytes()
        }
    }

    /** The body of a whole reply: everything after its blank line. */
    private fun bodyOf(reply: ByteArray): ByteArray {
        val text = String(reply, Charsets.ISO_8859_1)
        val start = text.indexOf("\r\n\r\n") + 4
        return reply.copyOfRange(start, reply.size)
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private companion object {
        // set by this module's build file, which lists the files
        const val BENCHMARKED_CONFIGS_PROPERTY = "benchmarkEndpoint.benchmarkedConfigs"
        val BASE_URL = Regex("\"(config|data)\"\\s*:\\s*\"([^\"]+)\"")
        val APP_ID = Regex("\"app_id\"\\s*:\\s*\"([^\"]+)\"")
    }
}
