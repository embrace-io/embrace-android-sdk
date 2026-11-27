package io.embrace.android.embracesdk.internal.state

/**
 * The packed form of a set of state values: `name=value` for each state joined by `;`, with `name~value` for a value
 * the SDK generated, and a `\` before any `;`, `=`, `~` or `\` inside a name or a value.
 */
object StatePacking {

    private const val ESCAPE = '\\'
    private const val SEPARATOR = ';'
    private const val OBSERVED = '='
    private const val GENERATED = '~'
    private const val RESERVED = "\\;=~"

    fun pack(snapshot: StateSnapshot): String = buildString {
        for (index in 0 until snapshot.size) {
            if (index > 0) {
                append(SEPARATOR)
            }
            appendEscaped(snapshot.name(index))
            append(if (snapshot.isSystem(index)) GENERATED else OBSERVED)
            appendEscaped(snapshot.value(index))
        }
    }

    /**
     * Calls [action] with each state's name, value and whether the SDK generated it, in the order they are packed.
     */
    fun unpack(packed: String, action: (name: String, value: String, isSystem: Boolean) -> Unit) {
        if (packed.isEmpty()) {
            return
        }
        val token = StringBuilder()
        var name: String? = null
        var isSystem = false
        var index = 0
        while (index < packed.length) {
            val c = packed[index]
            when {
                c == ESCAPE && index + 1 < packed.length -> {
                    token.append(packed[index + 1])
                    index++
                }
                name == null && (c == OBSERVED || c == GENERATED) -> {
                    name = token.toString()
                    isSystem = c == GENERATED
                    token.setLength(0)
                }
                c == SEPARATOR -> {
                    name?.let { action(it, token.toString(), isSystem) }
                    name = null
                    token.setLength(0)
                }
                else -> token.append(c)
            }
            index++
        }
        name?.let { action(it, token.toString(), isSystem) }
    }

    private fun StringBuilder.appendEscaped(text: String) {
        text.forEach { c ->
            if (c in RESERVED) {
                append(ESCAPE)
            }
            append(c)
        }
    }
}
