package io.embrace.android.embracesdk.internal.session.persistence

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Tracks the session parts that this process is still writing telemetry for. A part
 * is marked when it starts and unmarked once every write queued for it has run.
 */
class SessionPartWriteTracker {

    private val inProgress = CopyOnWriteArraySet<String>()

    /**
     * Records that telemetry is being written for the given session part.
     */
    fun markWriting(sessionPartId: String) {
        inProgress.add(sessionPartId)
    }

    /**
     * Records that every write for the given session part has run.
     */
    fun markComplete(sessionPartId: String) {
        inProgress.remove(sessionPartId)
    }

    /**
     * Whether telemetry is still being written for the given session part.
     */
    fun isWriting(sessionPartId: String): Boolean = inProgress.contains(sessionPartId)
}
