package io.embrace.android.embracesdk.internal.state

import io.embrace.android.embracesdk.internal.utils.event.EventKey

/**
 * The channels a state's reports travel on, from whatever knows the state to the recorder. They keep what reports a state apart from
 * what records it: a data source, or a hosted SDK's call, only emits.
 */
object StateEvents {

    /** A data source declares the state it reports, with its first value. */
    val DECLARED: EventKey<StateDeclaration> = EventKey()

    /** A state has a new value. One for a state nothing declared is discarded. */
    val UPDATED: EventKey<StateUpdate> = EventKey()

    /** A state's instrumentation reports changes it never passed on. */
    val DROPPED: EventKey<StateDrops> = EventKey()

    /**
     * Something other than a state's data source reports its value, as a hosted SDK does for the screen. The state's own data
     * source takes the report up and passes it on as its own, so that a state has one reporter and its value one judge of whether
     * it changed. A report for a state nothing declared is discarded.
     */
    val REPORTED: EventKey<StateReport> = EventKey()
}

class StateDeclaration(
    val definition: StateDefinition,
    val initialValue: String,
    val system: Boolean,
    val timeMs: Long,
)

class StateUpdate(
    val state: String,
    val value: String,
    val system: Boolean,
    val timeMs: Long,
    val attributes: Map<String, String> = emptyMap(),
)

class StateDrops(
    val state: String,
    val count: Int,
)

class StateReport(
    val state: String,
    val value: String,
    val timeMs: Long,
)
