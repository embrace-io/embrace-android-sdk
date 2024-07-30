package io.embrace.android.embracesdk.internal.session.orchestrator

/**
 * Defines the states in which a session can end.
 */
public enum class SessionSnapshotType(

    /**
     * Whether the session ended cleanly (i.e. not because of a crash).
     */
    public val endedCleanly: Boolean,

    /**
     * Whether the session process experienced a force quit/unexpected termination.
     */
    public val forceQuit: Boolean,

    /**
     * Whether this type of end should lead to the start of a new session
     */
    public val shouldStartNewSession: Boolean
) {

    /**
     * The end session happened in the normal way (i.e. process state changes or manual/timed end).
     */
    NORMAL_END(endedCleanly = true, forceQuit = false, shouldStartNewSession = true),

    /**
     * The end session is being constructed so that it can be periodically cached. This avoids
     * the scenario of data loss in the event of NDK crashes.
     */
    PERIODIC_CACHE(endedCleanly = false, forceQuit = true, shouldStartNewSession = false),

    /**
     * The end session is being constructed because of a JVM crash.
     */
    JVM_CRASH(endedCleanly = false, forceQuit = false, shouldStartNewSession = false)
}
