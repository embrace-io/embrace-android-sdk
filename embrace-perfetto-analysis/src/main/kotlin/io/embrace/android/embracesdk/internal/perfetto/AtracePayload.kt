package io.embrace.android.embracesdk.internal.perfetto

/**
 * What an ftrace print event's `buf` says.
 *
 * Synchronous begin and end, and counters, are interpreted. The rest are modelled so they can be
 * reported rather than mistaken.
 */
internal sealed interface AtracePayload {

    /** `B|<tgid>|<name>`. [name] is everything past the tgid. */
    data class Begin(val name: String) : AtracePayload

    /** `E`, or `E|<tgid>`. */
    data object End : AtracePayload

    /** `C|<tgid>|<name>|<value>`. A value at an instant, not a duration. */
    data class Counter(val name: String, val value: Long) : AtracePayload

    /** A payload this module does not interpret. [raw] keeps the text, minus its terminator. */
    sealed interface Unsupported : AtracePayload {

        val raw: String

        /** `S|<tgid>|<name>|<cookie>`. Async slices pair by cookie, not by stack. */
        data class AsyncBegin(override val raw: String) : Unsupported

        /** `F|<tgid>|<name>|<cookie>`. */
        data class AsyncEnd(override val raw: String) : Unsupported

        /** Anything else, including a payload malformed for the kind it claims to be. */
        data class Unrecognised(override val raw: String) : Unsupported
    }
}
