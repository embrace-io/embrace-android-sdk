package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.utils.event.EventKey

/**
 * What the state named [state] is, said once by the data source that reports it, when its capture starts and before its
 * first [StateUpdate]. A state exists only where a data source has declared it: whatever records the updates keeps
 * nothing of an update for a state that has not been declared.
 *
 * @param maxChangesInPart the most changes of this state that one session part lists, its first value included, which
 * is the state's own limit. A change made past it during a part still replaces the state's value, and is counted and
 * not listed.
 * @param attributeKeys the keys of the attributes a change of this state may carry. An attribute under any other key
 * is not kept.
 */
class StateDeclaration(
    val state: String,
    val maxChangesInPart: Int = DEFAULT_MAX_CHANGES_IN_PART,
    val attributeKeys: Set<String> = emptySet(),
) {
    companion object {
        /**
         * The channel a state's declaration is emitted on, by its data source, to whatever records the updates.
         */
        val KEY = EventKey<StateDeclaration>()

        /**
         * The most changes of one state that a session part lists, unless the state is a named exception to it.
         */
        const val DEFAULT_MAX_CHANGES_IN_PART: Int = 100
    }
}
