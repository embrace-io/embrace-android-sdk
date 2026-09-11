package io.embrace.android.embracesdk.internal.perfetto

/**
 * One atrace `print` event, as written by [android.os.Trace] and captured by ftrace.
 *
 * [payload] is the raw buffer, e.g. `B|9874|emb-prefs-first-read` or `E|9874`. The number it
 * carries is the process tgid and is not used: slices nest per [tid].
 */
internal data class AtraceEvent(
    val tid: Int,
    val timestampNanos: Long,
    val payload: String,
)
