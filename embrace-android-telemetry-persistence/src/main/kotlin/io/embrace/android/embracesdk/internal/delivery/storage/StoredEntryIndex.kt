package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.utils.threadSafeToList
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Tracks the telemetry stored under a directory and prunes entries that are too old or too
 * numerous, so that storage cannot grow without bound.
 *
 * An in-memory index is maintained to avoid calling listFiles() every time the storage limit needs
 * checking. It stays in sync with what is on disk as long as entries are only manipulated through
 * this class.
 */
class StoredEntryIndex<T>(
    outputDir: Lazy<File>,
    private val layout: StoredEntryLayout<T>,
    private val clock: Clock,
    private val logger: InternalLogger,
    private val errorType: InternalErrorType,
    private val storageLimit: Int,
    private val maxAgeMs: Long,
) {

    val rootDir: File by lazy {
        outputDir.value.apply { mkdirs() }
    }

    private val entries: CopyOnWriteArraySet<T> by lazy {
        val result = runCatching { rootDir.listFiles() }.getOrNull()
        val files = result?.toList() ?: emptyList()
        val parsed = files.mapNotNull { file ->
            val entry = layout.fromName(file.name)
            if (entry == null) {
                // discard anything that can't be parsed (e.g. leftover .tmp files from killed
                // processes, or data written by an SDK using a different layout)
                runCatching { layout.delete(file) }
            }
            entry
        }
        CopyOnWriteArraySet(parsed)
    }

    /**
     * The file or directory holding the entry's data.
     */
    fun fileFor(entry: T): File = layout.fileFor(rootDir, entry)

    /**
     * Records that the entry's data is now on disk.
     */
    fun add(entry: T) {
        entries.add(entry)
    }

    /**
     * The entries currently on disk.
     */
    fun storedEntries(): List<T> = entries.threadSafeToList()

    /**
     * Removes the entry's data from disk, and the entry from the index.
     */
    fun delete(entry: T) {
        try {
            layout.delete(fileFor(entry))
        } catch (exc: Throwable) {
            if (exc !is FileNotFoundException) {
                logger.trackInternalError(errorType, exc)
            }
        } finally {
            entries.remove(entry)
        }
    }

    /**
     * Removes all entries created before the age cutoff. When [newEntry] is non-null the
     * count-based limit is then enforced, and the return value indicates whether [newEntry] itself
     * was pruned and so should not be written to disk.
     */
    fun prune(newEntry: T? = null): Boolean {
        // remove entries created before the cutoff
        val cutoffMs = clock.now() - maxAgeMs
        if (cutoffMs > 0L) {
            entries.filter { layout.timestampOf(it) < cutoffMs }.forEach(::delete)
        }

        newEntry ?: return false

        // remove entries by count
        val count = entries.size
        if (count < storageLimit) {
            return false
        }
        val input = (entries + newEntry).toMutableList()
        val removalCount = input.size - storageLimit
        if (removalCount < 0) {
            return false
        }
        val removals = if (removalCount == 1) {
            // exceeding the limit by one is the common case, so avoid sorting the whole index
            listOfNotNull(input.minWithOrNull(layout.removalComparator))
        } else {
            input.sortWith(layout.removalComparator)
            input.take(removalCount)
        }
        removals.forEach(::delete)

        // notify the caller whether the new entry should be dropped
        return removals.contains(newEntry)
    }
}
