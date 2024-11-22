package io.embrace.android.embracesdk.internal.ndk

import java.io.File

/**
 * Interface to interact with the data files persisted during a native crash
 */
interface NdkServiceRepository {

    /**
     * Return a list of the raw files of native crashes recorded by the SDK in the given sort order
     */
    fun sortNativeCrashes(byOldest: Boolean): List<File>

    /**
     * Delete specific files used to store data about native crashes
     */
    fun deleteFiles(crashFile: File)

    /**
     * Delete old native crash files when the number of files exceeds the maximum allowed
     */
    fun cleanOldCrashFiles()
}
