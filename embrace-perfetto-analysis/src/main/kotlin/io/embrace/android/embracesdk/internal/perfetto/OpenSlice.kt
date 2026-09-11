package io.embrace.android.embracesdk.internal.perfetto

/** A begin event waiting for its end, held on a per thread stack while slices are built. */
internal data class OpenSlice(
    val name: String,
    val startNanos: Long,
    val depth: Int,
)
