package io.embrace.android.embracesdk.internal.resurrection

/**
 * Upper bound on the size of a cached payload file read back during resurrection. This is
 * meant as an upper bound to prevent memory exhaustion. Cached payloads are stored gzipped, so
 * this measures compressed bytes.
 */
internal const val MAX_CACHED_PAYLOAD_BYTES: Long = 3L * 1024 * 1024

/**
 * Upper bound on the size of a cached payload once it is decompressed. Session JSON compresses
 * around 8-15x, so this sits above what a payload under [MAX_CACHED_PAYLOAD_BYTES] can hold.
 * MAX_CACHED_LOG_ENVELOPE_BYTES in CachedLogEnvelopeStoreImpl mirrors this value.
 */
internal const val MAX_DECOMPRESSED_PAYLOAD_BYTES: Long = 32L * 1024 * 1024

internal const val OVERSIZED_PAYLOAD_MSG = "Cached payload file exceeds the maximum size"

internal const val OVERSIZED_DECOMPRESSED_PAYLOAD_MSG = "Cached payload exceeds the maximum size once decompressed"
