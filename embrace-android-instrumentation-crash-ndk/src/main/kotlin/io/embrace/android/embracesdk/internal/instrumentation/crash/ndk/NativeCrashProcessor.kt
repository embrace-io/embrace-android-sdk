package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.payload.NativeCrashData

/**
 * Processes any native crashes that are stored on disk from previous processes & converts them
 * into a format that can be ingested by the delivery layer.
 */
interface NativeCrashProcessor {

    /**
     * Get a summary of every native crash that has been persisted, without deleting anything. Use
     * [loadNativeCrash] to read from disk.
     */
    fun getNativeCrashes(): List<NativeCrashData>

    /**
     * Read the full crash for a summary returned by [getNativeCrashes], or the summary without its report if its
     * file can't be read. Returns null if the crash has been deleted, as it has already been handled, and
     * [nativeCrash] as it is if it was never stored.
     */
    fun loadNativeCrash(nativeCrash: NativeCrashData): NativeCrashData?

    /**
     * Purge all existing native crash data files.
     */
    fun deleteAllNativeCrashes()

    /**
     * Purge the data files associated with [nativeCrash].
     */
    fun deleteNativeCrash(nativeCrash: NativeCrashData)
}
