package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.payload.NativeCrashData

/**
 * Processes any native crashes that are stored on disk from previous processes & converts them
 * into a format that can be ingested by the delivery layer.
 */
interface NativeCrashProcessor {

    /**
     * Get all the native crash instances that have been persisted without deleting anything
     */
    fun getNativeCrashes(): List<NativeCrashData>

    /**
     * Purge all existing native crash data files.
     */
    fun deleteAllNativeCrashes()

    /**
     * Purge the data files associated with [nativeCrash].
     */
    fun deleteNativeCrash(nativeCrash: NativeCrashData)
}
