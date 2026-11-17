package io.embrace.android.embracesdk.benchmark.endpoint

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/*
 * The little HTTP/1.1 the local endpoint speaks: one request read from a connection, and the reply. The SDK sends
 * through OkHttp with a streamed body, so a delivery arrives chunked; a body with a `Content-Length` is read by its
 * length. Bodies are read to the end and dropped: the endpoint answers by method, path and headers alone.
 */

/**
 * One request as the endpoint read it: its [method], its [target] (path and query), its headers by lower-cased
 * name (the last value wins for a repeated header), and how many body bytes it sent.
 */
class HttpRequest(
    val method: String,
    val target: String,
    val headers: Map<String, String>,
    val bodyBytes: Long,
) {
    /** The [target] without its query. */
    val path: String get() = target.substringBefore('?')
}

/**
 * Reads one request from [input]: the request line, the headers up to the blank line, then the body, as
 * `Transfer-Encoding: chunked` or by its `Content-Length`, none when it has neither. Null when the stream ends
 * before a request line: a client that connected and sent nothing. Throws [IOException] for a request that is not
 * HTTP or ends early.
 */
fun readHttpRequest(input: InputStream): HttpRequest? {
    val requestLine = readLine(input) ?: return null
    val parts = requestLine.split(' ')
    if (parts.size != REQUEST_LINE_PARTS || !parts[2].startsWith("HTTP/")) {
        throw IOException("not an HTTP request line: '$requestLine'")
    }
    val headers = LinkedHashMap<String, String>()
    while (true) {
        val line = readLine(input) ?: throw IOException("the headers of ${parts[0]} ${parts[1]} end early")
        if (line.isEmpty()) {
            break
        }
        val colon = line.indexOf(':')
        if (colon <= 0) {
            throw IOException("not a header line: '$line'")
        }
        headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
    }
    val bodyBytes = when {
        headers["transfer-encoding"]?.lowercase()?.contains("chunked") == true -> skipChunked(input)
        headers["content-length"] != null -> skipFixed(input, contentLength(headers.getValue("content-length")))
        else -> 0L
    }
    return HttpRequest(parts[0], parts[1], headers, bodyBytes)
}

/** One line up to its line feed, a trailing carriage return dropped; null when the stream ends before any byte of it. */
private fun readLine(input: InputStream): String? {
    val line = ByteArrayOutputStream()
    while (true) {
        val b = input.read()
        if (b == -1) {
            if (line.size() == 0) {
                return null
            }
            throw IOException("a line ends without a line feed")
        }
        if (b == '\n'.code) {
            break
        }
        line.write(b)
    }
    return line.toString(Charsets.ISO_8859_1.name()).removeSuffix("\r")
}

/** Reads a chunked body to its end, trailer included, and returns how many bytes its chunks held. */
private fun skipChunked(input: InputStream): Long {
    var total = 0L
    while (true) {
        val sizeLine = readLine(input) ?: throw IOException("a chunked body ends before its last chunk")
        val size = sizeLine.substringBefore(';').trim().toLongOrNull(HEX)
            ?: throw IOException("not a chunk size: '$sizeLine'")
        if (size < 0) {
            throw IOException("not a chunk size: '$sizeLine'")
        }
        if (size == 0L) {
            skipTrailer(input)
            return total
        }
        total += skipFixed(input, size)
        if (readLine(input)?.isEmpty() != true) {
            throw IOException("a chunk of $size bytes is not followed by its line end")
        }
    }
}

/** A chunked body's trailer, up to and including its blank line. */
private fun skipTrailer(input: InputStream) {
    while (true) {
        val line = readLine(input) ?: throw IOException("a chunked body's trailer ends early")
        if (line.isEmpty()) {
            return
        }
    }
}

/** Reads exactly [length] bytes and drops them; throws [IOException] when the stream ends first. */
private fun skipFixed(input: InputStream, length: Long): Long {
    val buffer = ByteArray(SKIP_BUFFER_BYTES)
    var remaining = length
    while (remaining > 0) {
        val read = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
        if (read == -1) {
            throw IOException("a body of $length bytes ends early")
        }
        remaining -= read
    }
    return length
}

private fun contentLength(value: String): Long {
    val length = value.toLongOrNull() ?: throw IOException("not a content length: '$value'")
    if (length < 0) {
        throw IOException("a negative content length: $length")
    }
    return length
}

private const val REQUEST_LINE_PARTS = 3
private const val HEX = 16
private const val SKIP_BUFFER_BYTES = 8 * 1024
