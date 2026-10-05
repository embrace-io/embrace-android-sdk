package io.embrace.android.embracesdk.internal.session.persistence

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Tracks the session part directories that this process is still writing telemetry into. A
 * directory is marked when its part starts and unmarked once every write queued for it has run.
 *
 * The key is the directory, not the part id: the reader skips what the live process is writing,
 * and the directory is exactly that. A part id only names the part, so a dead process's directory
 * that happens to carry the same part id must still be read.
 */
class SessionPartWriteTracker {

    private val inProgress = CopyOnWriteArraySet<SessionPartDirectory>()

    /**
     * Records that telemetry is being written into the given directory.
     */
    fun markWriting(directory: SessionPartDirectory) {
        inProgress.add(directory)
    }

    /**
     * Records that every write into the given directory has run.
     */
    fun markComplete(directory: SessionPartDirectory) {
        inProgress.remove(directory)
    }

    /**
     * Whether telemetry is still being written into the given directory.
     */
    fun isWriting(directory: SessionPartDirectory): Boolean = inProgress.contains(directory)
}
