package io.embrace.android.embracesdk.internal.perfetto

/**
 * A section that has begun but not yet ended, held on [TraceInterpreter]'s per-thread stack. Becomes
 * an immutable [TraceSlice] when the matching end arrives, and is discarded if one never does.
 */
internal class OpenSlice(val name: String, val startNanos: Long, val depth: Int) {
    val children: MutableList<TraceSlice> = mutableListOf()
}
