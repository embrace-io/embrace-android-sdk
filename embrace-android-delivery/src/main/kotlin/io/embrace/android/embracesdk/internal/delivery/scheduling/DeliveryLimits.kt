package io.embrace.android.embracesdk.internal.delivery.scheduling

/**
 * Upper bound on the size of a payload the SDK will attempt to upload.
 */
internal const val MAX_UPLOAD_PAYLOAD_BYTES: Long = 3L * 1024 * 1024

internal const val OVERSIZED_UPLOAD_MSG = "Payload exceeds the maximum size for upload"
