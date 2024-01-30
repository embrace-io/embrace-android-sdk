package io.embrace.android.embracesdk.session.orchestrator

/**
 * Defines the states in which a session can end.
 */
internal enum class SessionSnapshotType(

    /**
     * Whether the session ended cleanly (i.e. not because of a crash).
     */
    val endedCleanly: Boolean,

    /**
     * Whether the session process experienced a force quit/unexpected termination.
     */
    val forceQuit: Boolean,

    /**
     * Whether periodic caching of the session should stop or not.
     */
    val shouldStopCaching: Boolean
) {

    /**
     * The end session happened in the normal way (i.e. process state changes or manual/timed end).
     */
    NORMAL_END(true, false, true),

    /**
     * The end session is being constructed so that it can be periodically cached. This avoids
     * the scenario of data loss in the event of NDK crashes.
     */
    PERIODIC_CACHE(false, true, false),

    /**
     * The end session is being constructed because of a JVM crash.
     */
    JVM_CRASH(false, false, true);
}
