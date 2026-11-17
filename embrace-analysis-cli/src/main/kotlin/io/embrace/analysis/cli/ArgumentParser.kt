package io.embrace.analysis.cli

/**
 * Parses a command's arguments: the [flags] it takes, the [valued] options, each taking its value as the next argument
 * or after `=`, and positional arguments. Only the [repeatable] valued options may be given more than once. `-h` or
 * `--help` anywhere asks for the help text.
 */
class ArgumentParser(
    private val flags: Set<String>,
    private val valued: Set<String>,
    private val repeatable: Set<String> = emptySet(),
) {

    /**
     * The arguments in [args], or null when they ask for the help text. Throws [UsageException] for an unknown option,
     * an option without its value, or one given again that isn't [repeatable].
     */
    fun parse(args: List<String>): ParsedArguments? {
        val setFlags = mutableSetOf<String>()
        val values = mutableMapOf<String, MutableList<String>>()
        val positional = mutableListOf<String>()
        val remaining = args.iterator()
        while (remaining.hasNext()) {
            val arg = remaining.next()
            val option = arg.substringBefore('=')
            val inlineValue = arg.substringAfter('=', missingDelimiterValue = "").takeIf { '=' in arg }
            when {
                option == "-h" || option == "--help" -> return null
                option in flags -> setFlags.add(option)
                option in valued -> {
                    val value = inlineValue ?: remaining.nextOrNull() ?: usageError("$option needs a value")
                    if (option in values && option !in repeatable) {
                        usageError("$option given more than once")
                    }
                    values.getOrPut(option) { mutableListOf() }.add(value)
                }
                arg.startsWith("-") -> usageError("unknown option $arg")
                else -> positional.add(arg)
            }
        }
        return ParsedArguments(setFlags, values, positional)
    }

    private fun usageError(message: String): Nothing = throw UsageException(message)

    private fun Iterator<String>.nextOrNull(): String? = if (hasNext()) {
        next()
    } else {
        null
    }
}

/** What [ArgumentParser] read: the [flags] set, each valued option's [values] in order, and the [positional] arguments. */
data class ParsedArguments(
    val flags: Set<String>,
    val values: Map<String, List<String>>,
    val positional: List<String>,
)

/** Arguments a command can't run with; the message says why. */
class UsageException(message: String) : IllegalArgumentException(message)
