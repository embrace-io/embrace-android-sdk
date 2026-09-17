package io.embrace.android.embracesdk.internal.delivery.scheduling

/**
 * Upper bound on the size of a payload the SDK will attempt to upload. Payload files are stored
 * gzipped and sent as the request body verbatim, so this measures compressed bytes and is the
 * ceiling that actually applies on the wire.
 */
internal const val MAX_UPLOAD_PAYLOAD_BYTES: Long = 3L * 1024 * 1024

internal const val OVERSIZED_UPLOAD_MSG = "Payload exceeds the maximum size for upload"
