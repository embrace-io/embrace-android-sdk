package io.embrace.android.embracesdk.internal.state

/**
 * What a data source declares about the state it reports: its [name], how many of its changes a session part's change log lists
 * before the rest are counted and not listed, and the attribute keys a change of it may carry.
 *
 * A state exists only once a data source has declared it. An update for a name nothing declared is discarded.
 */
class StateDefinition(
    val name: String,
    val changeLimit: Int = DEFAULT_CHANGE_LIMIT,
    attributeKeys: Set<String> = emptySet(),
) {

    /**
     * The keys a change of this state may carry: those declared that the SDK does not write itself, up to [MAX_CHANGE_ATTRIBUTES].
     */
    val attributeKeys: Set<String> =
        attributeKeys.filterNot(AppStateKeys::isReserved).take(MAX_CHANGE_ATTRIBUTES).toSet()

    val valueKey: String = AppStateKeys.VALUE_PREFIX + name
    val valueTypeKey: String = AppStateKeys.VALUE_TYPE_PREFIX + name
    val endValueKey: String = AppStateKeys.END_VALUE_PREFIX + name
    val endValueTypeKey: String = AppStateKeys.END_VALUE_TYPE_PREFIX + name

    /** The name a change of this state refused by a limit is tracked under, which is the one its data source has always had. */
    val limitTelemetryName: String = name + LIMIT_TELEMETRY_SUFFIX

    companion object {

        /** The limit on a state's changes in a session part, unless its data source declares another. */
        const val DEFAULT_CHANGE_LIMIT: Int = 100

        /** How many attributes of its own a change may carry, beside the three the SDK writes on it. */
        const val MAX_CHANGE_ATTRIBUTES: Int = 7

        private const val LIMIT_TELEMETRY_SUFFIX = "_state_data_source"

        /**
         * Whether [name] can name a state: lower case letters, digits and hyphens only.
         */
        fun isValidName(name: String): Boolean =
            name.isNotEmpty() && name.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
    }
}
