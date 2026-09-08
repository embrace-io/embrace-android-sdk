package io.embrace.android.embracesdk.internal.resurrection

/**
 * Upper bound on the size of a cached payload file read back during resurrection. This is
 * meant as an upper bound to prevent memory exhaustion.
 */
internal const val MAX_CACHED_PAYLOAD_BYTES: Long = 3L * 1024 * 1024

internal const val OVERSIZED_PAYLOAD_MSG = "Cached payload file exceeds the maximum size"
