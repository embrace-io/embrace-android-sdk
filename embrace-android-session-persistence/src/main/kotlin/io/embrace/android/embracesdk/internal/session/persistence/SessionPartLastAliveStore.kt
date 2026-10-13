package io.embrace.android.embracesdk.internal.session.persistence

import java.io.File

/**
 * Keeps the last time each session part's process was seen alive, so the next launch can end a part whose
 * process died no earlier than that.
 *
 * A process that can record a time was alive at it. A part's [SessionPartWriteTarget] records just before and
 * just after each write of the part's files, so a process that dies partway through a write still leaves the
 * time it started it.
 */
interface SessionPartLastAliveStore {

    /**
     * Opens a recorder for the part whose files are in [partDir]. Called on the part's first write, on the
     * thread that writes the part.
     */
    fun recorder(partDir: File): SessionPartLastAliveRecorder

    /**
     * The last time recorded for the part whose files are in [partDir], in milliseconds since the epoch, or null
     * if none was.
     */
    fun lastAliveMs(partDir: File): Long?
}

/**
 * Records, for one session part, that its process is alive now.
 */
fun interface SessionPartLastAliveRecorder {

    /**
     * Records that the process is alive now, as it writes [file].
     */
    fun recordAlive(file: File)
}

/**
 * Records nothing, so a dead part's end comes only from the times its telemetry records.
 */
class NoopSessionPartLastAliveStore : SessionPartLastAliveStore {

    override fun recorder(partDir: File): SessionPartLastAliveRecorder = SessionPartLastAliveRecorder { }

    override fun lastAliveMs(partDir: File): Long? = null
}
