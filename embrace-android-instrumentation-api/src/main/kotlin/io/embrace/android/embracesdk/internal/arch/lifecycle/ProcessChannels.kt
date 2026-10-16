package io.embrace.android.embracesdk.internal.arch.lifecycle

import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.utils.event.EventKey
import io.embrace.android.embracesdk.internal.utils.event.StateKey

/**
 * The channels that carry the process lifecycle on the event bus.
 */
object ProcessChannels {

    /**
     * The state the process is in, emitted before the session orchestrator reacts to each transition.
     */
    val ProcessState = StateKey<ProcessState>()

    /**
     * The id of a JVM crash, emitted once after its crash log so that handlers tear down in [CrashTeardownOrder].
     */
    val CrashTeardown = EventKey<String>()
}
