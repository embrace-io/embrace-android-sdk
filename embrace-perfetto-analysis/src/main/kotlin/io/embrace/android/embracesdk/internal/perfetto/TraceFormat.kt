package io.embrace.android.embracesdk.internal.perfetto

internal enum class TraceFormat(val label: String) {
    PERFETTO("gzipped perfetto trace"),
    UNKNOWN("not a gzipped perfetto trace"),
}
