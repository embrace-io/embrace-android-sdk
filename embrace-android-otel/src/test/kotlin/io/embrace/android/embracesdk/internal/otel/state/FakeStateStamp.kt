package io.embrace.android.embracesdk.internal.otel.state

/**
 * A [StateStamp] made of the values it is given: each state's name to its value, with a `~` before a value the SDK
 * generated.
 */
internal class FakeStateStamp(
    override val sequence: Long,
    private val values: Map<String, String> = emptyMap(),
    override val sinceMs: Long = 0,
) : StateStamp {

    override fun isEmpty(): Boolean = values.isEmpty()

    override val encoded: String = values.entries.joinToString(";") { (state, value) ->
        if (value.startsWith(GENERATED)) "$state~${value.removePrefix(GENERATED)}" else "$state=$value"
    }

    override val attributes: Map<String, String> = attributes(
        values,
        StateAttributes.VALUE_PREFIX,
        StateAttributes.VALUE_TYPE_PREFIX,
    )

    override fun endAttributes(start: StateStamp): Map<String, String> = attributes(
        values.filter { (state, value) -> (start as FakeStateStamp).values[state] != value },
        StateAttributes.END_VALUE_PREFIX,
        StateAttributes.END_VALUE_TYPE_PREFIX,
    )

    private fun attributes(values: Map<String, String>, valuePrefix: String, typePrefix: String): Map<String, String> =
        buildMap {
            values.forEach { (state, value) ->
                put(valuePrefix + state, value.removePrefix(GENERATED))
                if (value.startsWith(GENERATED)) {
                    put(typePrefix + state, StateAttributes.SYSTEM)
                }
            }
        }

    private companion object {
        const val GENERATED = "~"
    }
}
