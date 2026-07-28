package io.embrace.android.embracesdk.internal.instrumentation.network

/**
 * Returns an interned string for the status codes an app is most likely to see, avoiding an
 * allocation for the common cases. Anything else falls back to [toString].
 *
 * Redirects are deliberately absent: HTTP clients follow them by default, so the code that gets
 * recorded here is the one the destination returned.
 */
fun Int.toStatusCodeString(): String = when (this) {
    200 -> "200"
    201 -> "201"
    204 -> "204"
    304 -> "304"
    400 -> "400"
    401 -> "401"
    403 -> "403"
    404 -> "404"
    500 -> "500"
    503 -> "503"
    else -> toString()
}
