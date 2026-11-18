package io.embrace.android.embracesdk.internal.config.source

private const val MAX_ETAG_LENGTH = 256

/**
 * OkHttp parses response headers leniently but rejects non-ASCII request header values, so an ETag
 * is only replayed as `If-None-Match` if it is printable ASCII of a bounded length.
 */
internal fun isValidEtag(etag: String): Boolean =
    etag.length <= MAX_ETAG_LENGTH && etag.all { it in ' '..'~' }
