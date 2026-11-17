package io.embrace.android.embracesdk.benchmark.endpoint

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

/**
 * A reply the local endpoint sends: its [status], extra headers (`Content-Length` and `Connection` are added by
 * [httpResponseBytes]), and its [body] as sent on the wire.
 */
class EndpointResponse(
    val status: Int,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
)

/**
 * How the local endpoint answers [request], as Embrace's backend answers an app whose remote config is [remoteConfig]
 * (by default none):
 *
 * - the config fetch (`GET <config>/v2/config`) gets [remoteConfig], gzipped (the SDK asks for gzip and gunzips the
 *   body itself), with its ETag ([remoteConfigEtag]); a fetch that sends that ETag in `If-None-Match`, as the SDK does
 *   once it has the config cached, gets 304 and keeps its cached copy, and one that sends another ETag, cached from
 *   another config, gets this one;
 * - a delivery (any POST) gets 200, so the SDK counts it delivered and deletes it;
 * - anything else gets 404.
 */
fun endpointResponse(request: HttpRequest, remoteConfig: String = REMOTE_CONFIG_JSON): EndpointResponse = when {
    request.method == "GET" && request.path.endsWith(CONFIG_PATH) -> configResponse(request, remoteConfig)
    request.method == "POST" -> EndpointResponse(HTTP_OK, emptyList(), ByteArray(0))
    else -> EndpointResponse(HTTP_NOT_FOUND, emptyList(), ByteArray(0))
}

/**
 * The ETag the endpoint serves [remoteConfig] with: from its content, so a config cached from another test's endpoint,
 * which served something else, isn't revalidated as current.
 */
fun remoteConfigEtag(remoteConfig: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(remoteConfig.toByteArray(Charsets.UTF_8))
    return "\"benchmark-" + digest.take(ETAG_BYTES).joinToString("") { "%02x".format(it) } + "\""
}

/** The whole reply for [response]: status line, headers, then the body, and the connection closed after it. */
fun httpResponseBytes(response: EndpointResponse): ByteArray {
    val head = StringBuilder("HTTP/1.1 ${response.status} ${reasonPhrase(response.status)}\r\n")
    response.headers.forEach { (name, value) ->
        head.append("$name: $value\r\n")
    }
    head.append("Content-Length: ${response.body.size}\r\n")
    head.append("Connection: close\r\n\r\n")
    return head.toString().toByteArray(Charsets.ISO_8859_1) + response.body
}

/** The remote config the endpoint serves by default: none, so the SDK runs on its defaults and the app's local config. */
const val REMOTE_CONFIG_JSON: String = "{}"

/** The SDK's remote config path under its config base URL. */
const val CONFIG_PATH: String = "/v2/config"

private fun configResponse(request: HttpRequest, remoteConfig: String): EndpointResponse {
    val etag = remoteConfigEtag(remoteConfig)
    if (request.headers["if-none-match"] == etag) {
        return EndpointResponse(HTTP_NOT_MODIFIED, listOf("ETag" to etag), ByteArray(0))
    }
    return EndpointResponse(
        HTTP_OK,
        listOf(
            "Content-Type" to "application/json",
            "Content-Encoding" to "gzip",
            "ETag" to etag,
        ),
        gzip(remoteConfig.toByteArray(Charsets.UTF_8)),
    )
}

private fun gzip(bytes: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    GZIPOutputStream(out).use { it.write(bytes) }
    return out.toByteArray()
}

private fun reasonPhrase(status: Int): String = when (status) {
    HTTP_OK -> "OK"
    HTTP_NOT_MODIFIED -> "Not Modified"
    HTTP_NOT_FOUND -> "Not Found"
    else -> "Status"
}

private const val ETAG_BYTES = 8
private const val HTTP_OK = 200
private const val HTTP_NOT_MODIFIED = 304
private const val HTTP_NOT_FOUND = 404
