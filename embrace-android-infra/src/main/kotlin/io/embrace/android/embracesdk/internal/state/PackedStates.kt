package io.embrace.android.embracesdk.internal.state

/**
 * The form every state's value takes in one attribute on a log sent to Embrace: `name=value` for each state, joined by `;`, with
 * `name~value` for a value the SDK generated. A `;`, `=`, `~` or `\` inside a name or a value is preceded by a `\`.
 */
object PackedStates {

    private const val SEPARATOR = ';'
    private const val ASSIGN = '='
    private const val ASSIGN_SYSTEM = '~'
    private const val ESCAPE = '\\'
    private const val ESCAPED = ";=~\\"
    private const val ESTIMATED_ENTRY_LENGTH = 32

    /**
     * One state's value, as [unpack] reads it back.
     */
    class Entry(val name: String, val value: String, val system: Boolean)

    /**
     * Packs every state in [snapshot], in the order the states were declared.
     */
    fun pack(snapshot: StateSnapshot): String {
        val count = snapshot.size
        if (count == 0) {
            return ""
        }
        val out = StringBuilder(count * ESTIMATED_ENTRY_LENGTH)
        for (i in 0 until count) {
            append(out, snapshot.name(i), snapshot.value(i), snapshot.isSystem(i))
        }
        return out.toString()
    }

    fun pack(entries: List<Entry>): String {
        val out = StringBuilder(entries.size * ESTIMATED_ENTRY_LENGTH)
        for (entry in entries) {
            append(out, entry.name, entry.value, entry.system)
        }
        return out.toString()
    }

    /**
     * Reads [packed] back into its entries. An entry with no unescaped `=` or `~` is skipped.
     */
    fun unpack(packed: String): List<Entry> {
        val entries = ArrayList<Entry>()
        val name = StringBuilder()
        val value = StringBuilder()
        var assigned = false
        var system = false
        var i = 0
        while (i < packed.length) {
            val c = packed[i]
            val target = if (assigned) value else name
            when {
                c == ESCAPE && i + 1 < packed.length -> {
                    i++
                    target.append(packed[i])
                }

                c == SEPARATOR -> {
                    if (assigned) {
                        entries.add(Entry(name.toString(), value.toString(), system))
                    }
                    name.setLength(0)
                    value.setLength(0)
                    assigned = false
                }

                !assigned && (c == ASSIGN || c == ASSIGN_SYSTEM) -> {
                    assigned = true
                    system = c == ASSIGN_SYSTEM
                }

                else -> target.append(c)
            }
            i++
        }
        if (assigned) {
            entries.add(Entry(name.toString(), value.toString(), system))
        }
        return entries
    }

    private fun append(out: StringBuilder, name: String, value: String, system: Boolean) {
        if (out.isNotEmpty()) {
            out.append(SEPARATOR)
        }
        appendEscaped(out, name)
        out.append(if (system) ASSIGN_SYSTEM else ASSIGN)
        appendEscaped(out, value)
    }

    private fun appendEscaped(out: StringBuilder, text: String) {
        for (c in text) {
            if (c in ESCAPED) {
                out.append(ESCAPE)
            }
            out.append(c)
        }
    }
}
