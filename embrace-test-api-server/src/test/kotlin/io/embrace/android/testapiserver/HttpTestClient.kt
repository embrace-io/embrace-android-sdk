package io.embrace.android.testapiserver

import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockWebServer
import java.util.zip.GZIPInputStream

/**
 * Calls [server] the way the SDK calls Embrace's server backend. It asks for gzip itself, as the SDK does, so OkHttp
 * leaves a gzipped body as sent.
 */
internal class HttpTestClient(private val server: MockWebServer) {

    private val client = OkHttpClient()

    /** Calls [path] with [method] and [headers], a POST with an empty body, and returns the reply. */
    fun call(method: String, path: String, vararg headers: Pair<String, String>): Reply {
        val builder = Request.Builder()
            .url(server.url(path))
            .header("Accept-Encoding", "gzip")
            .method(method, ByteArray(0).toRequestBody().takeIf { method == "POST" })
        headers.forEach { (name, value) -> builder.header(name, value) }
        return client.newCall(builder.build()).execute().use { response ->
            Reply(response.code, response.headers, response.body?.bytes() ?: ByteArray(0))
        }
    }

    /** A reply as a test reads it: its status [code], [headers] and the [body] as sent. */
    class Reply(val code: Int, val headers: Headers, val body: ByteArray) {

        /** The [body] decompressed from gzip, as text, as the SDK reads a config body. */
        fun decompressedBody(): String = GZIPInputStream(body.inputStream()).use { String(it.readBytes()) }
    }
}
