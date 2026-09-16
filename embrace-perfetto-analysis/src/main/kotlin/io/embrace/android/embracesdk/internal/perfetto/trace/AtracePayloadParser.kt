package io.embrace.android.embracesdk.internal.perfetto.trace

/** Atrace terminates every payload it writes to `trace_marker` with a newline. */
private const val TERMINATOR = '\n'

/** Separates the fields within a payload. */
private const val SEPARATOR = '|'

private const val BEGIN = 'B'
private const val END = 'E'
private const val ASYNC_BEGIN = 'S'
private const val ASYNC_END = 'F'
private const val COUNTER = 'C'

/**
 * Reads the atrace payload an ftrace print event carries in [buf].
 * A payload that does not parse becomes [AtracePayload.Unsupported.Unrecognised].
 */
internal fun parseAtracePayload(buf: String): AtracePayload {
    val payload = buf.substringBefore(TERMINATOR)
    val kind = payload.firstOrNull()
    val fields = payload.drop(1)
    val delimited = fields.startsWith(SEPARATOR)
    return when {
        kind == BEGIN && delimited -> begin(payload)
        kind == END && (fields.isEmpty() || delimited) -> AtracePayload.End
        kind == COUNTER && delimited -> counter(payload)
        kind == ASYNC_BEGIN && delimited -> AtracePayload.Unsupported.AsyncBegin(payload)
        kind == ASYNC_END && delimited -> AtracePayload.Unsupported.AsyncEnd(payload)
        else -> AtracePayload.Unsupported.Unrecognised(payload)
    }
}

/** Reads `B|<tgid>|<name>`, taking the name as everything past the tgid so a `|` within it survives. */
private fun begin(payload: String): AtracePayload {
    val name = payload.pastTgid()
    return when {
        name.isEmpty() -> AtracePayload.Unsupported.Unrecognised(payload)
        else -> AtracePayload.Begin(name)
    }
}

/** Reads `C|<tgid>|<name>|<value>`, taking the value as the last field so a `|` within the name survives. */
private fun counter(payload: String): AtracePayload {
    val fields = payload.pastTgid()
    val name = fields.substringBeforeLast(SEPARATOR, missingDelimiterValue = "")
    val value = fields.substringAfterLast(SEPARATOR).toLongOrNull()
    return when {
        name.isEmpty() || value == null -> AtracePayload.Unsupported.Unrecognised(payload)
        else -> AtracePayload.Counter(name, value)
    }
}

/** Everything past the kind and the tgid, or empty when the payload holds no more. */
private fun String.pastTgid(): String =
    substringAfter(SEPARATOR).substringAfter(SEPARATOR, missingDelimiterValue = "")
