package io.embrace.android.embracesdk.internal.arch.state

import io.embrace.android.embracesdk.internal.state.StateReader

/**
 * A state, as the data source that reports it defines it: its [name], its limit on the changes a session part's change
 * log lists for it, and the keys of the attributes a change of it may carry. A state exists only as a data source.
 */
class StateDefinition(
    val name: String,
    val changeLimit: Int = DEFAULT_STATE_CHANGE_LIMIT,
    val attributeKeys: Set<String> = emptySet(),
)

/**
 * The limit on the changes listed for a state in a session part, unless its data source names another.
 */
const val DEFAULT_STATE_CHANGE_LIMIT: Int = 100

/**
 * The limit on the changes listed in a session part's change log for every state together, a backstop that normal use
 * does not reach.
 */
const val GLOBAL_STATE_CHANGE_LIMIT: Int = 10_000

/**
 * The most attributes a change may carry of its own.
 */
const val MAX_STATE_CHANGE_ATTRIBUTES: Int = 7

/**
 * The one recorder that holds every state's value and orders every change of every state, when v2 of the runtime
 * state data model is switched on.
 *
 * Every method can be called from any thread, does no I/O, and costs the same however many changes came before.
 */
interface StateRecorder : StateReader {

    /**
     * Makes the state [definition] describes active, with [initialValue] as its first value, which is a change like
     * any other. A state that is already active is left as it is.
     */
    fun define(definition: StateDefinition, initialValue: String, isSystem: Boolean, timeMs: Long)

    /**
     * Reports that the state [name] took [value] at [timeMs], by its reporter's clock. An update that leaves the value
     * as it was is not a change, and an update for a state that is not active is discarded.
     *
     * [attributes] describe the value being left, and only those its data source declared are kept. They are asked for
     * only when the update is a change, under the recorder's lock, so a source whose attributes are reset by collecting
     * them collects them once for each change and in the order of the changes.
     */
    fun record(
        name: String,
        value: String,
        isSystem: Boolean,
        timeMs: Long,
        attributes: () -> Map<String, String> = { emptyMap() },
    )

    /**
     * Reports that the instrumentation of the state [name] dropped [count] changes it never reported.
     */
    fun reportDropped(name: String, count: Int)

    /**
     * Whether the state [name] is active, which it is only once its data source has defined it.
     */
    fun isDefined(name: String): Boolean
}
