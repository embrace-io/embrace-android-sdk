package io.embrace.android.testapiserver

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * The config endpoint of Embrace's server backend, as it answers the SDK's config fetch, serving the remote config
 * [json] under the ETag [etag]: gzipped, as the SDK asks for gzip and decompresses the body itself. A fetch that sends
 * [etag] in `If-None-Match`, as the SDK does once it has this config cached, gets 304 and keeps its cached copy; one
 * that sends another ETag, cached from another config, gets this one.
 */
class RemoteConfigEndpoint(
    json: String,
    val etag: String,
) {
    private val body = gzip(json.toByteArray(Charsets.UTF_8))

    /** The answer to the config fetch [request]. */
    fun respond(request: RecordedRequest): MockResponse {
        if (request.getHeader("If-None-Match") == etag) {
            return MockResponse()
                .setResponseCode(HTTP_NOT_MODIFIED)
                .addHeader("ETag", etag)
        }
        return MockResponse()
            .addHeader("Content-Type", "application/json")
            .addHeader("Content-Encoding", "gzip")
            .addHeader("ETag", etag)
            .setBody(Buffer().write(body))
    }
}

private fun gzip(bytes: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    GZIPOutputStream(out).use { it.write(bytes) }
    return out.toByteArray()
}

private const val HTTP_NOT_MODIFIED = 304
