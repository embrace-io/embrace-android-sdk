package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.clock.Clock
import java.io.File

/**
 * Keeps a session part's last alive time in the modification times of its files: each file is set to
 * [clock]'s time as the process records itself alive writing it, and the latest across the part's files is
 * read back.
 *
 * Setting the time on a file that does not exist yet, before its first write, does nothing.
 */
class FileModifiedTimeLastAliveStore(private val clock: Clock) : SessionPartLastAliveStore {

    override fun recorder(partDir: File): SessionPartLastAliveRecorder =
        SessionPartLastAliveRecorder { file -> file.setLastModified(clock.now()) }

    override fun lastAliveMs(partDir: File): Long? {
        var latestMs = 0L
        for (partFile in SessionPartFile.entries) {
            latestMs = maxOf(latestMs, File(partDir, partFile.fileName).lastModified())
        }
        return latestMs.takeIf { it > 0 }
    }
}
