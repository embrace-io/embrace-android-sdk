package io.embrace.android.embracesdk.internal.perfetto

private const val TEMPLATE = "/report.html"
private const val PLACEHOLDER = "{{report}}"

private object Page

internal fun renderHtml(report: StatsReport): String {
    val stream = checkNotNull(Page::class.java.getResourceAsStream(TEMPLATE)) { "$TEMPLATE is not on the classpath" }
    val template = stream.use { it.reader().readText() }
    check(template.contains(PLACEHOLDER)) { "$TEMPLATE has no $PLACEHOLDER to write the report into" }
    return template.replace(PLACEHOLDER, embed(renderJson(report)))
}

private fun embed(json: String): String = json.replace("</", "<\\/")
