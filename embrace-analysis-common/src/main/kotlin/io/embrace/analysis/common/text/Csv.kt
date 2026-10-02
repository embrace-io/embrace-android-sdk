package io.embrace.analysis.common.text

/**
 * A small RFC-4180 reader, sufficient for `trace_processor -q` output: comma-separated fields,
 * double-quoted fields with `""` escapes, no embedded newlines inside quoted fields (Perfetto never
 * emits them). Behaves like Python's `csv.reader` on that input, so a parsed row matches the frozen
 * goldens.
 */
object Csv {

    fun parse(text: String): List<List<String>> =
        text.lineSequence().filter { it.isNotEmpty() }.map { parseLine(it) }.toList()

    fun parseLine(line: String): List<String> {
        val fields = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                inQuotes && c == '"' -> inQuotes = false
                !inQuotes && c == '"' -> inQuotes = true
                !inQuotes && c == ',' -> {
                    fields.add(field.toString())
                    field.setLength(0)
                }
                else -> field.append(c)
            }
            i++
        }
        fields.add(field.toString())
        return fields
    }
}
