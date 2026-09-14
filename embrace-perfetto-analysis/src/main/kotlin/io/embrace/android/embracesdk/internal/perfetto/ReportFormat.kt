package io.embrace.android.embracesdk.internal.perfetto

internal enum class ReportFormat(val flag: String) {
    MARKDOWN("markdown"),
    JSON("json"),
    HTML("html"),
    ;

    companion object {
        fun from(flag: String): ReportFormat? = entries.firstOrNull { it.flag == flag }
    }
}
