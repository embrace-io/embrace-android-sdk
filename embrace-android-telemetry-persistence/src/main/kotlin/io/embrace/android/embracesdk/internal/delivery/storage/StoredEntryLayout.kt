package io.embrace.android.embracesdk.internal.delivery.storage

import java.io.File

/**
 * Describes how one kind of stored telemetry occupies the filesystem, so that [StoredEntryIndex]
 * can track and prune it without knowing what the telemetry is.
 */
interface StoredEntryLayout<T> {

    /**
     * Parses the name of a file or directory found in the storage root. This returns null if the
     * name was not written by this layout, in which case the data is discarded.
     */
    fun fromName(name: String): T?

    /**
     * The file or directory holding the entry's data.
     */
    fun fileFor(rootDir: File, entry: T): File

    /**
     * Removes the entry's data from disk.
     */
    fun delete(file: File)

    /**
     * When the entry was created.
     */
    fun timestampOf(entry: T): Long

    /**
     * The order in which entries are removed once the count limit is reached. Entries that sort
     * first are removed first.
     */
    val removalComparator: Comparator<T>
}
