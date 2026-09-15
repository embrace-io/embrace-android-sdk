package io.embrace.android.embracesdk.internal.perfetto.trace

internal enum class TraceFormat(val label: String) {
    PERFETTO("perfetto trace"),
    UNKNOWN("not a perfetto trace"),
}
