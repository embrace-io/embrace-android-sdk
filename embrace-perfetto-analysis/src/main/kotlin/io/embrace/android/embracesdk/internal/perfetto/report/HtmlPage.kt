package io.embrace.android.embracesdk.internal.perfetto.report

private const val STYLES = "{{styles}}"
private const val SCRIPT = "{{script}}"
private const val REPORT = "{{report}}"
private const val STYLESHEET = "/report.css"
private const val TOOLKIT = "/report.js"

private object Asset

/**
 * Assembles one page from a template: the shared stylesheet, the shared drawing toolkit, and the
 * json.
 */
internal fun renderPage(template: String, json: String): String {
    val page = readAsset(template)
    check(page.contains(STYLES)) { "$template has no $STYLES to write the stylesheet into" }
    check(page.contains(SCRIPT)) { "$template has no $SCRIPT to write the toolkit into" }
    check(page.contains(REPORT)) { "$template has no $REPORT to write the report into" }
    return page
        .replace(STYLES, inlined(STYLESHEET))
        .replace(SCRIPT, inlined(TOOLKIT))
        .replace(REPORT, embed(json))
}

private fun readAsset(path: String): String {
    val stream = checkNotNull(Asset::class.java.getResourceAsStream(path)) { "$path is not on the classpath" }
    return stream.use { it.reader(Charsets.UTF_8).readText() }
}

private fun inlined(path: String): String = readAsset(path).also {
    check(!it.contains("</")) { "$path holds </, which would close the block it is inlined into" }
}

private fun embed(json: String): String = json.replace("</", "<\\/")
