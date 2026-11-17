package io.embrace.android.embracesdk.benchmark.endpoint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class HttpRequestsTest {

    @Test
    fun `a request with a content length is read whole, its header names lower-cased and its query kept in the target`() {
        val request = read("POST /v2/logs?x=1 HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 5\r\n\r\nhello")!!
        assertEquals("POST", request.method)
        assertEquals("/v2/logs?x=1", request.target)
        assertEquals("/v2/logs", request.path)
        assertEquals("127.0.0.1", request.headers["host"])
        assertEquals(5L, request.bodyBytes)
    }

    @Test
    fun `a chunked body is read across its chunks, extensions and trailer skipped`() {
        val raw = "POST /v2/spans HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n" +
            "3;ext=1\r\nabc\r\n2\r\nde\r\n0\r\nX-Trailer: t\r\n\r\n"
        assertEquals(5L, read(raw)!!.bodyBytes)
    }

    @Test
    fun `a body larger than the read buffer is read to its end`() {
        val body = "x".repeat(20_000)
        val request = read("POST /v2/spans HTTP/1.1\r\nContent-Length: ${body.length}\r\n\r\n$body")!!
        assertEquals(20_000L, request.bodyBytes)
    }

    @Test
    fun `a request with neither length nor chunks has no body, and an empty connection no request`() {
        assertEquals(0L, read("GET /v2/config?appId=a HTTP/1.1\r\n\r\n")!!.bodyBytes)
        assertNull(read(""))
    }

    @Test
    fun `the last of a repeated header wins`() {
        val request = read("GET / HTTP/1.1\r\nIf-None-Match: a\r\nif-none-match: b\r\n\r\n")!!
        assertEquals("b", request.headers["if-none-match"])
    }

    @Test
    fun `what is not a whole HTTP request is refused`() {
        listOf(
            "hello\r\n\r\n",
            "GET / FTP/1.0\r\n\r\n",
            "GET / HTTP/1.1\r\nno colon here\r\n\r\n",
            "GET / HTTP/1.1\r\nHost: x\r\n",
            "GET / HTTP/1.1\r\nHost: x",
            "POST / HTTP/1.1\r\nContent-Length: 10\r\n\r\nshort",
            "POST / HTTP/1.1\r\nContent-Length: -1\r\n\r\n",
            "POST / HTTP/1.1\r\nContent-Length: ten\r\n\r\n",
            "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\nzz\r\n",
            "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n-1\r\n",
            "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabcX\r\n0\r\n\r\n",
            "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n",
            "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nX-Trailer: t\r\n",
        ).forEach { raw ->
            assertThrows(raw, IOException::class.java) { read(raw) }
        }
    }

    private fun read(raw: String): HttpRequest? = readHttpRequest(raw.toByteArray(Charsets.ISO_8859_1).inputStream())
}
