package io.embrace.android.embracesdk.internal.session.orchestrator

/**
 * Defines the states in which a session can end.
 */
enum class SessionPartSnapshotType(
    /**
     * Whether the session process experienced a force quit/unexpected termination.
     */
    val forceQuit: Boolean,
) {

    /**
     * The end session happened in the normal way (i.e. process state changes or manual/timed end).
     */
    NORMAL_END(forceQuit = false),

    /**
     * The end session is being constructed so that it can be periodically cached. This avoids
     * the scenario of data loss in the event of NDK crashes.
     */
    PERIODIC_CACHE(forceQuit = true),

    /**
     * The end session is being constructed because of a JVM crash.
     */
    JVM_CRASH(forceQuit = false),
}
