package io.embrace.android.embracesdk.internal.perfetto.report

internal enum class ReportFormat(val flag: String, val extension: String) {
    MARKDOWN("markdown", "md"),
    JSON("json", "json"),
    HTML("html", "html"),
    ;

    companion object {
        fun from(flag: String): ReportFormat? = entries.firstOrNull { it.flag == flag }
    }
}
